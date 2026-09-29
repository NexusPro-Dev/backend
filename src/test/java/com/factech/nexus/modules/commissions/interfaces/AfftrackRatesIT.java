package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.AGENTE;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.MANAGER;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.NO_VENDEDOR;
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

/**
 * Los escalones afftrack de rol: alta, listado, corrección y retiro (`RF-CM-015` a `RF-CM-018`).
 *
 * <p>Los criterios que necesitan un cierre —`CA-CM-222`, `CA-CM-227` y `CA-CM-228`— viven en {@code
 * AfftrackSettlementIT}, con la liquidación (`RF-CM-020`).
 */
@AutoConfigureMockMvc
class AfftrackRatesIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private UUID ftd;

  @BeforeEach
  void preparar() {
    limpiar();
    reponerElSuelo(jdbc);
    ftd = AfftrackFixtures.productoFtd(jdbc, "FTD", false);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  private void limpiar() {
    AfftrackFixtures.limpiar(jdbc);
    CommissionFixtures.limpiar(jdbc, SUPERADMIN);
  }

  // ---- RF-CM-015 · el alta -------------------------------------------------

  @Test
  @DisplayName("CA-CM-209 · registra el escalón con rol y producto resueltos y lo que paga entero")
  void registra() throws Exception {
    mvc.perform(alta(ftd, MANAGER, "50", "8000"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role.code").value("MANAGER"))
        .andExpect(jsonPath("$.product.id").value(ftd.toString()))
        .andExpect(jsonPath("$.product.code").value("AF_FTD"))
        .andExpect(jsonPath("$.product.currency.code").isNotEmpty())
        .andExpect(jsonPath("$.threshold").value(50))
        .andExpect(jsonPath("$.amountPerFtd").value(8000))
        .andExpect(jsonPath("$.amountAtThreshold").value(400000));
  }

  @Test
  @DisplayName("CA-CM-210 · varios escalones del mismo rol y producto con límites distintos")
  void variosLimites() throws Exception {
    mvc.perform(alta(ftd, MANAGER, "50", "8000")).andExpect(status().isCreated());
    mvc.perform(alta(ftd, MANAGER, "60", "9000")).andExpect(status().isCreated());
    mvc.perform(alta(ftd, AGENTE, "50", "7000")).andExpect(status().isCreated());
    assertThat(escalones()).isEqualTo(3);
  }

  @Test
  @DisplayName("CA-CM-211 · el mismo límite vivo: 409; retirado el primero, entra")
  void mismoLimite() throws Exception {
    UUID primero = idDe(alta(ftd, MANAGER, "50", "8000"));
    mvc.perform(alta(ftd, MANAGER, "50", "9000"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));

    mvc.perform(retiro(primero, "Se sustituye")).andExpect(status().isNoContent());
    mvc.perform(alta(ftd, MANAGER, "50", "9000")).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("CA-CM-211 · dos altas simultáneas del mismo límite: una queda y la otra recibe 409")
  void dosAltasSimultaneas() {
    List<Outcome<Integer>> resultados =
        runTogether(2, indice -> estado(alta(ftd, MANAGER, "50", indice == 0 ? "8000" : "9000")));

    assertThat(resultados).allMatch(Outcome::succeeded);
    assertThat(resultados).extracting(Outcome::value).containsExactlyInAnyOrder(201, 409);
    assertThat(escalones()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "CA-CM-212 · un producto que no es FTD se rechaza con EX-005, distinto de EX-003/004")
  void productoNoFtd() throws Exception {
    UUID otraPareja = AfftrackFixtures.upgradeNoFtd(jdbc, "VIP");
    UUID bot = CommissionFixtures.sembrarProducto(jdbc, "AF_BOT");
    UUID retirado = AfftrackFixtures.productoFtd(jdbc, "FTD_RET", true);

    for (UUID noFtd : List.of(otraPareja, bot)) {
      mvc.perform(alta(noFtd, MANAGER, "50", "8000"))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.errors[0].code").value("EX-005"));
    }
    mvc.perform(alta(UUID.randomUUID(), MANAGER, "50", "8000"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
    mvc.perform(alta(retirado, MANAGER, "50", "8000"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
    assertThat(escalones()).isZero();
  }

  @Test
  @DisplayName("CA-CM-213 · rol inexistente 422 y rol que no vende 400, distinguidos")
  void roles() throws Exception {
    mvc.perform(alta(ftd, UUID.randomUUID().toString(), "50", "8000"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
    mvc.perform(alta(ftd, NO_VENDEDOR, "50", "8000"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("EX-001"));
  }

  @Test
  @DisplayName(
      "CA-CM-214 · límite cero, negativo o decimal y valor negativo, todos juntos; el 0 entra")
  void forma() throws Exception {
    mvc.perform(
            post("/api/v1/afftrack-rates")
                .with(como("afftrack-rates:create"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"threshold\":0,\"amountPerFtd\":-1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(4));
    mvc.perform(alta(ftd, MANAGER, "-5", "8000")).andExpect(status().isBadRequest());
    mvc.perform(alta(ftd, MANAGER, "10.5", "8000"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
    mvc.perform(alta(ftd, MANAGER, "10", "0")).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("CA-CM-215 · decimales de más para la moneda del producto: 400; los justos entran")
  void decimales() throws Exception {
    int decimales = decimalesDeLaMoneda();
    String demasiados = "1." + "1".repeat(decimales + 1);
    if (decimales < 4) {
      mvc.perform(alta(ftd, MANAGER, "50", demasiados))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].code").value("VAL-005"));
    }
    String justos = decimales == 0 ? "1" : "1." + "1".repeat(decimales);
    mvc.perform(alta(ftd, MANAGER, "50", justos)).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("CA-CM-216 · el alta queda auditada; sin afftrack-rates:create, 403")
  void auditoriaYPermiso() throws Exception {
    UUID id = idDe(alta(ftd, MANAGER, "50", "8000"));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE entity = 'afftrack_rates'"
                    + " AND entity_id = ? AND action = 'CREATE'",
                Long.class,
                id))
        .isEqualTo(1);
    mvc.perform(
            post("/api/v1/afftrack-rates")
                .with(como("afftrack-rates:read"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(ftd, MANAGER, "50", "8000")))
        .andExpect(status().isForbidden());
  }

  // ---- RF-CM-016 · el listado ----------------------------------------------

  @Test
  @DisplayName("CA-CM-217 · lista la escala por producto, rol y límite ascendente, con lo que paga")
  void lista() throws Exception {
    idDe(alta(ftd, MANAGER, "60", "9000"));
    idDe(alta(ftd, MANAGER, "50", "8000"));
    mvc.perform(get("/api/v1/afftrack-rates").with(como("afftrack-rates:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].threshold").value(50))
        .andExpect(jsonPath("$.content[1].threshold").value(60))
        .andExpect(jsonPath("$.content[1].amountAtThreshold").value(540000))
        .andExpect(jsonPath("$.content[0].role.code").value("MANAGER"));
  }

  @Test
  @DisplayName("CA-CM-218 · filtra por producto y por rol, combinables")
  void filtra() throws Exception {
    UUID otro = AfftrackFixtures.productoFtd(jdbc, "FTD2", false);
    idDe(alta(ftd, MANAGER, "50", "8000"));
    idDe(alta(ftd, AGENTE, "50", "8000"));
    idDe(alta(otro, MANAGER, "50", "8000"));
    mvc.perform(
            get("/api/v1/afftrack-rates")
                .param("productId", ftd.toString())
                .param("roleId", MANAGER)
                .with(como("afftrack-rates:read")))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(
            get("/api/v1/afftrack-rates")
                .param("roleId", MANAGER)
                .with(como("afftrack-rates:read")))
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  @DisplayName("CA-CM-219 · los retirados no salen por omisión; al pedirlos, marcados y sin motivo")
  void retirados() throws Exception {
    UUID id = idDe(alta(ftd, MANAGER, "50", "8000"));
    mvc.perform(retiro(id, "Ya no se paga")).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/afftrack-rates").with(como("afftrack-rates:read")))
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(
            get("/api/v1/afftrack-rates")
                .param("includeDeleted", "true")
                .with(como("afftrack-rates:read")))
        .andExpect(jsonPath("$.content[0].deletedAt").isNotEmpty())
        .andExpect(jsonPath("$.content[0].reason").doesNotExist());
  }

  @Test
  @DisplayName("CA-CM-220 · sin afftrack-rates:read, 403; y NO una consulta por fila")
  void listadoPermisoYSentencias() throws Exception {
    mvc.perform(get("/api/v1/afftrack-rates").with(como("afftrack-rates:create")))
        .andExpect(status().isForbidden());
    idDe(alta(ftd, MANAGER, "50", "8000"));
    long conUno = sentenciasDelListado();
    idDe(alta(ftd, MANAGER, "60", "8000"));
    idDe(alta(ftd, AGENTE, "50", "8000"));
    assertThat(sentenciasDelListado()).isEqualTo(conUno);
  }

  // ---- RF-CM-017 · la corrección -------------------------------------------

  @Test
  @DisplayName("CA-CM-221 · corrige el valor, el límite o los dos, y recalcula lo que paga")
  void corrige() throws Exception {
    UUID id = idDe(alta(ftd, MANAGER, "50", "8000"));
    mvc.perform(correccion(id, "{\"amountPerFtd\":9000}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amountAtThreshold").value(450000));
    mvc.perform(correccion(id, "{\"threshold\":40,\"amountPerFtd\":1000}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.threshold").value(40))
        .andExpect(jsonPath("$.amountAtThreshold").value(40000));
  }

  @Test
  @DisplayName("CA-CM-223 · retirado o inexistente: 404")
  void corregirInexistente() throws Exception {
    UUID id = idDe(alta(ftd, MANAGER, "50", "8000"));
    mvc.perform(retiro(id, "Fuera")).andExpect(status().isNoContent());
    mvc.perform(correccion(id, "{\"amountPerFtd\":1}")).andExpect(status().isNotFound());
    mvc.perform(correccion(UUID.randomUUID(), "{\"amountPerFtd\":1}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("CA-CM-224 · el rol o el producto se rechazan, y una petición sin nada corregible")
  void corregirInmutables() throws Exception {
    UUID id = idDe(alta(ftd, MANAGER, "50", "8000"));
    mvc.perform(correccion(id, "{\"roleId\":\"" + AGENTE + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
    mvc.perform(correccion(id, "{\"productId\":\"" + ftd + "\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(correccion(id, "{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
    mvc.perform(correccion(id, "{\"threshold\":null}")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "CA-CM-225 · corregir a un límite que ya tiene otro escalón vivo: 409; retirado, entra")
  void corregirChoque() throws Exception {
    UUID cincuenta = idDe(alta(ftd, MANAGER, "50", "8000"));
    UUID sesenta = idDe(alta(ftd, MANAGER, "60", "9000"));
    mvc.perform(correccion(sesenta, "{\"threshold\":50}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));
    mvc.perform(retiro(cincuenta, "Fuera")).andExpect(status().isNoContent());
    mvc.perform(correccion(sesenta, "{\"threshold\":50}")).andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "CA-CM-226 · sin cambios no escribe ni audita; con cambios deja el antes y el después")
  void corregirAuditoria() throws Exception {
    UUID id = idDe(alta(ftd, MANAGER, "50", "8000"));
    mvc.perform(correccion(id, "{\"amountPerFtd\":8000.0000,\"threshold\":50}"))
        .andExpect(status().isOk());
    assertThat(actualizaciones(id)).isZero();
    mvc.perform(correccion(id, "{\"amountPerFtd\":8500}")).andExpect(status().isOk());
    assertThat(actualizaciones(id)).isEqualTo(1);
  }

  // ---- RF-CM-018 · el retiro -----------------------------------------------

  @Test
  @DisplayName("CA-CM-229 · inexistente 404; dos veces 409; sin motivo 400")
  void retiro() throws Exception {
    UUID id = idDe(alta(ftd, MANAGER, "50", "8000"));
    mvc.perform(retiro(id, " ")).andExpect(status().isBadRequest());
    mvc.perform(retiro(UUID.randomUUID(), "Fuera")).andExpect(status().isNotFound());
    mvc.perform(retiro(id, "Fuera")).andExpect(status().isNoContent());
    mvc.perform(retiro(id, "Otra vez")).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("CA-CM-230 · el retiro queda en la auditoría con su motivo; sin permiso, 403")
  void retiroAuditoria() throws Exception {
    UUID id = idDe(alta(ftd, MANAGER, "50", "8000"));
    mvc.perform(
            post("/api/v1/afftrack-rates/{id}/deletion", id)
                .with(como("afftrack-rates:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Fuera\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(retiro(id, "Ya no se paga")).andExpect(status().isNoContent());
    assertThat(
            jdbc.queryForObject(
                "SELECT reason FROM audit_deletion_log WHERE entity = 'afftrack_rates'"
                    + " AND entity_id = ?",
                String.class,
                id))
        .isEqualTo("Ya no se paga");
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder alta(
      UUID producto, String rol, String limite, String valor) {
    return post("/api/v1/afftrack-rates")
        .with(como("afftrack-rates:create"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(producto, rol, limite, valor));
  }

  private static String cuerpo(UUID producto, String rol, String limite, String valor) {
    return "{\"productId\":\"%s\",\"roleId\":\"%s\",\"threshold\":%s,\"amountPerFtd\":%s}"
        .formatted(producto, rol, limite, valor);
  }

  private MockHttpServletRequestBuilder correccion(UUID id, String json) {
    return patch("/api/v1/afftrack-rates/{id}", id)
        .with(como("afftrack-rates:update"))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  private MockHttpServletRequestBuilder retiro(UUID id, String motivo) {
    return post("/api/v1/afftrack-rates/{id}/deletion", id)
        .with(como("afftrack-rates:delete"))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"" + motivo + "\"}");
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

  private long escalones() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM afftrack_rates WHERE deleted_at IS NULL", Long.class);
  }

  private long actualizaciones(UUID id) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_change_log WHERE entity = 'afftrack_rates'"
            + " AND entity_id = ? AND action = 'UPDATE'",
        Long.class,
        id);
  }

  private int decimalesDeLaMoneda() {
    return jdbc.queryForObject(
        "SELECT c.decimal_places FROM products p JOIN currencies c ON c.id = p.currency_id"
            + " WHERE p.id = ?",
        Integer.class,
        ftd);
  }

  private long sentenciasDelListado() throws Exception {
    Statistics estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
    mvc.perform(get("/api/v1/afftrack-rates").with(como("afftrack-rates:read")))
        .andExpect(status().isOk());
    return estadisticas.getPrepareStatementCount();
  }

  private static RequestPostProcessor como(String permiso) {
    return user(SUPERADMIN.toString()).authorities(() -> permiso);
  }
}
