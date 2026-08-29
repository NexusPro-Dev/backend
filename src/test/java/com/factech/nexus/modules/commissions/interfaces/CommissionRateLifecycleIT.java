package com.factech.nexus.modules.commissions.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
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
 * Corrección y retiro de una tarifa (`RF-CM-003` y `RF-CM-004`).
 *
 * <p>Las dos operaciones van juntas porque la distinción que las separa es lo que se prueba:
 * <b>corregir reescribe lo que una tarifa dice que rigió; retirar dice que no debió existir</b>. Y
 * de ahí que el retiro <b>no toque la vigencia</b>.
 */
@AutoConfigureMockMvc
class CommissionRateLifecycleIT extends IntegrationTestBase {

  private static final String VENDEDOR = "01a02a33-4c00-7005-9c4f-5e7ad1000003";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID tarifa;

  @BeforeEach
  void preparar() {
    limpiar();
    // Acotada a 2026 a propósito: una tarifa indefinida taparía los periodos que
    // las pruebas del retiro usan más adelante, y el choque sería del montaje y
    // no de lo que se quiere verificar.
    tarifa = sembrarTarifa("10.00", "2026-01-01", "2026-12-31");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName("`CA-CM-023` — corrige el porcentaje y conserva intacto lo demás")
  void corrigeElPorcentaje() throws Exception {
    mvc.perform(corregir(tarifa, "{\"percentage\":12.50}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.percentage").value(12.50))
        .andExpect(jsonPath("$.validFrom").value("2026-01-01"));
  }

  @Test
  @DisplayName("`CA-CM-024` y `CA-CM-025` — declara el fin de vigencia y lo VACÍA")
  void cierraYReabreLaVigencia() throws Exception {
    mvc.perform(corregir(tarifa, "{\"validTo\":\"2026-06-30\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validTo").value("2026-06-30"));

    mvc.perform(corregir(tarifa, "{\"validTo\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validTo").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName("`CA-CM-026` — vaciar el porcentaje se RECHAZA, al revés que el fin de vigencia")
  void elPorcentajeNoSeVacia() throws Exception {
    mvc.perform(corregir(tarifa, "{\"percentage\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
  }

  @Test
  @DisplayName("`CA-CM-027` — los cuatro inmutables se rechazan con su mensaje, no se ignoran")
  void losInmutablesSeRechazan() throws Exception {
    mvc.perform(corregir(tarifa, "{\"roleId\":\"" + VENDEDOR + "\",\"percentage\":50}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-009"));

    // Y NO se aplica el resto de la petición: ignorarlos haría creer que el
    // cambio se aplicó.
    assertThat(porcentajeDe(tarifa)).isEqualByComparingTo(new java.math.BigDecimal("10.00"));

    mvc.perform(corregir(tarifa, "{\"validFrom\":\"2026-02-01\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("la petición vacía se rechaza")
  void peticionVacia() throws Exception {
    mvc.perform(corregir(tarifa, "{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-010"));
  }

  @Test
  @DisplayName("`CA-CM-028` — corregir la vigencia hasta solapar devuelve 409, y NO 500")
  void elSolapamientoAlCorregirEs409() throws Exception {
    // Se cierra la primera y se declara la siguiente, consecutivas.
    mvc.perform(corregir(tarifa, "{\"validTo\":\"2026-06-30\"}")).andExpect(status().isOk());
    sembrarTarifa("12.00", "2026-07-01", null);

    // Reabrir la primera pisaría los días de la segunda. Es la prueba del
    // defecto de `RF-SP-027`: el UPDATE sale en el commit, fuera de todo try, y
    // sin el volcado explícito esto llegaría como 500.
    mvc.perform(corregir(tarifa, "{\"validTo\":null}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-007"));
  }

  @Test
  @DisplayName("`CA-CM-029` — una tarifa retirada se trata como inexistente al corregir")
  void laRetiradaNoSeCorrige() throws Exception {
    mvc.perform(retirar(tarifa, "Se declaró sobre el rol equivocado."))
        .andExpect(status().isNoContent());

    mvc.perform(corregir(tarifa, "{\"percentage\":50}")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "`CA-CM-031` y `CA-CM-038` — retira con motivo, conserva la fila y NO toca la vigencia")
  void retiraSinTocarLaVigencia() throws Exception {
    UUID conVigencia = sembrarTarifa("20.00", "2027-01-01", "2027-06-30");

    mvc.perform(retirar(conVigencia, "Se duplicó por error.")).andExpect(status().isNoContent());

    assertThat(sigueLaFila(conVigencia)).isTrue();
    assertThat(fechaDeRetiro(conVigencia)).isNotNull();
    // La evidencia que el registro de eliminación necesita: qué periodo cubría.
    assertThat(vigenciaHastaDe(conVigencia)).isEqualTo("2027-06-30");
  }

  @Test
  @DisplayName("`CA-CM-035` — sin motivo, o en blanco, no se retira nada")
  void elMotivoEsObligatorio() throws Exception {
    mvc.perform(retirar(tarifa, "   "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-007"));

    assertThat(fechaDeRetiro(tarifa)).isNull();
  }

  @Test
  @DisplayName("`CA-CM-036` — retirar dos veces devuelve 409, y no es idempotente")
  void noEsIdempotente() throws Exception {
    mvc.perform(retirar(tarifa, "El primer motivo.")).andExpect(status().isNoContent());
    mvc.perform(retirar(tarifa, "Un motivo distinto.")).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("`CA-CM-037` — tras retirar, LOS DÍAS QUEDAN LIBRES para otra tarifa")
  void losDiasQuedanLibres() throws Exception {
    mvc.perform(retirar(tarifa, "Se declaró mal.")).andExpect(status().isNoContent());

    // Es la prueba de que la restricción del motor es PARCIAL sobre las vivas.
    // Si se hubiera declarado sobre todas las filas, retirar dejaría el periodo
    // inutilizable para siempre y nada más fallaría.
    mvc.perform(
            post("/api/v1/commission-rates")
                .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"roleId\":\""
                        + VENDEDOR
                        + "\",\"percentage\":15,"
                        + "\"validFrom\":\"2026-01-01\"}"))
        .andExpect(status().isCreated());
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder corregir(UUID id, String json) {
    return patch("/api/v1/commission-rates/{id}", id)
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  private MockHttpServletRequestBuilder retirar(UUID id, String motivo) {
    return post("/api/v1/commission-rates/{id}/deletion", id)
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:delete"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"" + motivo + "\"}");
  }

  private UUID sembrarTarifa(String pct, String desde, String hasta) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO commission_rates (id, role_id, percentage, valid_from, valid_to)"
            + " VALUES (CAST(? AS uuid), CAST(? AS uuid), CAST(? AS numeric), CAST(? AS date),"
            + " CAST(? AS date))",
        id.toString(),
        VENDEDOR,
        pct,
        desde,
        hasta);
    return id;
  }

  private java.math.BigDecimal porcentajeDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT percentage FROM commission_rates WHERE id = CAST(? AS uuid)",
        java.math.BigDecimal.class,
        id.toString());
  }

  private boolean sigueLaFila(UUID id) {
    return jdbc.queryForObject(
            "SELECT count(*) FROM commission_rates WHERE id = CAST(? AS uuid)",
            Long.class,
            id.toString())
        == 1L;
  }

  private Object fechaDeRetiro(UUID id) {
    return jdbc.queryForObject(
        "SELECT deleted_at FROM commission_rates WHERE id = CAST(? AS uuid)",
        Object.class,
        id.toString());
  }

  private String vigenciaHastaDe(UUID id) {
    return jdbc.queryForObject(
        "SELECT CAST(valid_to AS text) FROM commission_rates WHERE id = CAST(? AS uuid)",
        String.class,
        id.toString());
  }

  private void limpiar() {
    jdbc.update("DELETE FROM commission_rates");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'CM'");
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'CM'");
  }
}
