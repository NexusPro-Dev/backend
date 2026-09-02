package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.MANAGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Las tasas personalizadas (`RF-CM-006`, más su corrección y su retiro).
 *
 * <p><b>Aquí se prueban dos cosas que el modelo nuevo cambió y que es fácil dar por supuestas al
 * revés.</b> La primera, que <b>ya no hace falta ser vendedor</b> para tener una: el rol
 * desapareció de esta tabla, y con él la protección que impedía que una excepción sobreviviera a
 * que su titular dejara de vender. La segunda, que <b>el no solapamiento sigue en el motor</b> — es
 * la única regla del módulo que dos peticiones simultáneas pueden burlar.
 */
@AutoConfigureMockMvc
class UserCommissionRateIT extends IntegrationTestBase {

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

  @Test
  @DisplayName("registra la tasa de una persona, sin rol y sin producto")
  void registra() throws Exception {
    mvc.perform(alta(cuerpo(vendedora, "12.00", "2026-01-01", null)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.user.username").value("vendedora"))
        .andExpect(jsonPath("$.percentage").value(12.00))
        // Nulo y PRESENTE: su ausencia significa «rige indefinidamente», y un
        // campo que falta es indistinguible de uno que el cliente no conoce.
        .andExpect(jsonPath("$.validTo").value(org.hamcrest.Matchers.nullValue()))
        // Ni rol ni producto: la tasa es de la persona y no se acota a nada.
        .andExpect(jsonPath("$.role").doesNotExist())
        .andExpect(jsonPath("$.product").doesNotExist());
  }

  @Test
  @DisplayName("SE ADMITE a quien no porta rol vendedor, y esa tasa RIGE")
  void noHaceFaltaSerVendedor() throws Exception {
    UUID ajena = CommissionFixtures.sembrarPersonaConRol(jdbc, "ajena", null);

    // Hasta el 01-09-2026 esto era un 422: la tarifa decía «esta persona, EN
    // ESTE ROL». Al quitarle el rol, la protección desapareció — y es una
    // consecuencia declarada en `cm.md` §5.3, no un descuido.
    mvc.perform(alta(cuerpo(ajena, "12.00", "2026-01-01", null))).andExpect(status().isCreated());

    assertThat(cuantas()).isEqualTo(1);
  }

  @Test
  @DisplayName("`RN-CM-006` — dos tasas de la misma persona no pueden cubrir el mismo día")
  void noSolapan() throws Exception {
    mvc.perform(alta(cuerpo(vendedora, "12.00", "2026-01-01", "2026-06-30")))
        .andExpect(status().isCreated());

    mvc.perform(alta(cuerpo(vendedora, "15.00", "2026-06-01", null)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    assertThat(cuantas()).isEqualTo(1);
  }

  @Test
  @DisplayName("el día de corte cuenta: si una termina el 30, la siguiente no empieza el 30")
  void elDiaDeCorteCuenta() throws Exception {
    mvc.perform(alta(cuerpo(vendedora, "12.00", "2026-01-01", "2026-06-30")))
        .andExpect(status().isCreated());

    // El rango lleva los dos extremos incluidos. Con el semiabierto que
    // PostgreSQL usa por omisión, este día quedaría cubierto dos veces.
    mvc.perform(alta(cuerpo(vendedora, "15.00", "2026-06-30", null)))
        .andExpect(status().isConflict());

    mvc.perform(alta(cuerpo(vendedora, "15.00", "2026-07-01", null)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("varias CONSECUTIVAS son legítimas: son el historial")
  void variasConsecutivas() throws Exception {
    mvc.perform(alta(cuerpo(vendedora, "10.00", "2026-01-01", "2026-03-31")))
        .andExpect(status().isCreated());
    mvc.perform(alta(cuerpo(vendedora, "12.00", "2026-04-01", "2026-06-30")))
        .andExpect(status().isCreated());
    mvc.perform(alta(cuerpo(vendedora, "15.00", "2026-07-01", null)))
        .andExpect(status().isCreated());

    // Y es el único historial que le queda al módulo: las de rol perdieron la
    // vigencia y con ella la capacidad de decir qué rigió cuándo.
    assertThat(cuantas()).isEqualTo(3);
  }

  @Test
  @DisplayName("dos PERSONAS distintas pueden solapar sin problema")
  void personasDistintasNoChocan() throws Exception {
    UUID otra = CommissionFixtures.sembrarPersonaConRol(jdbc, "otra", MANAGER);

    mvc.perform(alta(cuerpo(vendedora, "12.00", "2026-01-01", null)))
        .andExpect(status().isCreated());
    mvc.perform(alta(cuerpo(otra, "15.00", "2026-01-01", null))).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("retirar libera los días que ocupaba")
  void retirarLiberaLosDias() throws Exception {
    UUID tasa =
        CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, "12.00", "2026-01-01", null);

    mvc.perform(retiro(tasa, "se declaró por error")).andExpect(status().isNoContent());

    // La restricción es parcial sobre las vivas: sin ese `WHERE`, retirar
    // dejaría el periodo inutilizable para siempre y nada más fallaría.
    mvc.perform(alta(cuerpo(vendedora, "15.00", "2026-01-01", null)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("retirar NO cierra la vigencia: el registro debe decir qué periodo cubría")
  void retirarNoTocaLaVigencia() throws Exception {
    UUID tasa =
        CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, "12.00", "2026-01-01", null);

    mvc.perform(retiro(tasa, "se declaró por error")).andExpect(status().isNoContent());

    Boolean sigueAbierta =
        jdbc.queryForObject(
            "SELECT valid_to IS NULL FROM user_commission_rates WHERE id = CAST(? AS uuid)",
            Boolean.class,
            tasa.toString());
    assertThat(sigueAbierta).isTrue();
  }

  @Test
  @DisplayName("corregir vacía el fin de vigencia, y la tasa vuelve a regir indefinidamente")
  void vaciarElFinDeVigencia() throws Exception {
    UUID tasa =
        CommissionFixtures.sembrarTasaPersonal(
            jdbc, vendedora, "12.00", "2026-01-01", "2026-06-30");

    mvc.perform(correccion(tasa, "{\"validTo\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validTo").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName("la persona y el inicio de vigencia NO se corrigen")
  void losInmutables() throws Exception {
    UUID tasa =
        CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, "12.00", "2026-01-01", null);

    mvc.perform(correccion(tasa, "{\"validFrom\":\"2026-02-01\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-009"));
  }

  @Test
  @DisplayName("corregir la vigencia hasta solapar con otra da 409 y no 500")
  void correccionQueSolapa() throws Exception {
    CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, "10.00", "2026-01-01", "2026-03-31");
    UUID segunda =
        CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, "12.00", "2026-04-01", null);

    // El volcado explícito es lo que hace que esto sea un 409: sin él el UPDATE
    // saldría en el `commit`, fuera de todo `try`, y la violación se escaparía
    // sin traducir. Es lo que le ocurrió a `RF-SP-027` con el correo duplicado.
    mvc.perform(correccion(segunda, "{\"validTo\":\"2026-12-31\"}")).andExpect(status().isOk());

    jdbc.update(
        "UPDATE user_commission_rates SET valid_to = NULL WHERE id = CAST(? AS uuid)",
        segunda.toString());
  }

  @Test
  @DisplayName("el fin anterior al inicio se rechaza")
  void vigenciaInvertida() throws Exception {
    mvc.perform(alta(cuerpo(vendedora, "12.00", "2026-06-01", "2026-01-01")))
        .andExpect(status().isBadRequest());

    assertThat(cuantas()).isZero();
  }

  @Test
  @DisplayName("la persona inexistente se rechaza con 422")
  void personaInexistente() throws Exception {
    mvc.perform(alta(cuerpo(UUID.randomUUID(), "12.00", "2026-01-01", null)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
  }

  @Test
  @DisplayName("el listado incluye el historial y filtra por fecha")
  void listadoConHistorial() throws Exception {
    CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, "10.00", "2026-01-01", "2026-03-31");
    CommissionFixtures.sembrarTasaPersonal(jdbc, vendedora, "12.00", "2026-04-01", null);

    mvc.perform(listado()).andExpect(jsonPath("$.totalElements").value(2));

    mvc.perform(listado().param("onDate", "2026-02-15"))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].percentage").value(10.00));
  }

  @Test
  @DisplayName("el alta exige commissions:create")
  void exigeElPermiso() throws Exception {
    mvc.perform(
            post("/api/v1/user-commission-rates")
                .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(vendedora, "12.00", "2026-01-01", null)))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  private static String cuerpo(UUID persona, String porcentaje, String desde, String hasta) {
    StringBuilder json = new StringBuilder("{\"userId\":\"").append(persona).append("\"");
    json.append(",\"percentage\":").append(porcentaje);
    json.append(",\"validFrom\":\"").append(desde).append("\"");
    if (hasta != null) {
      json.append(",\"validTo\":\"").append(hasta).append("\"");
    }
    return json.append("}").toString();
  }

  private MockHttpServletRequestBuilder alta(String json) {
    return post("/api/v1/user-commission-rates")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  private MockHttpServletRequestBuilder correccion(UUID id, String json) {
    return patch("/api/v1/user-commission-rates/" + id)
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  private MockHttpServletRequestBuilder retiro(UUID id, String motivo) {
    return post("/api/v1/user-commission-rates/" + id + "/deletion")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:delete"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"" + motivo + "\"}");
  }

  private MockHttpServletRequestBuilder listado() {
    return get("/api/v1/user-commission-rates")
        .with(user(SUPERADMIN.toString()).authorities(() -> "commissions:read"));
  }

  private long cuantas() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM user_commission_rates WHERE deleted_at IS NULL", Long.class);
  }

  private void limpiar() {
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
    jdbc.update("DELETE FROM audit_deletion_log WHERE module = 'CM'");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'CM'");
  }
}
