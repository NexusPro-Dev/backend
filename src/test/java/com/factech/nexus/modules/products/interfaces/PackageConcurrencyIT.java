package com.factech.nexus.modules.products.interfaces;

import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.products.interfaces.PackageTestSupport.Membresias;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Las carreras del paquete (`RF-PM-017` · `T-10`, y las que añadan las tripletas siguientes).
 *
 * <p>Dos altas con el mismo código: la comprobación previa no ve a nadie en las dos, y es {@code
 * uq_product_packages_code} quien muerde. Lo que se comprueba es que el fallo llegue
 * <b>traducido</b> —un {@code 409}, no un {@code 500}— y que quede exactamente una fila.
 */
@AutoConfigureMockMvc
class PackageConcurrencyIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  @AfterEach
  void limpiar() {
    PackageTestSupport.limpiarPaquetes(jdbc);
    jdbc.update("DELETE FROM products");
  }

  @Test
  @DisplayName("dos altas simultáneas con el mismo código: una queda y la otra recibe 409")
  void dosAltasConElMismoCodigo() throws Exception {
    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(alta("COMBO", "Combo " + indice)));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(cuantosPaquetes()).isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 201).count())
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("dos altas simultáneas con el mismo nombre: el índice parcial también aguanta")
  void dosAltasConElMismoNombre() throws Exception {
    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(alta("CODIGO_" + indice, "Combo")));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(cuantosPaquetes()).isEqualTo(1);
  }

  @Test
  @DisplayName("`CA-PM-312` — el mismo producto dos veces a la vez: una fila y un 409")
  void elMismoProductoDosVeces() throws Exception {
    Membresias m = PackageTestSupport.limpiarCatalogoYSembrarMembresias(jdbc);
    UUID paquete = PackageTestSupport.paquete(jdbc, "COMBO", "Descripción", "INACTIVO", "AMBOS");
    UUID bot = PackageTestSupport.bot(jdbc, "BOT_A", "10.00");

    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(asociar(paquete, bot, "FIJO", "1.00")));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(cuantasFilas()).isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 201).count())
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .isEqualTo(1);
    assertThat(m.beca()).isNotNull();
  }

  @Test
  @DisplayName(
      "`RN-PM-046` bajo carrera — dos upgrades a la vez, del MISMO origen: un 201 y un 409, y el"
          + " bloqueo del paquete es lo que los ordena — la regla no tiene red en el esquema")
  void dosUpgradesALaVez() throws Exception {
    Membresias m = PackageTestSupport.limpiarCatalogoYSembrarMembresias(jdbc);
    UUID paquete = PackageTestSupport.paquete(jdbc, "COMBO", "Descripción", "INACTIVO", "AMBOS");
    UUID haciaPlatino =
        PackageTestSupport.upgrade(jdbc, "UP_BECA_PLATINO", "100.00", m.beca(), m.platino());
    UUID haciaOro = PackageTestSupport.upgrade(jdbc, "UP_BECA_ORO", "200.00", m.beca(), m.oro());
    List<UUID> productos = List.of(haciaPlatino, haciaOro);

    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(asociar(paquete, productos.get(indice), "FIJO", "0")));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(cuantasFilas()).as("solo un upgrade puede quedar").isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .isEqualTo(1);
  }

  private MockHttpServletRequestBuilder asociar(
      UUID paquete, UUID producto, String forma, String valor) {
    return post("/api/v1/packages/" + paquete + "/products")
        .with(
            user(UUID.randomUUID().toString())
                .authorities(
                    () -> "packages:update",
                    () -> "packages:change-status",
                    () -> "packages:set-cover",
                    () -> "packages:remove-cover",
                    () -> "packages:add-product",
                    () -> "packages:update-product",
                    () -> "packages:remove-product"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(PackageProductsIT.json(producto, forma, valor));
  }

  private int cuantasFilas() {
    Integer filas =
        jdbc.queryForObject("SELECT count(*) FROM product_package_items", Integer.class);
    return filas == null ? 0 : filas;
  }

  private MockHttpServletRequestBuilder alta(String codigo, String nombre) {
    return post("/api/v1/packages")
        .with(user(UUID.randomUUID().toString()).authorities(() -> "packages:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(PackagesIT.cuerpo(codigo, nombre, PackageTestSupport.USD));
  }

  private int estadoDe(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }

  private int cuantosPaquetes() {
    Integer filas = jdbc.queryForObject("SELECT count(*) FROM product_packages", Integer.class);
    return filas == null ? 0 : filas;
  }
}
