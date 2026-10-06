package com.factech.nexus.modules.indicators.interfaces;

import static com.factech.nexus.modules.movements.PointsFixtures.POINTS;
import static com.factech.nexus.modules.movements.PointsFixtures.TARJETA;
import static org.assertj.core.api.Assertions.assertThat;
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
      "CA-IN-043, CA-IN-044, CA-IN-045 y CA-IN-068 — compras por estado con lo pagado, gastos y"
          + " ajustes aparte, el saldo cuadra y cada moneda por su lado")
  void lasCifras() throws Exception {
    resumen(director1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currencies[*].currency.code", contains("COP", "USD")))
        .andExpect(jsonPath("$.currencies[0].purchases.confirmed.points").value(100.0))
        .andExpect(jsonPath("$.currencies[0].purchases.confirmed.amount").value(1000.0))
        .andExpect(jsonPath("$.currencies[0].balance").value(100.0))
        .andExpect(jsonPath("$.currencies[1].purchases.confirmed.count").value(1))
        .andExpect(jsonPath("$.currencies[1].purchases.confirmed.points").value(2000.0))
        .andExpect(jsonPath("$.currencies[1].purchases.confirmed.amount").value(20.0))
        .andExpect(jsonPath("$.currencies[1].purchases.pending.count").value(1))
        .andExpect(jsonPath("$.currencies[1].purchases.pending.points").value(500.0))
        .andExpect(jsonPath("$.currencies[1].purchases.pending.amount").value(5.0))
        .andExpect(jsonPath("$.currencies[1].purchases.rejected.points").value(300.0))
        .andExpect(jsonPath("$.currencies[1].purchases.rejected.amount").value(3.0))
        .andExpect(jsonPath("$.currencies[1].spent.count").value(1))
        .andExpect(jsonPath("$.currencies[1].spent.points").value(1000.0))
        .andExpect(jsonPath("$.currencies[1].adjustments.added.points").value(50.0))
        .andExpect(jsonPath("$.currencies[1].adjustments.removed.count").value(1))
        .andExpect(jsonPath("$.currencies[1].adjustments.removed.points").value(30.0))
        // CA-IN-071: sin filtros, 2000 − 1000 + 50 − 30 = 1020; lo pendiente y lo
        // rechazado no mueven el saldo.
        .andExpect(jsonPath("$.currencies[1].balance").value(1020.0));
  }

  @Test
  @DisplayName(
      "CA-IN-067 — con los mismos filtros, las cifras son la suma de las filas de la lista de"
          + " administración de los movimientos de puntos")
  void cuadraConLaLista() throws Exception {
    String lista =
        mvc.perform(
                get("/api/v1/movements/points-movements")
                    .param("size", "100")
                    .with(
                        user(funcionario.toString())
                            .authorities(() -> "movements:list-points-movements")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String indicador =
        mvc.perform(get(RUTA).with(conPermiso(funcionario)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Lo que dice la lista, fila a fila: por moneda, tipo, estado y signo.
    java.util.Map<String, java.math.BigDecimal[]> deLaLista = new java.util.HashMap<>();
    java.util.List<java.util.Map<String, Object>> filas = JsonPath.read(lista, "$.content");
    for (java.util.Map<String, Object> f : filas) {
      java.math.BigDecimal puntos = new java.math.BigDecimal(f.get("points").toString());
      String tipo = (String) f.get("type");
      String clave =
          JsonPath.read(f, "$.currency.code")
              + "|"
              + (tipo.equals("AJUSTE_PUNTOS") ? tipo + (puntos.signum() > 0 ? "+" : "-") : tipo)
              + "|"
              + (tipo.equals("COMPRA_PUNTOS") ? f.get("status") : "");
      java.math.BigDecimal[] suma =
          deLaLista.computeIfAbsent(
              clave,
              k ->
                  new java.math.BigDecimal[] {
                    java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO
                  });
      suma[0] = suma[0].add(java.math.BigDecimal.ONE);
      suma[1] = suma[1].add(puntos.abs());
      if (tipo.equals("COMPRA_PUNTOS")) {
        suma[2] = suma[2].add(new java.math.BigDecimal(f.get("amount").toString()));
      }
    }

    // Y lo que dice el indicador, por las mismas claves.
    java.util.Map<String, String> rutas = new java.util.LinkedHashMap<>();
    rutas.put("COMPRA_PUNTOS|CONFIRMADA", "purchases.confirmed");
    rutas.put("COMPRA_PUNTOS|PENDIENTE", "purchases.pending");
    rutas.put("COMPRA_PUNTOS|RECHAZADA", "purchases.rejected");
    rutas.put("GASTO_PUNTOS|", "spent");
    rutas.put("AJUSTE_PUNTOS+|", "adjustments.added");
    rutas.put("AJUSTE_PUNTOS-|", "adjustments.removed");
    java.util.List<String> monedas = JsonPath.read(indicador, "$.currencies[*].currency.code");
    int comparadas = 0;
    for (int i = 0; i < monedas.size(); i++) {
      for (var r : rutas.entrySet()) {
        java.math.BigDecimal[] esperado =
            deLaLista.getOrDefault(
                monedas.get(i) + "|" + r.getKey(),
                new java.math.BigDecimal[] {
                  java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO
                });
        String base = "$.currencies[" + i + "]." + r.getValue();
        assertThat(((Number) JsonPath.read(indicador, base + ".count")).longValue())
            .as(monedas.get(i) + " " + r.getKey() + " cuántas")
            .isEqualTo(esperado[0].longValue());
        assertThat(new java.math.BigDecimal(JsonPath.read(indicador, base + ".points").toString()))
            .as(monedas.get(i) + " " + r.getKey() + " puntos")
            .isEqualByComparingTo(esperado[1]);
        if (r.getKey().startsWith("COMPRA_PUNTOS")) {
          assertThat(
                  new java.math.BigDecimal(JsonPath.read(indicador, base + ".amount").toString()))
              .as(monedas.get(i) + " " + r.getKey() + " pagado")
              .isEqualByComparingTo(esperado[2]);
        }
        comparadas++;
      }
    }
    assertThat(comparadas).isEqualTo(12);
    assertThat(filas).hasSize(8);
  }

  @Test
  @DisplayName(
      "CA-IN-046 — el alcance: el funcionario ve todo; un director su rama; un agente lo suyo")
  void alcance() throws Exception {
    resumen(funcionario)
        .andExpect(jsonPath("$.currencies[1].currency.code").value("USD"))
        .andExpect(jsonPath("$.currencies[1].purchases.confirmed.points").value(2100.0))
        .andExpect(jsonPath("$.currencies[1].purchases.confirmed.count").value(2))
        .andExpect(jsonPath("$.currencies[1].balance").value(1120.0));
    resumen(director2)
        .andExpect(jsonPath("$.currencies[*].currency.code", contains("USD")))
        .andExpect(jsonPath("$.currencies[0].purchases.confirmed.points").value(100.0))
        .andExpect(jsonPath("$.currencies[0].spent.points").value(0.0));
    resumen(agente1)
        .andExpect(jsonPath("$.currencies[*].currency.code", contains("USD")))
        .andExpect(jsonPath("$.currencies[0].balance").value(1020.0));
  }

  @Test
  @DisplayName(
      "CA-IN-047, CA-IN-070 y CA-IN-071 — persona, moneda, tipo y estado acotan; el saldo no se"
          + " mueve con el tipo ni el estado; los desconocidos, juntos")
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

    mvc.perform(get(RUTA).param("type", "gasto_puntos").with(conPermiso(agente1)))
        .andExpect(jsonPath("$.currencies[0].spent.count").value(1))
        .andExpect(jsonPath("$.currencies[0].purchases.confirmed.count").value(0))
        .andExpect(jsonPath("$.currencies[0].adjustments.added.count").value(0))
        .andExpect(jsonPath("$.currencies[0].balance").value(1020.0));
    mvc.perform(get(RUTA).param("status", "PENDIENTE").with(conPermiso(agente1)))
        .andExpect(jsonPath("$.currencies[0].purchases.pending.count").value(1))
        .andExpect(jsonPath("$.currencies[0].purchases.confirmed.count").value(0))
        .andExpect(jsonPath("$.currencies[0].spent.count").value(0))
        .andExpect(jsonPath("$.currencies[0].balance").value(1020.0));

    mvc.perform(
            get(RUTA).param("type", "BONO").param("status", "ANULADA").with(conPermiso(agente1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].code", contains("VAL-006", "VAL-007")));
  }

  @Test
  @DisplayName(
      "CA-IN-069 y CA-IN-072 — la fecha es la de la fila de la lista: cuándo ocurrió la compra, y"
          + " cuándo se descontaron los puntos de un gasto")
  void fechaDeLaFila() throws Exception {
    // La compra de director2 se pidió el 30 de agosto y se cobró el 1 de
    // septiembre (en Bogotá): cuenta el 30.
    jdbc.update(
        "UPDATE movements SET occurred_at = '2026-08-30T15:00:00Z' WHERE user_id = ?"
            + " AND movement_type_id = (SELECT id FROM movement_types WHERE code = 'COMPRA_PUNTOS')",
        director2);
    jdbc.update(
        "UPDATE movement_entries SET created_at = '2026-09-01T15:00:00Z' WHERE account_id IN"
            + " (SELECT id FROM accounts WHERE user_id = ?)",
        director2);
    rango(director2, "2026-08-30", "2026-08-30")
        .andExpect(jsonPath("$.currencies[0].purchases.confirmed.points").value(100.0))
        .andExpect(jsonPath("$.currencies[0].balance").value(100.0));
    rango(director2, "2026-09-01", "2026-09-01")
        .andExpect(jsonPath("$.currencies[0].purchases.confirmed.points").value(0.0))
        .andExpect(jsonPath("$.currencies[0].balance").value(100.0));

    // El gasto de agente1: la venta ocurrió el 1 y los puntos se descontaron el
    // 2 —como al volver a pagar—: cuenta el 2.
    jdbc.update(
        "UPDATE movements SET occurred_at = '2026-09-01T15:00:00Z' WHERE user_id = ?"
            + " AND movement_type_id = (SELECT id FROM movement_types WHERE code = 'VENTA')",
        agente1);
    jdbc.update(
        "UPDATE movement_entries SET created_at = '2026-09-02T15:00:00Z' WHERE event = 'PAGO'"
            + " AND movement_id IN (SELECT id FROM movements WHERE user_id = ?)",
        agente1);
    rango(agente1, "2026-09-02", "2026-09-02")
        .andExpect(jsonPath("$.currencies[0].spent.count").value(1))
        .andExpect(jsonPath("$.currencies[0].spent.points").value(1000.0));
    rango(agente1, "2026-09-01", "2026-09-01")
        .andExpect(jsonPath("$.currencies[0].spent.count").value(0));
  }

  @Test
  @DisplayName(
      "CA-IN-057 y CA-IN-058 — sin fechas, el saldo es la suma de las clases; con tramo, las"
          + " cifras por tramo y sin saldo")
  void totalidadYTramos() throws Exception {
    resumen(director1)
        .andExpect(jsonPath("$.period.from").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.currencies[1].balance").value(1020.0));

    // Por meses desde septiembre: todo lo de hoy cae en el mes en curso.
    String mesEnCurso =
        java.time.LocalDate.now(java.time.ZoneId.of("America/Bogota")).withDayOfMonth(1).toString();
    mvc.perform(
            get(RUTA)
                .param("from", "2026-09-01")
                .param("granularity", "MONTH")
                .with(conPermiso(director1)))
        .andExpect(jsonPath("$.granularity").value("MONTH"))
        .andExpect(jsonPath("$.buckets[0].start").value("2026-09-01"))
        .andExpect(jsonPath("$.buckets[0].currencies[*].currency.code", contains("COP", "USD")))
        .andExpect(jsonPath("$.buckets[0].currencies[1].purchases.confirmed.points").value(0.0))
        .andExpect(jsonPath("$.buckets[-1].start").value(mesEnCurso))
        .andExpect(jsonPath("$.buckets[-1].currencies[1].purchases.confirmed.points").value(2000.0))
        .andExpect(jsonPath("$.buckets[-1].currencies[1].purchases.pending.points").value(500.0))
        .andExpect(jsonPath("$.buckets[-1].currencies[1].spent.points").value(1000.0))
        .andExpect(jsonPath("$.buckets[-1].currencies[1].adjustments.removed.points").value(30.0))
        .andExpect(jsonPath("$.buckets[-1].currencies[1].balance").doesNotExist());
  }

  @Test
  @DisplayName(
      "CA-IN-049 — sin el permiso, 403; ni los de ventas ni los de la lista de puntos lo abren;"
          + " sin token, 401")
  void permisos() throws Exception {
    mvc.perform(get(RUTA).with(user(director1.toString()))).andExpect(status().isForbidden());
    for (String otro :
        new String[] {
          "indicators:read-sales-summary",
          "movements:read-user-balances",
          "movements:list-points-movements"
        }) {
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
