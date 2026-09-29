package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.MANAGER;
import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Los escalones afftrack de persona y sus cuatro operaciones (`RF-CM-019`). */
@AutoConfigureMockMvc
class UserAfftrackRatesIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private UUID ftd;
  private UUID vendedora;

  @BeforeEach
  void preparar() {
    limpiar();
    reponerElSuelo(jdbc);
    ftd = AfftrackFixtures.productoFtd(jdbc, "FTD", false);
    vendedora = CommissionFixtures.sembrarPersonaConRol(jdbc, "af-vendedora", MANAGER);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  private void limpiar() {
    AfftrackFixtures.limpiar(jdbc);
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
  }

  @Test
  @DisplayName("CA-CM-231 · registra el escalón con persona y producto resueltos y su vigencia")
  void registra() throws Exception {
    mvc.perform(alta(ftd, "40", "9000", "2026-01-01", null))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.user.id").value(vendedora.toString()))
        .andExpect(jsonPath("$.user.username").value("af-vendedora"))
        .andExpect(jsonPath("$.product.code").value("AF_FTD"))
        .andExpect(jsonPath("$.amountAtThreshold").value(360000))
        .andExpect(jsonPath("$.validFrom").value("2026-01-01"))
        .andExpect(jsonPath("$.validTo").doesNotExist());
  }

  @Test
  @DisplayName("CA-CM-232 · varios límites a la vez, y el mismo límite en vigencias consecutivas")
  void escala() throws Exception {
    mvc.perform(alta(ftd, "40", "9000", "2026-01-01", "2026-06-30"))
        .andExpect(status().isCreated());
    mvc.perform(alta(ftd, "60", "9500", "2026-01-01", "2026-06-30"))
        .andExpect(status().isCreated());
    mvc.perform(alta(ftd, "40", "9900", "2026-07-01", null)).andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "CA-CM-233 · el mismo límite con un día en común: 409; otro producto o retirado, entra")
  void solapamiento() throws Exception {
    UUID primero = idDe(alta(ftd, "40", "9000", "2026-01-01", "2026-12-31"));
    mvc.perform(alta(ftd, "40", "9000", "2026-12-31", null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
    UUID otro = AfftrackFixtures.productoFtd(jdbc, "FTD2", false);
    mvc.perform(alta(otro, "40", "9000", "2026-06-01", null)).andExpect(status().isCreated());
    mvc.perform(retiro(primero, "Fuera")).andExpect(status().isNoContent());
    mvc.perform(alta(ftd, "40", "9000", "2026-06-01", null)).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("CA-CM-233 · dos altas simultáneas del mismo límite y periodo: una y un 409")
  void simultaneas() {
    List<Outcome<Integer>> resultados =
        runTogether(
            2,
            indice ->
                estado(alta(ftd, "40", indice == 0 ? "9000" : "9500", "2026-01-01", "2026-12-31")));
    assertThat(resultados).allMatch(Outcome::succeeded);
    assertThat(resultados).extracting(Outcome::value).containsExactlyInAnyOrder(201, 409);
  }

  @Test
  @DisplayName("CA-CM-234 · producto no FTD, inexistente o retirado, y persona inexistente")
  void rechazos() throws Exception {
    UUID otraPareja = AfftrackFixtures.upgradeNoFtd(jdbc, "VIP");
    UUID retirado = AfftrackFixtures.productoFtd(jdbc, "FTD_RET", true);
    mvc.perform(alta(otraPareja, "40", "9000", "2026-01-01", null))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-005"));
    mvc.perform(alta(UUID.randomUUID(), "40", "9000", "2026-01-01", null))
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
    mvc.perform(alta(retirado, "40", "9000", "2026-01-01", null))
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
    mvc.perform(
            post("/api/v1/user-afftrack-rates")
                .with(como("user-afftrack-rates:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(UUID.randomUUID(), ftd, "40", "9000", "2026-01-01", null)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"));
  }

  @Test
  @DisplayName("CA-CM-235 · obligatorios, límite, valor y fin anterior al inicio")
  void forma() throws Exception {
    mvc.perform(
            post("/api/v1/user-afftrack-rates")
                .with(como("user-afftrack-rates:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(5));
    mvc.perform(alta(ftd, "0", "-1", "2026-01-01", null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(2));
    mvc.perform(alta(ftd, "40", "9000", "2026-06-01", "2026-05-31"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));
  }

  @Test
  @DisplayName(
      "CA-CM-236 · con onDate salen exactamente los escalones que el cierre aplicaría ese día")
  void vigentesEn() throws Exception {
    idDe(alta(ftd, "40", "9000", "2026-01-01", "2026-06-30"));
    idDe(alta(ftd, "60", "9500", "2026-01-01", null));
    UUID retirado = idDe(alta(ftd, "80", "9900", "2026-01-01", null));
    mvc.perform(retiro(retirado, "Fuera")).andExpect(status().isNoContent());

    mvc.perform(listado().param("userId", vendedora.toString()).param("onDate", "2026-03-15"))
        .andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(listado().param("userId", vendedora.toString()).param("onDate", "2026-07-01"))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].threshold").value(60));
    mvc.perform(listado().param("onDate", "2025-12-31"))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("CA-CM-237 · corrige límite, valor y fin; el fin vacío lo quita; un choque es 409")
  void corrige() throws Exception {
    UUID id = idDe(alta(ftd, "40", "9000", "2026-01-01", "2026-06-30"));
    idDe(alta(ftd, "40", "9000", "2026-07-01", null));
    mvc.perform(correccion(id, "{\"amountPerFtd\":9100,\"threshold\":45}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.threshold").value(45));
    mvc.perform(correccion(id, "{\"threshold\":40,\"validTo\":null}"))
        .andExpect(status().isConflict());
    UUID aparte = idDe(alta(ftd, "70", "1", "2026-01-01", "2026-02-01"));
    mvc.perform(correccion(aparte, "{\"validTo\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.validTo").doesNotExist());
  }

  @Test
  @DisplayName("CA-CM-238 · inmutables rechazados, retirado 404, y sin cambios no audita")
  void corregirRestricciones() throws Exception {
    UUID id = idDe(alta(ftd, "40", "9000", "2026-01-01", null));
    mvc.perform(correccion(id, "{\"validFrom\":\"2026-02-01\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("EX-008"));
    mvc.perform(correccion(id, "{\"amountPerFtd\":9000.00}")).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE entity = 'user_afftrack_rates'"
                    + " AND entity_id = ? AND action = 'UPDATE'",
                Long.class,
                id))
        .isZero();
    mvc.perform(retiro(id, "Fuera")).andExpect(status().isNoContent());
    mvc.perform(correccion(id, "{\"amountPerFtd\":1}")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("CA-CM-239 · se retira con motivo; dos veces 409; sus días quedan libres")
  void retira() throws Exception {
    UUID id = idDe(alta(ftd, "40", "9000", "2026-01-01", null));
    mvc.perform(retiro(id, "")).andExpect(status().isBadRequest());
    mvc.perform(retiro(id, "Fuera")).andExpect(status().isNoContent());
    mvc.perform(retiro(id, "Otra")).andExpect(status().isConflict());
    mvc.perform(alta(ftd, "40", "9000", "2026-01-01", null)).andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "CA-CM-240 · cada operación exige su permiso; el listado no hace una consulta por fila")
  void permisosYSentencias() throws Exception {
    mvc.perform(get("/api/v1/user-afftrack-rates").with(como("user-afftrack-rates:create")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/user-afftrack-rates")
                .with(como("user-afftrack-rates:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(vendedora, ftd, "40", "9000", "2026-01-01", null)))
        .andExpect(status().isForbidden());
    idDe(alta(ftd, "40", "9000", "2026-01-01", null));
    long conUno = sentencias();
    UUID otra = CommissionFixtures.sembrarPersonaConRol(jdbc, "af-otra", MANAGER);
    mvc.perform(
            post("/api/v1/user-afftrack-rates")
                .with(como("user-afftrack-rates:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(otra, ftd, "40", "9000", "2026-01-01", null)))
        .andExpect(status().isCreated());
    idDe(alta(ftd, "60", "9000", "2026-01-01", null));
    assertThat(sentencias()).isEqualTo(conUno);
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder alta(
      UUID producto, String limite, String valor, String desde, String hasta) {
    return post("/api/v1/user-afftrack-rates")
        .with(como("user-afftrack-rates:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(vendedora, producto, limite, valor, desde, hasta));
  }

  private static String cuerpo(
      UUID persona, UUID producto, String limite, String valor, String desde, String hasta) {
    return ("{\"userId\":\"%s\",\"productId\":\"%s\",\"threshold\":%s,\"amountPerFtd\":%s,"
            + "\"validFrom\":\"%s\",\"validTo\":%s}")
        .formatted(
            persona, producto, limite, valor, desde, hasta == null ? "null" : "\"" + hasta + "\"");
  }

  private MockHttpServletRequestBuilder correccion(UUID id, String json) {
    return patch("/api/v1/user-afftrack-rates/{id}", id)
        .with(como("user-afftrack-rates:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  private MockHttpServletRequestBuilder retiro(UUID id, String motivo) {
    return post("/api/v1/user-afftrack-rates/{id}/deletion", id)
        .with(como("user-afftrack-rates:delete"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"" + motivo + "\"}");
  }

  private MockHttpServletRequestBuilder listado() {
    return get("/api/v1/user-afftrack-rates").with(como("user-afftrack-rates:read"));
  }

  private UUID idDe(MockHttpServletRequestBuilder peticion) throws Exception {
    String json =
        mvc.perform(peticion)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(com.jayway.jsonpath.JsonPath.read(json, "$.id"));
  }

  private Integer estado(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }

  private long sentencias() throws Exception {
    Statistics estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
    mvc.perform(listado()).andExpect(status().isOk());
    return estadisticas.getPrepareStatementCount();
  }

  private static RequestPostProcessor como(String permiso) {
    return user(SUPERADMIN.toString()).authorities(() -> permiso);
  }
}
