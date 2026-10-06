package com.factech.nexus.modules.movements.interfaces;

import static com.factech.nexus.modules.movements.LedgerFixtures.USD;
import static com.factech.nexus.modules.movements.LedgerFixtures.saldo;
import static com.factech.nexus.modules.movements.PointsFixtures.GRATIS;
import static com.factech.nexus.modules.movements.PointsFixtures.POINTS;
import static com.factech.nexus.modules.movements.PointsFixtures.PSE;
import static com.factech.nexus.modules.movements.PointsFixtures.TARJETA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.modules.movements.PointsFixtures;
import com.factech.nexus.modules.movements.domain.service.CreditService;
import com.factech.nexus.shared.persistence.MinorUnits;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.hibernate.SessionFactory;
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
 * `RF-MV-027` comprar puntos, `RF-MV-028` confirmar y `RF-MV-029` rechazar. Mis compras de puntos
 * (`RF-MV-031`) se retiraron el 06-10-2026: sus criterios viven en `OwnPointsMovementsIT`.
 */
@AutoConfigureMockMvc
class PointsPurchaseIT extends IntegrationTestBase {

  private static final String BASE = "/api/v1/movements";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CreditService abonos;
  @Autowired private SessionFactory sessionFactory;

  private UUID comprador;
  private UUID otro;
  private UUID administrador;

