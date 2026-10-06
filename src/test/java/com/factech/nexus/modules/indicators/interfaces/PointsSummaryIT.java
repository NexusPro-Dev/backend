package com.factech.nexus.modules.indicators.interfaces;

import static com.factech.nexus.modules.movements.PointsFixtures.POINTS;
import static com.factech.nexus.modules.movements.PointsFixtures.TARJETA;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.modules.movements.PointsFixtures;
import com.factech.nexus.testing.CommissionCleanup;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * `RF-IN-005` · `T-05` — el resumen de puntos (`CA-IN-041` a `CA-IN-049`).
 *
 * <pre>
 *   director1 ─── agente1        director2 (otra rama)        funcionario (ADMIN)
 *   pts-vendedor: dueño del enlace por el que agente1 paga con puntos
 * </pre>
 *
 * <p><b>Los movimientos se hacen por las rutas de `MV`</b> —comprar, confirmar, rechazar, pagar con
 * puntos, ajustar—, de modo que los asientos los escribe `MV` y no la prueba: es lo que hace que
 * {@code CA-IN-044} pruebe algo. A 100 puntos el dólar y 0,1 el peso.
 *
 * <p>Lo de agente1 en dólares: compra 2000 cobrada, 500 pendiente y 300 rechazada; paga un bot de
 * 10 USD con 1000; ajuste +50 y −30. Saldo 1020. Lo de director1 en pesos: compra 100. Lo de
 * director2: compra 100 en dólares.
 */
@AutoConfigureMockMvc
class PointsSummaryIT extends IntegrationTestBase {
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";
  private static final String COP = "01a03336-6d00-7002-9c4f-5e7ad3000002";
  private static final String ADMIN = "01a02a33-4c00-7002-9c4f-5e7ad1000002";
  private static final String DIRECTOR = "01a02a33-4c00-7006-9c4f-5e7ad1000004";
  private static final String AGENTE = "01a02a33-4c00-7007-9c4f-5e7ad1000005";

  private static final String RUTA = "/api/v1/indicators/points/summary";
  private static final String PERMISO = "indicators:read-points-summary";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID funcionario;
  private UUID director1;
  private UUID director2;
  private UUID agente1;
  private UUID vendedor;

