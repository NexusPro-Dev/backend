package com.factech.nexus.modules.products.interfaces;

import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import java.util.List;
import java.util.UUID;
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
 * `RF-PM-001` · `T-15` — concurrencia sobre el alta del catálogo.
 *
 * <p><b>Es el caso que la verificación previa NO puede resolver.</b> Dos peticiones simultáneas con
 * el mismo código pasan las dos el {@code SELECT} de unicidad —ninguna ve a la otra— y llegan al
 * {@code INSERT}. Quien decide es el índice único, y la única pregunta que importa es si su
 * violación llega al cliente como un {@code 409} legible o como un {@code 500}: el adaptador la
 * traduce por <b>nombre de restricción</b>, y esto lo comprueba.
 *
 * <p>Aquí importa especialmente porque `RN-PM-013` hace el código <b>irrepetible para siempre</b>:
 * un duplicado que se colara no se podría corregir después, ni siquiera retirando el producto.
 */
@AutoConfigureMockMvc
class ProductConcurrencyIT extends IntegrationTestBase {

  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void limpiarCatalogo() {
    jdbc.update("DELETE FROM products");
  }

  @Test
  @DisplayName("dos altas simultáneas con el mismo código: una queda y la otra recibe 409")
  void dosAltasConElMismoCodigo() throws Exception {
    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(alta("ASESORIA", "Asesoría " + indice)));

    // Ninguna puede salir como 500: el fallo de integridad tiene que llegar
    // traducido, o el cliente no sabe que su problema es un duplicado.
    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);

    assertThat(cuantosProductos()).as("las dos altas quedaron, o no quedó ninguna").isEqualTo(1);

    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 201).count())
        .as("exactamente una debía crearse")
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .as("exactamente una debía rechazarse con 409")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("dos altas simultáneas con el mismo nombre: la unicidad del nombre también aguanta")
  void dosAltasConElMismoNombre() throws Exception {
    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estadoDe(alta("CODIGO_" + indice, "Asesoría")));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);
    assertThat(cuantosProductos()).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder alta(String codigo, String nombre) {
    return post("/api/v1/products")
        .with(user(UUID.randomUUID().toString()).authorities(() -> "products:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"code":"%s","type":"SERVICIO","name":"%s","price":10.00,"currencyId":"%s"}
            """
                .formatted(codigo, nombre, USD));
  }

  private int estadoDe(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }

  private int cuantosProductos() {
    Integer filas = jdbc.queryForObject("SELECT count(*) FROM products", Integer.class);
    return filas == null ? 0 : filas;
  }
}
