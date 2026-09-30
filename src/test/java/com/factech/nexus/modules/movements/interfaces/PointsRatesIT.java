package com.factech.nexus.modules.movements.interfaces;

import static com.factech.nexus.modules.movements.LedgerFixtures.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.PointsFixtures;
import java.math.BigDecimal;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** `RF-MV-025` — fijar la tasa de puntos; y `RF-MV-026` — consultar las vigentes. */
@AutoConfigureMockMvc
class PointsRatesIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SessionFactory sessionFactory;

  private UUID administrador;
  private UUID cliente;
  private UUID activa;
  private UUID inactiva;

  @BeforeEach
  void sembrar() {
    limpiar();
    administrador = persona("pr-admin");
    cliente = persona("pr-cliente");
    activa = PointsFixtures.moneda(jdbc, "ZZA", true);
    inactiva = PointsFixtures.moneda(jdbc, "ZZI", false);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-025`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-293 y CA-MV-294 — fijar deja la tasa vigente desde ya; otra la sustituye y la"
          + " anterior sigue escrita sin cambios")
  void fijaYSustituye() throws Exception {
    mvc.perform(fijar(USD, "100"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.currency.code").value("USD"))
        .andExpect(jsonPath("$.pointsPerUnit").value(100.0))
        .andExpect(jsonPath("$.validFrom").exists());
    List<BigDecimal> antes = valores(USD);
    assertThat(antes).hasSize(1);

    mvc.perform(fijar(USD, "120.5")).andExpect(status().isCreated());

    // CA-MV-294: dos filas, y la primera intacta.
    assertThat(valores(USD)).hasSize(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT points_per_unit FROM points_rates WHERE currency_id = CAST(? AS uuid)"
                    + " ORDER BY valid_from ASC LIMIT 1",
                BigDecimal.class,
                USD))
        .isEqualByComparingTo("100.0000");
    mvc.perform(get("/api/v1/movements/points-rates").with(lector(cliente)))
        .andExpect(jsonPath("$[?(@.currency.code == 'USD')].pointsPerUnit").value(120.5));
  }

  @Test
  @DisplayName("CA-MV-295 — fijar la que ya rige responde 200 y no escribe ni tasa ni auditoría")
  void laMismaNoEscribe() throws Exception {
    mvc.perform(fijar(USD, "100")).andExpect(status().isCreated());
    int auditoriasAntes = auditorias();

    mvc.perform(fijar(USD, "100.0000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pointsPerUnit").value(100.0));

    assertThat(valores(USD)).hasSize(1);
    assertThat(auditorias()).isEqualTo(auditoriasAntes);
  }

  @Test
  @DisplayName(
      "CA-MV-296 — cero, negativo, cinco decimales o ausente: 400, los errores juntos, y nada"
          + " cambia")
  void valoresMalos() throws Exception {
    for (String malo : new String[] {"0", "-1", "1.23456", "123456789"}) {
      mvc.perform(fijar(USD, malo))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("pointsPerUnit"));
    }
    mvc.perform(
            post("/api/v1/movements/points-rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(fijador(administrador)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(2));
    assertThat(valores(USD)).isEmpty();
  }

  @Test
  @DisplayName("CA-MV-297 — moneda inexistente: 422; moneda inactiva: 409. Nada cambia")
  void monedas() throws Exception {
    mvc.perform(fijar(UUID.randomUUID().toString(), "10"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
    mvc.perform(fijar(inactiva.toString(), "10"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM points_rates", Integer.class)).isZero();
  }

  @Test
  @DisplayName(
      "CA-MV-298 a CA-MV-300 — permiso, auditoría con quien la fijó y la anterior, y las otras"
          + " monedas intactas")
  void permisoAuditoriaYAislamiento() throws Exception {
    mvc.perform(
            post("/api/v1/movements/points-rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(USD, "10"))
                .with(user(administrador.toString()).authorities(() -> "movements:read")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/movements/points-rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(USD, "10")))
        .andExpect(status().isUnauthorized());

    mvc.perform(fijar(activa.toString(), "3")).andExpect(status().isCreated());
    mvc.perform(fijar(USD, "10")).andExpect(status().isCreated());
    mvc.perform(fijar(USD, "11")).andExpect(status().isCreated());

    // CA-MV-299: la segunda de USD recuerda la primera.
    String cambios =
        jdbc.queryForObject(
            "SELECT CAST(changes AS text) FROM audit_change_log WHERE entity = 'points_rates'"
                + " AND action = 'CREATE' AND actor_id = ? ORDER BY occurred_at DESC LIMIT 1",
            String.class,
            administrador);
    assertThat(cambios).contains("10.0000").contains("11.0000");

    // CA-MV-300
    assertThat(valores(activa.toString())).containsExactly(new BigDecimal("3.0000"));
  }

  @Test
  @DisplayName(
      "el esquema de V58: una compra con tasa y sin puntos, una cuenta de puntos emitidos con"
          + " persona y un evento desconocido se rechazan; lo nuevo se admite")
  void losTresCheckDeV58() {
    UUID tasa = PointsFixtures.tasa(jdbc, USD, "1", administrador);

    // ck_movements_points: la tasa y los puntos, juntos o ninguno.
    assertThat(catchThrowable(() -> compraSembrada(tasa, null)))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(catchThrowable(() -> compraSembrada(tasa, "0")))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(catchThrowable(() -> compraSembrada(tasa, "10.00"))).isNull();

    // ck_accounts_kind: PUNTOS_EMITIDOS es de la empresa, y de nadie más.
    assertThat(catchThrowable(() -> cuenta(null, "PE-EMPRESA"))).isNull();
    assertThat(catchThrowable(() -> cuenta(cliente, "PE-PERSONA")))
        .isInstanceOf(DataIntegrityViolationException.class);

    // ck_movement_entries_event: PAGO entra en el dominio, y nada más.
    assertThat(
            jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint"
                    + " WHERE conname = 'ck_movement_entries_event'",
                String.class))
        .contains("'PAGO'")
        .contains("'ABONO'")
        .doesNotContain("'CARGO'");
  }

  private void compraSembrada(UUID tasa, String puntos) {
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id, currency_id, code,
                               status, total_amount, discount_amount, payable_amount,
                               occurred_at, points_rate_id, points_amount)
        SELECT gen_random_uuid(), t.id, s.id, ?, CAST(? AS uuid),
               'PTS-' || substr(md5(random()::text), 1, 12), 'PENDIENTE', 10, 0, 10, now(), ?,
               CAST(? AS numeric)
          FROM movement_types t
          JOIN movement_type_statuses s ON s.movement_type_id = t.id AND s.code = 'REGISTRADO'
         WHERE t.code = 'COMPRA_PUNTOS'
        """,
        cliente,
        USD,
        tasa,
        puntos);
  }

  private void cuenta(UUID titular, String numero) {
    jdbc.update(
        "INSERT INTO accounts (id, user_id, kind, name, number, currency_id)"
            + " VALUES (gen_random_uuid(), ?, 'PUNTOS_EMITIDOS', 'Puntos emitidos', ?,"
            + " CAST(? AS uuid))",
        titular,
        numero,
        USD);
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-026`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-301 a CA-MV-304 — una fila por moneda activa con tasa, solo la última, por código;"
          + " vacía sin tasas")
  void vigentes() throws Exception {
    mvc.perform(get("/api/v1/movements/points-rates").with(lector(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    PointsFixtures.tasa(jdbc, USD, "90", administrador);
    PointsFixtures.tasa(jdbc, USD, "100", administrador);
    PointsFixtures.tasa(jdbc, activa.toString(), "5", administrador);
    // Inactiva con tasa, y una moneda más sin ninguna: ninguna de las dos sale.
    PointsFixtures.tasa(jdbc, inactiva.toString(), "7", administrador);
    PointsFixtures.moneda(jdbc, "ZZN", true);

    Statistics estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
    mvc.perform(get("/api/v1/movements/points-rates").with(lector(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].currency.code").value("USD"))
        .andExpect(jsonPath("$[0].pointsPerUnit").value(100.0))
        .andExpect(jsonPath("$[1].currency.code").value("ZZA"))
        .andExpect(jsonPath("$[1].pointsPerUnit").value(5.0));
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("CA-MV-305 — la consulta exige su permiso, y sin token es 401")
  void permisoDeConsulta() throws Exception {
    mvc.perform(
            get("/api/v1/movements/points-rates")
                .with(user(cliente.toString()).authorities(() -> "movements:set-points-rate")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/movements/points-rates")).andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder fijar(String moneda, String valor) {
    return post("/api/v1/movements/points-rates")
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo(moneda, valor))
        .with(fijador(administrador));
  }

  private static String cuerpo(String moneda, String valor) {
    return "{\"currencyId\":\"%s\",\"pointsPerUnit\":%s}".formatted(moneda, valor);
  }

  private static RequestPostProcessor fijador(UUID quien) {
    return user(quien.toString()).authorities(() -> "movements:set-points-rate");
  }

  private static RequestPostProcessor lector(UUID quien) {
    return user(quien.toString()).authorities(() -> "movements:read-points-rates");
  }

  private List<BigDecimal> valores(String moneda) {
    return jdbc.queryForList(
        "SELECT points_per_unit FROM points_rates WHERE currency_id = CAST(? AS uuid)"
            + " ORDER BY valid_from",
        BigDecimal.class,
        moneda);
  }

  private int auditorias() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_change_log WHERE entity = 'points_rates'", Integer.class);
  }

  private void limpiar() {
    PointsFixtures.limpiar(jdbc);
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'pr-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'pr-%'");
  }

  private UUID persona(String username) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, 'Nombre', 'Apellido', 'x', false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@factech.co");
    darElSuelo(jdbc, id);
    return id;
  }
}