  @BeforeEach
  void sembrar() throws Exception {
    limpiar();
    funcionario = persona("pts-funcionario", ADMIN);
    director1 = persona("pts-director1", DIRECTOR);
    director2 = persona("pts-director2", DIRECTOR);
    agente1 = persona("pts-agente1", AGENTE);
    vendedor = persona("pts-vendedor", null);
    UUID rolVendedor = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO roles (id, code, name, role_type, parent_role_id) VALUES (?, 'PTS_VENDEDOR',"
            + " 'PTS_VENDEDOR', 'VENDEDOR', (SELECT r.id FROM roles r WHERE r.code = 'MANAGER'))",
        rolVendedor);
    jdbc.update(
        "INSERT INTO user_roles (user_id, role_id, role_type) VALUES (?, ?, 'VENDEDOR')",
        vendedor,
        rolVendedor);
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at)"
            + " VALUES (gen_random_uuid(), ?, ?, now())",
        agente1,
        director1);
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin, first_movement_id, created_at)"
            + " VALUES (?, ?, 'REGISTRO', NULL, now())",
        agente1,
        vendedor);
    jdbc.update(
        """
        INSERT INTO products (id, code, type, name, description, price, currency_id,
                              validity_days, status, scope, implementation, created_at, updated_at)
        VALUES (gen_random_uuid(), 'PTS_BOT', 'BOT', 'PTS_BOT', 'Sembrado por PointsSummaryIT',
                1000, CAST(? AS uuid), 30, 'ACTIVO', 'AMBOS', 'AUTOMATICA', now(), now())
        """,
        USD);
    PointsFixtures.tasa(jdbc, USD, "100", funcionario);
    PointsFixtures.tasa(jdbc, COP, "0.1", funcionario);

    confirmar(comprar(agente1, USD, "20.00"));
    comprar(agente1, USD, "5.00");
    rechazar(comprar(agente1, USD, "3.00"));
    pagarConPuntos(agente1);
    ajustar(agente1, "50");
    ajustar(agente1, "-30");
    confirmar(comprar(director1, COP, "1000.00"));
    confirmar(comprar(director2, USD, "1.00"));
  }

  @AfterEach
  void devolverLaBaseASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName(
      "CA-IN-041 a CA-IN-045 — comprados solo los cobrados, redimidos en positivo, ajustes aparte,"
          + " el saldo cuadra y cada moneda por su lado")
  void lasCifras() throws Exception {
    resumen(director1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currencies[*].currency.code", contains("COP", "USD")))
        .andExpect(jsonPath("$.currencies[0].purchased.points").value(100.0))
        .andExpect(jsonPath("$.currencies[0].balance").value(100.0))
        .andExpect(jsonPath("$.currencies[1].purchased.points").value(2000.0))
        .andExpect(jsonPath("$.currencies[1].purchased.count").value(1))
        .andExpect(jsonPath("$.currencies[1].redeemed.points").value(1000.0))
        .andExpect(jsonPath("$.currencies[1].redeemed.count").value(1))
        .andExpect(jsonPath("$.currencies[1].added.points").value(50.0))
        .andExpect(jsonPath("$.currencies[1].removed.points").value(30.0))
        .andExpect(jsonPath("$.currencies[1].removed.count").value(1))
        // CA-IN-044: el periodo lo cubre todo, y 2000 − 1000 + 50 − 30 = 1020.
        .andExpect(jsonPath("$.currencies[1].balance").value(1020.0));
  }

  @Test
  @DisplayName(
      "CA-IN-046 — el alcance: el funcionario ve todo; un director su rama; un agente lo suyo")
  void alcance() throws Exception {
    resumen(funcionario)
        .andExpect(jsonPath("$.currencies[1].currency.code").value("USD"))
        .andExpect(jsonPath("$.currencies[1].purchased.points").value(2100.0))
        .andExpect(jsonPath("$.currencies[1].purchased.count").value(2))
        .andExpect(jsonPath("$.currencies[1].balance").value(1120.0));
    resumen(director2)
        .andExpect(jsonPath("$.currencies[*].currency.code", contains("USD")))
        .andExpect(jsonPath("$.currencies[0].purchased.points").value(100.0))
        .andExpect(jsonPath("$.currencies[0].redeemed.points").value(0.0));
    resumen(agente1)
        .andExpect(jsonPath("$.currencies[*].currency.code", contains("USD")))
        .andExpect(jsonPath("$.currencies[0].balance").value(1020.0));
  }

  @Test
  @DisplayName(
      "CA-IN-047 — la persona acota dentro del alcance; fuera, lista vacía y no error; la moneda"
          + " acota")
  void filtros() throws Exception {
    mvc.perform(get(RUTA).param("userId", agente1.toString()).with(conPermiso(director1)))
        .andExpect(jsonPath("$.currencies[*].currency.code", contains("USD")));
    mvc.perform(get(RUTA).param("userId", director2.toString()).with(conPermiso(director1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currencies").isEmpty());
    mvc.perform(get(RUTA).param("userId", UUID.randomUUID().toString()).with(conPermiso(director1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currencies").isEmpty());
    mvc.perform(get(RUTA).param("currencyId", COP).with(conPermiso(director1)))
        .andExpect(jsonPath("$.currencies[*].currency.code", contains("COP")));
  }

  @Test
  @DisplayName(
      "CA-IN-044 y CA-IN-048 — el periodo mira cuándo se movieron los puntos; el saldo es el de"
          + " hoy, sea cual sea el periodo")
  void periodo() throws Exception {
    // La compra de director2 se pidió el 30 de agosto y se cobró el 1 de
    // septiembre (en Bogotá): cuenta el 1.
    jdbc.update(
        "UPDATE movements SET occurred_at = '2026-08-30T15:00:00Z' WHERE user_id = ?"
            + " AND movement_type_id = (SELECT id FROM movement_types WHERE code = 'COMPRA_PUNTOS')",
        director2);
    jdbc.update(
        "UPDATE movement_entries SET created_at = '2026-09-01T15:00:00Z' WHERE account_id IN"
            + " (SELECT id FROM accounts WHERE user_id = ?)",
        director2);

    rango(director2, "2026-09-01", "2026-09-01")
        .andExpect(jsonPath("$.currencies[0].purchased.points").value(100.0))
        .andExpect(jsonPath("$.currencies[0].balance").value(100.0));
    // El 30 de agosto no hubo movimiento de puntos; el saldo sigue siendo el de hoy.
    rango(director2, "2026-08-30", "2026-08-30")
        .andExpect(jsonPath("$.currencies[0].purchased.points").value(0.0))
        .andExpect(jsonPath("$.currencies[0].balance").value(100.0));
  }

  @Test
  @DisplayName(
      "CA-IN-049 — sin el permiso, 403; ni los de ventas ni el de saldos de una persona lo abren;"
          + " sin token, 401")
  void permisos() throws Exception {
    mvc.perform(get(RUTA).with(user(director1.toString()))).andExpect(status().isForbidden());
    for (String otro :
        new String[] {"indicators:read-sales-summary", "movements:read-user-balances"}) {
      mvc.perform(get(RUTA).with(user(director1.toString()).authorities(() -> otro)))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------

  private ResultActions resumen(UUID actor) throws Exception {
    return mvc.perform(get(RUTA).with(conPermiso(actor)));
  }

  private ResultActions rango(UUID actor, String desde, String hasta) throws Exception {
    return mvc.perform(get(RUTA).param("from", desde).param("to", hasta).with(conPermiso(actor)));
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor conPermiso(
      UUID persona) {
    return user(persona.toString()).authorities(() -> PERMISO);
  }

  private UUID comprar(UUID quien, String moneda, String importe) throws Exception {
    String cuerpo =
        mvc.perform(
                post("/api/v1/movements/mine/points-purchases")
                    .header("Idempotency-Key", "pts-" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"currencyId\":\"%s\",\"amount\":%s,\"paymentMethodId\":\"%s\"}"
                            .formatted(moneda, importe, TARJETA))
                    .with(user(quien.toString()).authorities(() -> "movements:buy-points")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(cuerpo, "$.id"));
  }

  private void confirmar(UUID compra) throws Exception {
    mvc.perform(
            post(
                    "/api/v1/movements/payments/{id}/confirmation",
                    PaymentFixtures.pagoAConciliar(jdbc, compra))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user(funcionario.toString()).authorities(() -> "movements:confirm-payment")))
        .andExpect(status().isOk());
  }

  private void rechazar(UUID compra) throws Exception {
    mvc.perform(
            post(
                    "/api/v1/movements/payments/{id}/rejection",
                    PaymentFixtures.pagoAConciliar(jdbc, compra))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"No entró\"}")
                .with(user(funcionario.toString()).authorities(() -> "movements:reject-payment")))
        .andExpect(status().isOk());
  }

  private void pagarConPuntos(UUID quien) throws Exception {
    mvc.perform(
            post("/api/v1/hotlinks/{u}/{c}/purchases", "pts-vendedor", "PTS_BOT")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethodId\":\"" + POINTS + "\"}")
                .with(user(quien.toString()).authorities(() -> "products:buy-by-hotlink")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMADA"));
  }

  private void ajustar(UUID quien, String puntos) throws Exception {
    mvc.perform(
            post("/api/v1/movements/points-adjustments")
                .header("Idempotency-Key", "pts-ajuste-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"userId\":\"%s\",\"currencyId\":\"%s\",\"points\":%s,\"concept\":\"Prueba\"}"
                        .formatted(quien, USD, puntos))
                .with(user(funcionario.toString()).authorities(() -> "movements:adjust-points")))
        .andExpect(status().isCreated());
  }

  private void limpiar() {
    CommissionCleanup.limpiar(jdbc);
    jdbc.update(
        "DELETE FROM client_sellers WHERE client_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'pts-%')"
            + " OR seller_id IN (SELECT id FROM users WHERE username LIKE 'pts-%')");
    PointsFixtures.limpiar(jdbc);
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'pts-%')");
    jdbc.update("DELETE FROM products WHERE code LIKE 'PTS\\_%'");
    jdbc.update(
        "DELETE FROM user_supervisors WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'pts-%') OR supervisor_id IN (SELECT id FROM users WHERE username LIKE 'pts-%')");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'pts-%')");
    jdbc.update("DELETE FROM roles WHERE code = 'PTS_VENDEDOR'");
    jdbc.update("DELETE FROM users WHERE username LIKE 'pts-%'");
  }

  private UUID persona(String username, String rol) {
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
    if (rol != null) {
      jdbc.update(
          "INSERT INTO user_roles (user_id, role_id, role_type)"
              + " SELECT ?, r.id, r.role_type FROM roles r WHERE r.id = ?::uuid",
          id,
          rol);
    }
    darElSuelo(jdbc, id);
    return id;
  }
}
