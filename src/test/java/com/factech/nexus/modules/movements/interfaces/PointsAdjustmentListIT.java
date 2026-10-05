package com.factech.nexus.modules.movements.interfaces;

import static com.factech.nexus.modules.movements.LedgerFixtures.USD;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.LedgerFixtures;
import com.factech.nexus.modules.movements.domain.service.CreditService;
import java.util.UUID;
import org.hamcrest.Matchers;
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

/** `RF-MV-053` — consultar los ajustes de puntos; y `RF-MV-054` — los saldos de una persona. */
@AutoConfigureMockMvc
class PointsAdjustmentListIT extends IntegrationTestBase {

  private static final String RUTA = "/api/v1/movements/points-adjustments";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CreditService abonos;

  private UUID ana;
  private UUID jose;
  private UUID administrador;

  @BeforeEach
  void sembrar() throws Exception {
    limpiar();
    ana = persona("ajl-ana", "Ána María", "Pérez");
    jose = persona("ajl-jose", "José", "Gómez");
    administrador = persona("ajl-admin", "Laura", "Admin");

    // Tres ajustes, en este orden: +100 a Ana, +50 a José, −20 a Ana.
    ajustar(ana, "100", "Consignación Bancolombia", "CONS-001", "ajuste-lista-01");
    ajustar(jose, "50", "Pago por Nequi", null, "ajuste-lista-02");
    ajustar(ana, "-20", "Error de digitación", null, "ajuste-lista-03");
    // Un bono en la billetera: otro tipo, no debe salir (CA-MV-653).
    LedgerFixtures.llenarBilletera(abonos, ana, "10.00");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName(
      "CA-MV-647, CA-MV-653 y CA-MV-654 — todos los ajustes, los más recientes primero, con la"
          + " persona, los puntos con signo y quién lo hizo; ni bonos ni documento")
  void listado() throws Exception {
    mvc.perform(get(RUTA).with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content[0].points").value(-20))
        .andExpect(jsonPath("$.content[0].concept").value("Error de digitación"))
        .andExpect(jsonPath("$.content[2].points").value(100))
        .andExpect(jsonPath("$.content[2].code").value(Matchers.startsWith("AJP-")))
        .andExpect(jsonPath("$.content[2].status").value("CONFIRMADA"))
        .andExpect(jsonPath("$.content[2].reference").value("CONS-001"))
        .andExpect(jsonPath("$.content[2].user.id").value(ana.toString()))
        .andExpect(jsonPath("$.content[2].user.fullName").value("Ána María Pérez"))
        .andExpect(jsonPath("$.content[2].user.username").value("ajl-ana"))
        .andExpect(jsonPath("$.content[2].user.email").value("ajl-ana@factech.co"))
        .andExpect(jsonPath("$.content[2].user.document").doesNotExist())
        .andExpect(jsonPath("$.content[2].currency.code").value("USD"))
        .andExpect(jsonPath("$.content[2].adjustedBy.id").value(administrador.toString()))
        .andExpect(jsonPath("$.content[2].adjustedBy.fullName").value("Laura Admin"));

    // Un ajuste sin quién lo hizo —anterior a V73— sale con adjustedBy nulo.
    jdbc.update(
        "UPDATE movements SET recorded_by = NULL WHERE idempotency_key = 'ajuste-lista-02'");
    mvc.perform(get(RUTA).param("userId", jose.toString()).with(lector()))
        .andExpect(jsonPath("$.content[0].adjustedBy").value(Matchers.nullValue()));
  }

  @Test
  @DisplayName("CA-MV-648 y CA-MV-649 — persona, moneda, periodo y sentido filtran y se combinan")
  void filtros() throws Exception {
    mvc.perform(get(RUTA).param("userId", ana.toString()).with(lector()))
        .andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(get(RUTA).param("currencyId", USD).param("sign", "suma").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(get(RUTA).param("userId", ana.toString()).param("sign", "RESTA").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].points").value(-20));
    mvc.perform(get(RUTA).param("currencyId", UUID.randomUUID().toString()).with(lector()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(
            get(RUTA)
                .param("from", "2000-01-01T00:00:00Z")
                .param("to", "2100-01-01T00:00:00Z")
                .with(lector()))
        .andExpect(jsonPath("$.totalElements").value(3));
    mvc.perform(get(RUTA).param("to", "2000-01-01T00:00:00Z").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName(
      "CA-MV-650 — la búsqueda va por comprobante, motivo, referencia, nombre, usuario y correo,"
          + " sin acentos ni mayúsculas")
  void busqueda() throws Exception {
    mvc.perform(get(RUTA).param("q", "bancolombia").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get(RUTA).param("q", "cons-0").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get(RUTA).param("q", "ana maria").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(get(RUTA).param("q", "JOSE").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get(RUTA).param("q", "ajl-jose@").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get(RUTA).param("q", "ajp-").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(3));
    mvc.perform(get(RUTA).param("q", "%").with(lector()))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName(
      "CA-MV-651 y CA-MV-652 — se ordena por fecha, puntos o comprobante; lo inválido es 400,"
          + " todo junto")
  void ordenYValidaciones() throws Exception {
    mvc.perform(get(RUTA).param("sort", "points,asc").with(lector()))
        .andExpect(jsonPath("$.content[0].points").value(-20))
        .andExpect(jsonPath("$.content[2].points").value(100));
    mvc.perform(get(RUTA).param("sort", "points").with(lector()))
        .andExpect(jsonPath("$.content[0].points").value(100));
    mvc.perform(get(RUTA).param("sort", "occurredAt,asc").with(lector()))
        .andExpect(jsonPath("$.content[0].points").value(100));
    mvc.perform(get(RUTA).param("sort", "code,desc").with(lector())).andExpect(status().isOk());

    mvc.perform(
            get(RUTA)
                .param("sort", "concept")
                .param("sign", "OTRO")
                .param("from", "2026-12-01T00:00:00Z")
                .param("to", "2026-01-01T00:00:00Z")
                .with(lector()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(3));
    mvc.perform(get(RUTA).param("sort", "points,arriba").with(lector()))
        .andExpect(status().isBadRequest());
    mvc.perform(get(RUTA).param("size", "100000").with(lector()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("CA-MV-655 — sin movements:list-points-adjustments, 403; sin sesión, 401")
  void permisosDelListado() throws Exception {
    mvc.perform(
            get(RUTA)
                .with(user(administrador.toString()).authorities(() -> "movements:adjust-points")))
        .andExpect(status().isForbidden());
    mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-054`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-656 y CA-MV-657 — los saldos de la persona indicada, no los de quien pregunta; sin"
          + " cuentas, lista vacía")
  void saldosDeUnaPersona() throws Exception {
    mvc.perform(get("/api/v1/movements/users/{id}/balances", ana).with(saldos()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].currency.code").value("USD"))
        .andExpect(jsonPath("$[0].points").value(80))
        .andExpect(jsonPath("$[0].wallet").value(10))
        .andExpect(jsonPath("$[0].held").value(0));
    mvc.perform(get("/api/v1/movements/users/{id}/balances", administrador).with(saldos()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName(
      "CA-MV-658 y CA-MV-659 — inexistente o eliminada, 404; sin permiso, 403; sin sesión, 401")
  void saldosBordes() throws Exception {
    mvc.perform(get("/api/v1/movements/users/{id}/balances", UUID.randomUUID()).with(saldos()))
        .andExpect(status().isNotFound());
    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", jose);
    mvc.perform(get("/api/v1/movements/users/{id}/balances", jose).with(saldos()))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/movements/users/{id}/balances", ana)
                .with(
                    user(administrador.toString())
                        .authorities(() -> "movements:read-own-balances")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/movements/users/{id}/balances", ana))
        .andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------

  private void ajustar(UUID quien, String puntos, String motivo, String referencia, String clave)
      throws Exception {
    MockHttpServletRequestBuilder p =
        post(RUTA)
            .header("Idempotency-Key", clave)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"userId\":\"%s\",\"currencyId\":\"%s\",\"points\":%s,\"concept\":\"%s\"%s}"
                    .formatted(
                        quien,
                        USD,
                        puntos,
                        motivo,
                        referencia == null ? "" : ",\"reference\":\"" + referencia + "\""))
            .with(user(administrador.toString()).authorities(() -> "movements:adjust-points"));
    mvc.perform(p).andExpect(status().isCreated());
    // Instantes distintos y en orden, sin depender del reloj.
    jdbc.update(
        "UPDATE movements SET occurred_at = occurred_at - make_interval(mins => ?)"
            + " WHERE idempotency_key = ?",
        10 - Integer.parseInt(clave.substring(clave.length() - 2)),
        clave);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor lector() {
    return user(administrador.toString()).authorities(() -> "movements:list-points-adjustments");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor saldos() {
    return user(administrador.toString()).authorities(() -> "movements:read-user-balances");
  }

  private void limpiar() {
    LedgerFixtures.limpiar(jdbc);
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'ajl-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'ajl-%'");
  }

  private UUID persona(String username, String nombres, String apellidos) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, ?, ?, 'x', false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@factech.co",
        nombres,
        apellidos);
    darElSuelo(jdbc, id);
    return id;
  }
}