  @BeforeEach
  void sembrar() {
    limpiar();
    comprador = persona("pp-comprador", "ACTIVO");
    otro = persona("pp-otro", "ACTIVO");
    administrador = persona("pp-admin", "ACTIVO");
    PointsFixtures.tasa(jdbc, USD, "100", administrador);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-027` — comprar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-306, CA-MV-308 y CA-MV-309 — nace pendiente con la tasa, los puntos y un pago"
          + " pendiente; sin asientos, sin líneas y sin comisión")
  void compraPendiente() throws Exception {
    String cuerpo =
        mvc.perform(comprar(comprador, "10.00", TARJETA, "compra-000001"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDIENTE"))
            .andExpect(jsonPath("$.code").value(startsWith("PTS-")))
            .andExpect(jsonPath("$.currency.code").value("USD"))
            .andExpect(jsonPath("$.amount").value(10.00))
            .andExpect(jsonPath("$.pointsRate.pointsPerUnit").value(100.0))
            .andExpect(jsonPath("$.points").value(1000.00))
            .andExpect(jsonPath("$.confirmedAt").doesNotExist())
            .andExpect(jsonPath("$.payments.length()").value(1))
            .andExpect(jsonPath("$.payments[0].status").value("PENDIENTE"))
            .andExpect(jsonPath("$.payments[0].paymentMethod.code").value("CREDIT_CARD"))
            .andExpect(jsonPath("$.payments[0].amount").value(10.00))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID compra = UUID.fromString(JsonPath.read(cuerpo, "$.id"));

    // CA-MV-308: ningún saldo, ningún asiento.
    assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movement_entries", Integer.class))
        .isZero();
    // CA-MV-309: sin líneas, y comisiones no se entera.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM movement_details WHERE movement_id = ?",
                Integer.class,
                compra))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM commission_accruals", Integer.class))
        .isZero();
  }

  @Test
  @DisplayName("CA-MV-307 — los puntos se redondean hacia abajo: 10.00 a 0.3336 dan 3.33")
  void redondeoHaciaAbajo() throws Exception {
    PointsFixtures.tasa(jdbc, USD, "0.3336", administrador);
    mvc.perform(comprar(comprador, "10.00", TARJETA, "compra-000002"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.points").value(3.33));
  }

  @Test
  @DisplayName(
      "CA-MV-310 y CA-MV-311 — la misma petición devuelve la misma compra, también a la vez; la"
          + " misma clave con otros datos es 409")
  void idempotente() throws Exception {
    String primera =
        mvc.perform(comprar(comprador, "5.00", TARJETA, "compra-000003"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    mvc.perform(comprar(comprador, "5.00", TARJETA, "compra-000003"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value((String) JsonPath.read(primera, "$.id")));
    mvc.perform(comprar(comprador, "6.00", TARJETA, "compra-000003"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-008"));
    mvc.perform(comprar(comprador, "5.00", PSE, "compra-000003")).andExpect(status().isConflict());
    // Otra persona con la misma clave tampoco la reutiliza.
    mvc.perform(comprar(otro, "5.00", TARJETA, "compra-000003")).andExpect(status().isConflict());
    assertThat(compras()).isEqualTo(1);

    CountDownLatch salida = new CountDownLatch(1);
    ExecutorService hilos = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> r = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        r.add(
            hilos.submit(
                () -> {
                  salida.await();
                  return mvc.perform(comprar(otro, "5.00", TARJETA, "compra-000004"))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      }
      salida.countDown();
      for (Future<Integer> f : r) {
        f.get();
      }
    } finally {
      hilos.shutdownNow();
    }
    assertThat(compras()).isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "CA-MV-312 — POINTS, un método desactivado o uno interno: 409; uno inexistente: 422. Nada"
          + " se crea")
  void metodos() throws Exception {
    mvc.perform(comprar(comprador, "5.00", POINTS, "compra-000005"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-005"));
    mvc.perform(comprar(comprador, "5.00", GRATIS, "compra-000006"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-005"));
    String manual =
        jdbc.queryForObject(
            "SELECT CAST(id AS text) FROM payment_methods WHERE code = 'MANUAL'", String.class);
    mvc.perform(comprar(comprador, "5.00", manual, "compra-000007"))
        .andExpect(status().isConflict());
    mvc.perform(comprar(comprador, "5.00", UUID.randomUUID().toString(), "compra-000008"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));

    jdbc.update("UPDATE payment_methods SET is_active = false WHERE id = CAST(? AS uuid)", PSE);
    try {
      mvc.perform(comprar(comprador, "5.00", PSE, "compra-000009"))
          .andExpect(status().isConflict());
    } finally {
      jdbc.update("UPDATE payment_methods SET is_active = true WHERE id = CAST(? AS uuid)", PSE);
    }
    assertThat(compras()).isZero();
  }

  @Test
  @DisplayName("CA-MV-313 — moneda inexistente: 422; inactiva o sin tasa: 409. Nada se crea")
  void monedas() throws Exception {
    UUID inactiva = PointsFixtures.moneda(jdbc, "ZZI", false);
    UUID sinTasa = PointsFixtures.moneda(jdbc, "ZZS", true);
    PointsFixtures.tasa(jdbc, inactiva.toString(), "10", administrador);

    mvc.perform(comprarEn(comprador, UUID.randomUUID().toString(), "compra-000010"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(comprarEn(comprador, inactiva.toString(), "compra-000011"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
    mvc.perform(comprarEn(comprador, sinTasa.toString(), "compra-000012"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
    assertThat(compras()).isZero();
  }

  @Test
  @DisplayName(
      "CA-MV-314 y CA-MV-315 — importe cero, con decimales de más, que no da puntos o sin clave:"
          + " 400; una cuenta en espera de su depósito: 409")
  void bordesDeLaCompra() throws Exception {
    mvc.perform(comprar(comprador, "0", TARJETA, "compra-000013"))
        .andExpect(status().isBadRequest());
    mvc.perform(comprar(comprador, "1.001", TARJETA, "compra-000014"))
        .andExpect(status().isBadRequest());
    mvc.perform(comprar(comprador, "5.00", TARJETA, null)).andExpect(status().isBadRequest());
    PointsFixtures.tasa(jdbc, USD, "0.0001", administrador);
    mvc.perform(comprar(comprador, "0.01", TARJETA, "compra-000015"))
        .andExpect(status().isBadRequest());

    UUID enEspera = persona("pp-espera", "FTD_PENDIENTE");
    mvc.perform(comprar(enEspera, "5.00", TARJETA, "compra-000016"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));
    assertThat(compras()).isZero();
  }

  @Test
  @DisplayName(
      "CA-MV-316 y CA-MV-317 — el permiso; la auditoría; y la compra conserva su tasa cuando la"
          + " tasa cambia")
  void permisoYTasaCongelada() throws Exception {
    mvc.perform(
            post(BASE + "/mine/points-purchases")
                .header("Idempotency-Key", "compra-000017")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoDeCompra(USD, "5.00", TARJETA))
                .with(user(comprador.toString()).authorities(() -> "movements:read")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(BASE + "/mine/points-purchases")
                .header("Idempotency-Key", "compra-000018")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoDeCompra(USD, "5.00", TARJETA)))
        .andExpect(status().isUnauthorized());

    UUID compra = comprarYLeer(comprador, "5.00", "compra-000019");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE entity = 'movements'"
                    + " AND action = 'CREATE' AND entity_id = ? AND actor_id = ?",
                Integer.class,
                compra,
                comprador))
        .isEqualTo(1);

    PointsFixtures.tasa(jdbc, USD, "200", administrador);
    // La tasa queda congelada: se ve en el detalle (`RF-MV-055`).
    mvc.perform(
            get(BASE + "/mine/points-movements/{id}", compra)
                .with(
                    user(comprador.toString())
                        .authorities(() -> "movements:read-own-points-movement")))
        .andExpect(jsonPath("$.pointsRate.pointsPerUnit").value(100.0))
        .andExpect(jsonPath("$.movement.points").value(500.00));
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-028` — confirmar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-318, CA-MV-319 y CA-MV-321 — confirmada con su pago y la referencia; los puntos"
          + " entran desde la cuenta de puntos emitidos en dos asientos de ABONO; sin referencia"
          + " también")
  void confirma() throws Exception {
    UUID compra = comprarYLeer(comprador, "10.00", "compra-000020");

    mvc.perform(confirmar(compra, "{\"providerReference\":\"BANCO-123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMADA"))
        .andExpect(jsonPath("$.confirmedAt").exists())
        .andExpect(jsonPath("$.payments[0].status").value("CONFIRMADO"))
        .andExpect(jsonPath("$.payments[0].providerReference").value("BANCO-123"));

    assertThat(saldo(jdbc, comprador, "PUNTOS")).isEqualByComparingTo("1000.00");
    assertThat(emitidos()).isEqualByComparingTo("-1000.00");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM movement_entries WHERE movement_id = ? AND event = 'ABONO'"
                    + " AND payment_id IS NOT NULL",
                Integer.class,
                compra))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(amount) FROM movement_entries WHERE movement_id = ?",
                BigDecimal.class,
                compra))
        .isEqualByComparingTo("0");

    UUID segunda = comprarYLeer(comprador, "1.00", "compra-000021");
    mvc.perform(confirmar(segunda, "{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payments[0].providerReference").doesNotExist());
    assertThat(saldo(jdbc, comprador, "PUNTOS")).isEqualByComparingTo("1100.00");
  }

  @Test
  @DisplayName("CA-MV-320 — se abonan los puntos congelados, aunque la tasa haya cambiado")
  void abonaLosCongelados() throws Exception {
    UUID compra = comprarYLeer(comprador, "10.00", "compra-000022");
    PointsFixtures.tasa(jdbc, USD, "500", administrador);
    mvc.perform(confirmar(compra, "{}")).andExpect(status().isOk());
    assertThat(saldo(jdbc, comprador, "PUNTOS")).isEqualByComparingTo("1000.00");
  }

  @Test
  @DisplayName(
      "CA-MV-322 a CA-MV-324 — confirmar dos veces o lo rechazado es 409 y no abona; confirmar y"
          + " rechazar a la vez abonan una vez o ninguna; una venta o un retiro no se encuentran")
  void unaSolaVez() throws Exception {
    UUID compra = comprarYLeer(comprador, "10.00", "compra-000023");
    mvc.perform(confirmar(compra, "{}")).andExpect(status().isOk());
    mvc.perform(confirmar(compra, "{}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
    mvc.perform(rechazar(compra, "Tarde")).andExpect(status().isConflict());
    assertThat(saldo(jdbc, comprador, "PUNTOS")).isEqualByComparingTo("1000.00");

    UUID rechazada = comprarYLeer(comprador, "3.00", "compra-000024");
    mvc.perform(rechazar(rechazada, "No entró")).andExpect(status().isOk());
    mvc.perform(confirmar(rechazada, "{}")).andExpect(status().isConflict());
    assertThat(saldo(jdbc, comprador, "PUNTOS")).isEqualByComparingTo("1000.00");

    // CA-MV-323, con dos hilos.
    UUID disputada = comprarYLeer(otro, "2.00", "compra-000025");
    CountDownLatch salida = new CountDownLatch(1);
    ExecutorService hilos = Executors.newFixedThreadPool(2);
    List<Integer> estados = new ArrayList<>();
    try {
      Future<Integer> a =
          hilos.submit(
              () -> {
                salida.await();
                return mvc.perform(confirmar(disputada, "{}"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      Future<Integer> b =
          hilos.submit(
              () -> {
                salida.await();
                return mvc.perform(rechazar(disputada, "Carrera"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      salida.countDown();
      estados.add(a.get());
      estados.add(b.get());
    } finally {
      hilos.shutdownNow();
    }
    assertThat(estados).containsExactlyInAnyOrder(200, 409);
    String estado =
        jdbc.queryForObject("SELECT status FROM movements WHERE id = ?", String.class, disputada);
    assertThat(saldo(jdbc, otro, "PUNTOS"))
        .isEqualByComparingTo("CONFIRMADA".equals(estado) ? "200.00" : "0");

    // CA-MV-324
    UUID bono = UUID.fromString(JsonPath.read(bonoDe(comprador), "$.id"));
    mvc.perform(confirmar(bono, "{}")).andExpect(status().isNotFound());
    mvc.perform(confirmar(UUID.randomUUID(), "{}")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("CA-MV-325 — confirmar exige su permiso, y queda auditado con quien confirmó")
  void permisoDeConfirmar() throws Exception {
    UUID compra = comprarYLeer(comprador, "1.00", "compra-000026");
    mvc.perform(
            post(
                    "/api/v1/movements/payments/{id}/confirmation",
                    PaymentFixtures.pagoAConciliar(jdbc, compra))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user(administrador.toString()).authorities(() -> "movements:reject-payment")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(
                    "/api/v1/movements/payments/{id}/confirmation",
                    PaymentFixtures.pagoAConciliar(jdbc, compra))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(confirmar(compra, "{}")).andExpect(status().isOk());
    assertThat(auditoriaDe(compra, "UPDATE")).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-029` — rechazar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-326 a CA-MV-330 — rechazada con motivo y su pago también; ningún saldo; una sola"
          + " vez; el motivo se valida; una venta no se encuentra")
  void rechaza() throws Exception {
    UUID compra = comprarYLeer(comprador, "10.00", "compra-000027");

    mvc.perform(rechazar(compra, "")).andExpect(status().isBadRequest());
    mvc.perform(rechazar(compra, "x".repeat(501))).andExpect(status().isBadRequest());

    mvc.perform(rechazar(compra, "El banco lo devolvió"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RECHAZADA"))
        .andExpect(jsonPath("$.payments[0].status").value("RECHAZADO"))
        .andExpect(jsonPath("$.payments[0].rejectionReason").value("El banco lo devolvió"));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movement_entries", Integer.class))
        .isZero();

    mvc.perform(rechazar(compra, "Otra vez"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    UUID bono = UUID.fromString(JsonPath.read(bonoDe(comprador), "$.id"));
    mvc.perform(rechazar(bono, "No")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("CA-MV-331 — rechazar exige su permiso, y queda auditado con quien rechazó")
  void permisoDeRechazar() throws Exception {
    UUID compra = comprarYLeer(comprador, "1.00", "compra-000028");
    mvc.perform(
            post(
                    "/api/v1/movements/payments/{id}/rejection",
                    PaymentFixtures.pagoAConciliar(jdbc, compra))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"No\"}")
                .with(
                    user(administrador.toString()).authorities(() -> "movements:confirm-payment")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(
                    "/api/v1/movements/payments/{id}/rejection",
                    PaymentFixtures.pagoAConciliar(jdbc, compra))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"No\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(rechazar(compra, "No")).andExpect(status().isOk());
    assertThat(auditoriaDe(compra, "UPDATE")).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder comprar(
      UUID quien, String importe, String metodo, String clave) {
    MockHttpServletRequestBuilder peticion =
        post(BASE + "/mine/points-purchases")
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpoDeCompra(USD, importe, metodo))
            .with(user(quien.toString()).authorities(() -> "movements:buy-points"));
    return clave == null ? peticion : peticion.header("Idempotency-Key", clave);
  }

  private MockHttpServletRequestBuilder comprarEn(UUID quien, String moneda, String clave) {
    return post(BASE + "/mine/points-purchases")
        .header("Idempotency-Key", clave)
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpoDeCompra(moneda, "5.00", TARJETA))
        .with(user(quien.toString()).authorities(() -> "movements:buy-points"));
  }

  private static String cuerpoDeCompra(String moneda, String importe, String metodo) {
    return "{\"currencyId\":\"%s\",\"amount\":%s,\"paymentMethodId\":\"%s\"}"
        .formatted(moneda, importe, metodo);
  }

  private UUID comprarYLeer(UUID quien, String importe, String clave) throws Exception {
    String cuerpo =
        mvc.perform(comprar(quien, importe, TARJETA, clave))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(cuerpo, "$.id"));
  }

  private MockHttpServletRequestBuilder confirmar(UUID compra, String cuerpo) {
    return post(
            "/api/v1/movements/payments/{id}/confirmation",
            PaymentFixtures.pagoAConciliar(jdbc, compra))
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo)
        .with(user(administrador.toString()).authorities(() -> "movements:confirm-payment"));
  }

  private MockHttpServletRequestBuilder rechazar(UUID compra, String motivo) {
    return post(
            "/api/v1/movements/payments/{id}/rejection",
            PaymentFixtures.pagoAConciliar(jdbc, compra))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"" + motivo + "\"}")
        .with(user(administrador.toString()).authorities(() -> "movements:reject-payment"));
  }

  private String bonoDe(UUID quien) throws Exception {
    return mvc.perform(
            post(BASE + "/bonuses")
                .header("Idempotency-Key", "bono-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"userId\":\"%s\",\"currencyId\":\"%s\",\"amount\":1,\"concept\":\"x\"}"
                        .formatted(quien, USD))
                .with(user(administrador.toString()).authorities(() -> "movements:grant-bonus")))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private int compras() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM movements m JOIN movement_types t ON t.id = m.movement_type_id"
            + " WHERE t.code = 'COMPRA_PUNTOS'",
        Integer.class);
  }

  private BigDecimal emitidos() {
    // En centésimas en la base (ADR-006), también los puntos.
    return MinorUnits.fromMinor(
        jdbc.queryForObject(
            "SELECT balance FROM accounts WHERE user_id IS NULL AND kind = 'PUNTOS_EMITIDOS'",
            Long.class));
  }

  private int auditoriaDe(UUID compra, String accion) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_change_log WHERE entity = 'movements' AND entity_id = ?"
            + " AND action = ? AND actor_id = ?",
        Integer.class,
        compra,
        accion,
        administrador);
  }

  private void limpiar() {
    PointsFixtures.limpiar(jdbc);
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'pp-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'pp-%'");
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
