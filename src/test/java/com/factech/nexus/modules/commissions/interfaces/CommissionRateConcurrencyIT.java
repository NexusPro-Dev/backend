package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.MANAGER;
import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.factech.nexus.IntegrationTestBase;
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

/**
 * Las dos reglas de `CM` que viven en el motor, bajo concurrencia.
 *
 * <p><b>Es la prueba que verifica DÓNDE vive cada regla.</b> Las otras suites comprueban que el
 * solapamiento y el rol duplicado se rechazan, y pasarían igual si la comprobación fuera un {@code
 * SELECT} previo en el caso de uso. Esta no: dos peticiones simultáneas leerían las dos que no hay
 * conflicto y las dos insertarían.
 *
 * <p>Es el defecto que `RN-SP-018` tuvo y que se corrigió el 26-08-2026, escrito aquí antes de
 * volver a tenerlo.
 *
 * <p><b>Y desde el 01-09-2026 son dos reglas y no una.</b> El no solapamiento se mudó a las tasas
 * personalizadas —las de rol perdieron la vigencia y con ella la posibilidad de solaparse—, y nació
 * `RN-CM-013`, que un {@code SELECT} previo burlaría igual de fácil.
 */
@AutoConfigureMockMvc
class CommissionRateConcurrencyIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID vendedora;

  @BeforeEach
  void preparar() {
    limpiar();
    vendedora = CommissionFixtures.sembrarPersonaConRol(jdbc, "vendedora", MANAGER);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // `RN-CM-006` — el no solapamiento, ahora sobre las personalizadas
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("dos tasas personales simultáneas del mismo periodo: una queda, la otra recibe 409")
  void dosAltasSimultaneasDelMismoPeriodo() throws Exception {
    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> altaPersonal("10.0" + indice, "2026-01-01", "2026-12-31"));

    // Ninguna puede salir como 500: el fallo de integridad tiene que llegar
    // traducido, o el cliente no sabe que su problema es un solapamiento.
    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);

    assertThat(cuantasPersonales()).as("las dos altas quedaron, o no quedó ninguna").isEqualTo(1);

    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 201).count())
        .as("exactamente una debía crearse")
        .isEqualTo(1);

    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .as("y la otra debía recibir el conflicto traducido")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("dos tasas personales simultáneas de periodos que NO se tocan: las dos quedan")
  void dosAltasSimultaneasConsecutivas() throws Exception {
    List<Outcome<Integer>> resultados =
        runTogether(
            2,
            indice ->
                indice == 0
                    ? altaPersonal("10.00", "2026-01-01", "2026-06-30")
                    : altaPersonal("12.00", "2026-07-01", "2026-12-31"));

    assertThat(resultados).allMatch(r -> r.succeeded() && r.value() == 201);
    assertThat(cuantasPersonales()).isEqualTo(2);
  }

  // ---------------------------------------------------------------------------
  // `RN-CM-013` — un porcentaje por rol y producto
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("dos asociaciones simultáneas del mismo rol al mismo producto: solo una entra")
  void dosAsociacionesSimultaneas() throws Exception {
    UUID producto = CommissionFixtures.sembrarProducto(jdbc, "BOT_A");
    UUID primera = CommissionFixtures.sembrarTasaDeRol(jdbc, MANAGER, "10.00");
    UUID segunda = CommissionFixtures.sembrarTasaDeRol(jdbc, MANAGER, "15.00");

    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> asociar(indice == 0 ? primera : segunda, producto));

    assertThat(resultados).noneMatch(r -> r.succeeded() && r.value() >= 500);

    // Si entraran las dos, la resolución tendría dos respuestas válidas para
    // «qué paga MANAGER por este producto» y elegiría el plan de ejecución. La
    // clave primaria es lo único que lo impide.
    assertThat(cuantasAsociaciones()).isEqualTo(1);

    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 201).count())
        .isEqualTo(1);
    assertThat(resultados.stream().filter(r -> r.succeeded() && r.value() == 409).count())
        .isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  private int altaPersonal(String porcentaje, String desde, String hasta) {
    return estadoDe(
        post("/api/v1/user-commission-rates")
            .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:create"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"userId\":\""
                    + vendedora
                    + "\",\"rateType\":\"PORCENTAJE\",\"percentage\":"
                    + porcentaje
                    + ",\"validFrom\":\""
                    + desde
                    + "\",\"validTo\":\""
                    + hasta
                    + "\"}"));
  }

  private int asociar(UUID tasa, UUID producto) {
    return estadoDe(
        post("/api/v1/commission-rates/" + tasa + "/products")
            .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:update"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"productId\":\"" + producto + "\"}"));
  }

  private int estadoDe(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion) {
    try {
      return mvc.perform(peticion).andReturn().getResponse().getStatus();
    } catch (Exception fallo) {
      throw new IllegalStateException(fallo);
    }
  }

  private long cuantasPersonales() {
    return jdbc.queryForObject("SELECT count(*) FROM user_commission_rates", Long.class);
  }

  private long cuantasAsociaciones() {
    return jdbc.queryForObject("SELECT count(*) FROM product_commission_rates", Long.class);
  }

  private void limpiar() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'CM'");
  }
}
