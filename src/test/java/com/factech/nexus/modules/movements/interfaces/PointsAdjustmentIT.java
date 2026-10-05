package com.factech.nexus.modules.movements.interfaces;

import static com.factech.nexus.modules.movements.LedgerFixtures.USD;
import static com.factech.nexus.modules.movements.LedgerFixtures.saldo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.LedgerFixtures;
import com.factech.nexus.shared.persistence.MinorUnits;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** `RF-MV-052` — ajustar los puntos de una persona (`RN-MV-076`). */
@AutoConfigureMockMvc
class PointsAdjustmentIT extends IntegrationTestBase {

  private static final String RUTA = "/api/v1/movements/points-adjustments";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID persona;
  private UUID administrador;

  @BeforeEach
  void sembrar() {
    limpiar();
    persona = persona("ajp-persona", "ACTIVO");
    administrador = persona("ajp-admin", "ACTIVO");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName(
      "CA-MV-636 y CA-MV-637 — suma: ajuste confirmado con motivo y referencia, sin pago ni"
          + " dinero; los puntos suben y los emitidos bajan, en dos asientos de AJUSTE")
  void suma() throws Exception {
    String cuerpo =
        mvc.perform(
                ajuste(
                    persona, "1500.50", "Consignación Bancolombia", "CONS-778812", "ajuste-000001"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(startsWith("AJP-")))
            .andExpect(jsonPath("$.status").value("CONFIRMADA"))
            .andExpect(jsonPath("$.userId").value(persona.toString()))
            .andExpect(jsonPath("$.currency.code").value("USD"))
            .andExpect(jsonPath("$.points").value(1500.50))
            .andExpect(jsonPath("$.concept").value("Consignación Bancolombia"))
            .andExpect(jsonPath("$.reference").value("CONS-778812"))
            .andExpect(jsonPath("$.confirmedAt").isNotEmpty())
            .andExpect(jsonPath("$.pointsBalance").value(1500.50))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID ajuste = UUID.fromString(JsonPath.read(cuerpo, "$.id"));

    assertThat(saldo(jdbc, persona, "PUNTOS")).isEqualByComparingTo("1500.50");
    assertThat(emitidos()).isEqualByComparingTo("-1500.50");

    // CA-MV-637: dos asientos de AJUSTE que suman cero, sin pago, sin dinero en la cabecera.
    List<Map<String, Object>> asientos =
        jdbc.queryForList(
            "SELECT event, amount, payment_id FROM movement_entries WHERE movement_id = ?", ajuste);
    assertThat(asientos).hasSize(2);
    assertThat(asientos).allSatisfy(a -> assertThat(a.get("event")).isEqualTo("AJUSTE"));
    assertThat(asientos).allSatisfy(a -> assertThat(a.get("payment_id")).isNull());
    assertThat(asientos.stream().mapToLong(a -> ((Number) a.get("amount")).longValue()).sum())
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE movement_id = ?", Integer.class, ajuste))
        .isZero();
    Map<String, Object> cabecera =
        jdbc.queryForMap(
            "SELECT payable_amount, total_amount, points_amount, points_rate_id,"
                + " external_reference FROM movements WHERE id = ?",
            ajuste);
    assertThat(((Number) cabecera.get("payable_amount")).longValue()).isZero();
    assertThat(((Number) cabecera.get("total_amount")).longValue()).isZero();
    assertThat(((Number) cabecera.get("points_amount")).longValue()).isEqualTo(150050L);
    assertThat(cabecera.get("points_rate_id")).isNull();
    assertThat(cabecera.get("external_reference")).isEqualTo("CONS-778812");
  }

  @Test
  @DisplayName(
      "CA-MV-638 y CA-MV-639 — la resta baja los puntos y sube los emitidos; la que no alcanza es"
          + " 422 con los disponibles y no escribe nada; hasta cero se admite")
  void resta() throws Exception {
    mvc.perform(ajuste(persona, "100", "Pago por fuera", null, "ajuste-000002"))
        .andExpect(status().isCreated());

    mvc.perform(ajuste(persona, "-30.25", "Error de digitación", null, "ajuste-000003"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.points").value(-30.25))
        .andExpect(jsonPath("$.reference").isEmpty())
        .andExpect(jsonPath("$.pointsBalance").value(69.75));
    assertThat(saldo(jdbc, persona, "PUNTOS")).isEqualByComparingTo("69.75");
    assertThat(emitidos()).isEqualByComparingTo("-69.75");

    int movimientos = movimientosDeAjuste();
    mvc.perform(ajuste(persona, "-69.76", "Demasiado", null, "ajuste-000004"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"))
        .andExpect(
            jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.containsString("69.75")));
    assertThat(saldo(jdbc, persona, "PUNTOS")).isEqualByComparingTo("69.75");
    assertThat(emitidos()).isEqualByComparingTo("-69.75");
    assertThat(movimientosDeAjuste()).isEqualTo(movimientos);

    // Quien no tiene cuenta de puntos tampoco puede bajar de cero.
    mvc.perform(ajuste(administrador, "-1", "Sin puntos", null, "ajuste-000005"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));

    mvc.perform(ajuste(persona, "-69.75", "Hasta cero", null, "ajuste-000006"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.pointsBalance").value(0));
    assertThat(saldo(jdbc, persona, "PUNTOS")).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName(
      "CA-MV-640 y CA-MV-641 — la misma petición no ajusta dos veces; la misma clave con otros"
          + " datos es 409 y no mueve nada")
  void idempotente() throws Exception {
    String primero =
        mvc.perform(ajuste(persona, "10", "Uno", null, "ajuste-000007"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    mvc.perform(ajuste(persona, "10", "Uno", null, "ajuste-000007"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value((String) JsonPath.read(primero, "$.id")))
        .andExpect(jsonPath("$.pointsBalance").value(10));
    mvc.perform(ajuste(persona, "11", "Uno", null, "ajuste-000007"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-005"));
    mvc.perform(ajuste(administrador, "10", "Uno", null, "ajuste-000007"))
        .andExpect(status().isConflict());
    assertThat(saldo(jdbc, persona, "PUNTOS")).isEqualByComparingTo("10");
    assertThat(saldo(jdbc, administrador, "PUNTOS")).isEqualByComparingTo("0");

    // Una clave usada por otro tipo de movimiento también es ajena.
    jdbc.update(
        "UPDATE movements SET idempotency_key = 'ajuste-000008' WHERE idempotency_key = 'ajuste-000007'");
    jdbc.update(
        "UPDATE movements SET movement_type_id = (SELECT id FROM movement_types WHERE code ="
            + " 'BONO'), type_status_id = (SELECT s.id FROM movement_type_statuses s JOIN"
            + " movement_types t ON t.id = s.movement_type_id WHERE t.code = 'BONO')"
            + " WHERE idempotency_key = 'ajuste-000008'");
    mvc.perform(ajuste(persona, "10", "Uno", null, "ajuste-000008"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName(
      "CA-MV-642 — a quien está bloqueado o espera su depósito se le ajusta; a quien no existe o"
          + " está eliminado, o en una moneda que no existe, no")
  void personas() throws Exception {
    UUID bloqueada = persona("ajp-bloqueada", "BLOQUEADO");
    UUID enEspera = persona("ajp-espera", "FTD_PENDIENTE");
    UUID eliminada = persona("ajp-eliminada", "INACTIVO");
    jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?", eliminada);

    mvc.perform(ajuste(bloqueada, "5", "Pago por fuera", null, "ajuste-000009"))
        .andExpect(status().isCreated());
    mvc.perform(ajuste(enEspera, "5", "Pago por fuera", null, "ajuste-000010"))
        .andExpect(status().isCreated());
    mvc.perform(ajuste(eliminada, "5", "Pago por fuera", null, "ajuste-000011"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
    mvc.perform(ajuste(UUID.randomUUID(), "5", "Pago por fuera", null, "ajuste-000012"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
    mvc.perform(
            peticion(
                    cuerpo(persona, UUID.randomUUID().toString(), "5", "Pago por fuera", null),
                    "movements:adjust-points")
                .header("Idempotency-Key", "ajuste-000013"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("currencyId"));
    assertThat(saldo(jdbc, eliminada, "PUNTOS")).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName(
      "CA-MV-643 — puntos en cero o con decimales de más, sin motivo, referencia en blanco o"
          + " larga, o sin clave: 400 y nada cambia")
  void validaciones() throws Exception {
    mvc.perform(ajuste(persona, "0", "Cero", null, "ajuste-000014"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    mvc.perform(ajuste(persona, "1.001", "Decimales", null, "ajuste-000015"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
    mvc.perform(ajuste(persona, "5", "   ", null, "ajuste-000016"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-004"));
    mvc.perform(ajuste(persona, "5", "Motivo", "   ", "ajuste-000017"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"));
    mvc.perform(ajuste(persona, "5", "Motivo", "R".repeat(121), "ajuste-000018"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-005"));
    mvc.perform(ajuste(persona, "5", "Sin clave", null, null)).andExpect(status().isBadRequest());
    mvc.perform(
            peticion("{\"currencyId\":\"" + USD + "\",\"points\":5,\"concept\":\"x\"}", null)
                .header("Idempotency-Key", "ajuste-000019"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("userId"));
    mvc.perform(peticion(null, null).header("Idempotency-Key", "ajuste-000020"))
        .andExpect(status().isBadRequest());

    assertThat(saldo(jdbc, persona, "PUNTOS")).isEqualByComparingTo("0");
    assertThat(movimientosDeAjuste()).isZero();
  }

  @Test
  @DisplayName("CA-MV-644 — sin movements:adjust-points, 403; sin sesión, 401")
  void permisos() throws Exception {
    mvc.perform(
            peticion(cuerpo(persona, USD, "5", "x", null), "movements:grant-bonus")
                .header("Idempotency-Key", "ajuste-000021"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(RUTA)
                .header("Idempotency-Key", "ajuste-000022")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(persona, USD, "5", "x", null)))
        .andExpect(status().isUnauthorized());
    assertThat(movimientosDeAjuste()).isZero();
  }

  @Test
  @DisplayName(
      "CA-MV-645 y CA-MV-646 — el ajuste se ve en el historial de saldos de la persona, y queda"
          + " auditado con quién lo hizo, el motivo y la referencia")
  void historialYAuditoria() throws Exception {
    mvc.perform(ajuste(persona, "40", "Consignación", "CONS-1", "ajuste-000023"))
        .andExpect(status().isCreated());

    mvc.perform(
            get("/api/v1/movements/mine/balances/entries")
                .param("account", "PUNTOS")
                .with(user(persona.toString()).authorities(() -> "movements:list-own-entries")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].event").value("AJUSTE"))
        .andExpect(jsonPath("$.content[0].amount").value(40))
        .andExpect(jsonPath("$.content[0].balanceAfter").value(40))
        .andExpect(jsonPath("$.content[0].movement.type").value("AJUSTE_PUNTOS"))
        .andExpect(jsonPath("$.content[0].movement.concept").value("Consignación"));

    String cambios =
        jdbc.queryForObject(
            "SELECT CAST(changes AS text) FROM audit_change_log WHERE entity = 'movements'"
                + " AND action = 'CREATE' AND actor_id = ?",
            String.class,
            administrador);
    assertThat(cambios).contains("AJUSTE", "Consignación", "CONS-1", "points_amount");
  }

  @Test
  @DisplayName(
      "el esquema de V72: puntos con signo y sin tasa entran, cero no; una referencia en blanco no;"
          + " el evento AJUSTE existe")
  void elEsquemaDeV72() {
    assertThat(catchThrowable(() -> ajusteSembrado("-5.00", null))).isNull();
    assertThat(catchThrowable(() -> ajusteSembrado("5.00", "CONS-9"))).isNull();
    assertThat(catchThrowable(() -> ajusteSembrado("0", null)))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(catchThrowable(() -> ajusteSembrado("5.00", "   ")))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint"
                    + " WHERE conname = 'ck_movement_entries_event'",
                String.class))
        .contains("'AJUSTE'")
        .contains("'PAGO'");
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder ajuste(
      UUID quien, String puntos, String motivo, String referencia, String clave) {
    MockHttpServletRequestBuilder p =
        peticion(cuerpo(quien, USD, puntos, motivo, referencia), "movements:adjust-points");
    return clave == null ? p : p.header("Idempotency-Key", clave);
  }

  private MockHttpServletRequestBuilder peticion(String cuerpo, String permiso) {
    MockHttpServletRequestBuilder p =
        post(RUTA)
            .contentType(MediaType.APPLICATION_JSON)
            .with(
                user(administrador.toString())
                    .authorities(() -> permiso == null ? "movements:adjust-points" : permiso));
    return cuerpo == null ? p : p.content(cuerpo);
  }

  private static String cuerpo(
      UUID quien, String moneda, String puntos, String motivo, String referencia) {
    return "{\"userId\":\"%s\",\"currencyId\":\"%s\",\"points\":%s,\"concept\":\"%s\"%s}"
        .formatted(
            quien,
            moneda,
            puntos,
            motivo,
            referencia == null ? "" : ",\"reference\":\"" + referencia + "\"");
  }

  private void ajusteSembrado(String puntos, String referencia) {
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id, currency_id, code,
                               status, total_amount, discount_amount, payable_amount,
                               occurred_at, confirmed_at, points_amount, external_reference)
        SELECT gen_random_uuid(), t.id, s.id, ?, CAST(? AS uuid),
               'AJP-' || substr(md5(random()::text), 1, 12), 'CONFIRMADA', 0, 0, 0, now(), now(),
               CAST(? AS numeric) * 100, ?
          FROM movement_types t
          JOIN movement_type_statuses s ON s.movement_type_id = t.id AND s.code = 'REGISTRADO'
         WHERE t.code = 'AJUSTE_PUNTOS'
        """,
        persona,
        USD,
        puntos,
        referencia);
  }

  private BigDecimal emitidos() {
    return jdbc
        .query(
            "SELECT balance FROM accounts WHERE user_id IS NULL AND kind = 'PUNTOS_EMITIDOS'"
                + " AND currency_id = CAST(? AS uuid)",
            (fila, n) -> MinorUnits.fromMinor(fila.getLong(1)),
            USD)
        .stream()
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }

  private int movimientosDeAjuste() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM movements m JOIN movement_types t ON t.id = m.movement_type_id"
            + " WHERE t.code = 'AJUSTE_PUNTOS'",
        Integer.class);
  }

  private void limpiar() {
    LedgerFixtures.limpiar(jdbc);
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'ajp-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'ajp-%'");
  }

  private UUID persona(String username, String estado) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, 'Nombre', 'Apellido', 'x', false, ?,
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@factech.co",
        estado);
    darElSuelo(jdbc, id);
    return id;
  }
}
