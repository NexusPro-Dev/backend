package com.factech.nexus.modules.movements.interfaces;

import static com.factech.nexus.modules.movements.LedgerFixtures.USD;
import static com.factech.nexus.modules.movements.LedgerFixtures.llenarBilletera;
import static com.factech.nexus.modules.movements.LedgerFixtures.saldo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.LedgerFixtures;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.modules.movements.PayoutFixtures;
import com.factech.nexus.modules.movements.domain.service.CreditService;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

/** `RF-MV-019`, `RF-MV-020` y `RF-MV-021` — pedir, aprobar y negar un retiro. */
@AutoConfigureMockMvc
class WithdrawalIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CreditService abonos;

  private UUID persona;
  private UUID administrador;

  @BeforeEach
  void sembrar() {
    limpiar();
    persona = persona("wd-persona", "ACTIVO");
    administrador = persona("wd-admin", "ACTIVO");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-019`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-224 y CA-MV-225 — con saldo, el retiro nace pendiente y el importe pasa de la"
          + " billetera a lo retenido en dos asientos que suman cero")
  void pideYRetiene() throws Exception {
    llenarBilletera(abonos, persona, "100.00");

    mvc.perform(pedir("60.00").with(propio(persona)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.movement.type").value("RETIRO"))
        .andExpect(jsonPath("$.movement.status").value("PENDIENTE"))
        .andExpect(jsonPath("$.movement.code").value(org.hamcrest.Matchers.startsWith("RET-")))
        .andExpect(jsonPath("$.balances.wallet").value(40.00))
        .andExpect(jsonPath("$.balances.held").value(60.00));

    assertThat(
            jdbc.queryForObject(
                "SELECT sum(amount) FROM movement_entries WHERE event = 'SOLICITUD'",
                BigDecimal.class))
        .isEqualByComparingTo("0");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM movement_entries WHERE event = 'SOLICITUD'", Integer.class))
        .isEqualTo(2);
  }

  @Test
  @DisplayName(
      "CA-MV-226 y CA-MV-227 — sin saldo suficiente, o sin billetera, es 409 con el disponible y"
          + " no queda nada")
  void noAlcanza() throws Exception {
    mvc.perform(pedir("10.00").with(propio(persona)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    llenarBilletera(abonos, persona, "50.00");
    mvc.perform(pedir("80.00").with(propio(persona)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("50.00")));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM movements m JOIN movement_types t"
                    + " ON t.id = m.movement_type_id WHERE t.code = 'RETIRO'",
                Integer.class))
        .isZero();
    assertThat(saldo(jdbc, persona, "BILLETERA")).isEqualByComparingTo("50.00");
  }

  @Test
  @DisplayName(
      "CA-MV-228 — dos peticiones a la vez que juntas superan el saldo: una pasa y otra no, y la"
          + " billetera nunca queda en negativo")
  void carrera() throws Exception {
    llenarBilletera(abonos, persona, "100.00");
    CountDownLatch salida = new CountDownLatch(1);
    ExecutorService hilos = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> respuestas = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        respuestas.add(
            hilos.submit(
                () -> {
                  salida.await();
                  return mvc.perform(pedir("60.00").with(propio(persona)))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      }
      salida.countDown();
      List<Integer> codigos = new ArrayList<>();
      for (Future<Integer> r : respuestas) {
        codigos.add(r.get());
      }
      assertThat(codigos).containsExactlyInAnyOrder(201, 409);
    } finally {
      hilos.shutdownNow();
    }
    assertThat(saldo(jdbc, persona, "BILLETERA")).isEqualByComparingTo("40.00");
    assertThat(saldo(jdbc, persona, "RETENIDO")).isEqualByComparingTo("60.00");
  }

  @Test
  @DisplayName("CA-MV-229 — importe cero, negativo, con decimales de más, o moneda inexistente")
  void validaciones() throws Exception {
    llenarBilletera(abonos, persona, "100.00");
    mvc.perform(pedir("0").with(propio(persona))).andExpect(status().isBadRequest());
    mvc.perform(pedir("-5.00").with(propio(persona))).andExpect(status().isBadRequest());
    mvc.perform(pedir("1.005").with(propio(persona))).andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/movements/mine/withdrawals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currencyId\":\"" + UUID.randomUUID() + "\",\"amount\":10.00}")
                .with(propio(persona)))
        .andExpect(status().isUnprocessableEntity());
    assertThat(saldo(jdbc, persona, "BILLETERA")).isEqualByComparingTo("100.00");
  }

  @Test
  @DisplayName("CA-MV-230 — una cuenta a la espera de su primer depósito no pide retiros")
  void cuentaQueNoOpera() throws Exception {
    UUID enEspera = persona("wd-espera", "FTD_PENDIENTE");
    llenarBilletera(abonos, enEspera, "100.00");
    mvc.perform(pedir("10.00").with(propio(enEspera)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
  }

  @Test
  @DisplayName("CA-MV-231 — sin movements:request-withdrawal es 403; sin token, 401")
  void permisosDePedir() throws Exception {
    mvc.perform(pedir("10.00").with(user(persona.toString()).authorities(() -> "x:y")))
        .andExpect(status().isForbidden());
    mvc.perform(pedir("10.00")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "CA-MV-232 y CA-MV-233 — el retiro está en el libro y no en «mis compras», y las"
          + " operaciones de la venta no lo alcanzan")
  void noEsUnaVenta() throws Exception {
    llenarBilletera(abonos, persona, "100.00");
    UUID retiro = pedirYLeer("30.00");

    mvc.perform(
            get("/api/v1/movements")
                .param("type", "RETIRO")
                .with(user(administrador.toString()).authorities(() -> "movements:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(retiro.toString()));
    mvc.perform(
            get("/api/v1/movements/mine/shopping")
                .with(user(persona.toString()).authorities(() -> "movements:list-own")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));

    mvc.perform(
            post(
                    "/api/v1/movements/payments/{id}/confirmation",
                    PaymentFixtures.pagoAConciliar(jdbc, retiro))
                .with(
                    user(administrador.toString()).authorities(() -> "movements:confirm-payment")))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/movements/{id}/voiding", retiro)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"x\"}")
                .with(user(administrador.toString()).authorities(() -> "movements:void")))
        .andExpect(status().isNotFound());
    mvc.perform(
            post(
                    "/api/v1/movements/payments/{id}/rejection",
                    PaymentFixtures.pagoAConciliar(jdbc, retiro))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"x\"}")
                .with(user(administrador.toString()).authorities(() -> "movements:reject-payment")))
        .andExpect(status().isNotFound());
    assertThat(estado(retiro)).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName("CA-MV-234 — el esquema rechaza una billetera en negativo por cualquier vía")
  void elEsquemaDefiende() {
    llenarBilletera(abonos, persona, "10.00");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE accounts SET balance = -1 WHERE user_id = ? AND kind = 'BILLETERA'",
                    persona))
        .hasMessageContaining("ck_accounts_saldo");
  }

  @Test
  @DisplayName("CA-MV-235 — el retiro queda auditado")
  void auditado() throws Exception {
    llenarBilletera(abonos, persona, "100.00");
    UUID retiro = pedirYLeer("30.00");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE entity = 'movements'"
                    + " AND entity_id = ? AND action = 'CREATE'",
                Integer.class,
                retiro))
        .isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-020`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-236 a CA-MV-238 — aprobar confirma, escribe el pago MANUAL con la referencia y saca"
          + " lo retenido; la billetera no cambia")
  void aprueba() throws Exception {
    llenarBilletera(abonos, persona, "100.00");
    UUID retiro = pedirYLeer("30.00");

    mvc.perform(aprobar(retiro, "{\"providerReference\":\"TRF-889\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMADA"))
        .andExpect(jsonPath("$.payments.length()").value(1))
        .andExpect(jsonPath("$.payments[0].paymentMethod.code").value("MANUAL"))
        .andExpect(jsonPath("$.payments[0].status").value("CONFIRMADO"))
        .andExpect(jsonPath("$.payments[0].providerReference").value("TRF-889"));

    assertThat(saldo(jdbc, persona, "RETENIDO")).isEqualByComparingTo("0");
    assertThat(saldo(jdbc, persona, "BILLETERA")).isEqualByComparingTo("70.00");
    assertThat(saldoDeLaEmpresa("RETIROS")).isEqualByComparingTo("30.00");
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(amount) FROM movement_entries WHERE event = 'APROBACION'",
                BigDecimal.class))
        .isEqualByComparingTo("0");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM movement_entries WHERE event = 'APROBACION'"
                    + " AND payment_id IS NOT NULL",
                Integer.class))
        .isEqualTo(2);

    UUID otro = pedirYLeer("10.00");
    mvc.perform(aprobar(otro, "{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payments[0].providerReference").isEmpty());
  }

  @Test
  @DisplayName(
      "CA-MV-239, CA-MV-240 y CA-MV-242 — aprobar dos veces, aprobar uno negado o una venta")
  void aprobarNoPendiente() throws Exception {
    llenarBilletera(abonos, persona, "100.00");
    UUID retiro = pedirYLeer("30.00");
    mvc.perform(aprobar(retiro, "{}")).andExpect(status().isOk());
    mvc.perform(aprobar(retiro, "{}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("CONFIRMADA")));

    UUID negado = pedirYLeer("10.00");
    mvc.perform(negar(negado, "No procede")).andExpect(status().isOk());
    mvc.perform(aprobar(negado, "{}")).andExpect(status().isConflict());

    mvc.perform(aprobar(UUID.randomUUID(), "{}")).andExpect(status().isNotFound());
    UUID venta = insertarVenta();
    mvc.perform(aprobar(venta, "{}")).andExpect(status().isNotFound());
    assertThat(estado(venta)).isEqualTo("PENDIENTE");
    assertThat(saldo(jdbc, persona, "BILLETERA")).isEqualByComparingTo("70.00");
  }

  @Test
  @DisplayName("CA-MV-241 — aprobar y negar a la vez mueven lo retenido una sola vez")
  void aprobarYNegarALaVez() throws Exception {
    llenarBilletera(abonos, persona, "100.00");
    UUID retiro = pedirYLeer("30.00");
    CountDownLatch salida = new CountDownLatch(1);
    ExecutorService hilos = Executors.newFixedThreadPool(2);
    try {
      Future<Integer> a =
          hilos.submit(
              () -> {
                salida.await();
                return mvc.perform(aprobar(retiro, "{}")).andReturn().getResponse().getStatus();
              });
      Future<Integer> n =
          hilos.submit(
              () -> {
                salida.await();
                return mvc.perform(negar(retiro, "No")).andReturn().getResponse().getStatus();
              });
      salida.countDown();
      assertThat(List.of(a.get(), n.get())).containsExactlyInAnyOrder(200, 409);
    } finally {
      hilos.shutdownNow();
    }
    assertThat(saldo(jdbc, persona, "RETENIDO")).isEqualByComparingTo("0");
    BigDecimal billetera = saldo(jdbc, persona, "BILLETERA");
    assertThat(
            billetera.compareTo(new BigDecimal("70.00")) == 0
                || billetera.compareTo(new BigDecimal("100.00")) == 0)
        .isTrue();
  }

  @Test
  @DisplayName("CA-MV-243 — sin movements:approve-withdrawal es 403; sin token 401")
  void permisosDeAprobar() throws Exception {
    llenarBilletera(abonos, persona, "100.00");
    UUID retiro = pedirYLeer("30.00");
    mvc.perform(
            post("/api/v1/movements/{id}/withdrawal-approval", retiro)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(
                    user(administrador.toString())
                        .authorities(() -> "movements:reject-withdrawal")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/movements/{id}/withdrawal-approval", retiro)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-021`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-244 a CA-MV-246 — negar deja RECHAZADA con su motivo, devuelve lo retenido sin"
          + " pago, y la persona puede volver a pedir")
  void niega() throws Exception {
    llenarBilletera(abonos, persona, "100.00");
    UUID retiro = pedirYLeer("30.00");

    mvc.perform(negar(retiro, "Datos bancarios sin verificar"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RECHAZADA"))
        .andExpect(jsonPath("$.rejectionReason").value("Datos bancarios sin verificar"))
        .andExpect(jsonPath("$.payments.length()").value(0));

    assertThat(saldo(jdbc, persona, "BILLETERA")).isEqualByComparingTo("100.00");
    assertThat(saldo(jdbc, persona, "RETENIDO")).isEqualByComparingTo("0");
    mvc.perform(pedir("100.00").with(propio(persona))).andExpect(status().isCreated());
  }

  @Test
  @DisplayName("CA-MV-247 a CA-MV-250 — negar dos veces, sin motivo, una venta, y los permisos")
  void negarLosBordes() throws Exception {
    llenarBilletera(abonos, persona, "100.00");
    UUID retiro = pedirYLeer("30.00");
    mvc.perform(negar(retiro, "   ")).andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/movements/{id}/withdrawal-rejection", retiro)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(
                    user(administrador.toString())
                        .authorities(() -> "movements:reject-withdrawal")))
        .andExpect(status().isBadRequest());
    assertThat(estado(retiro)).isEqualTo("PENDIENTE");

    mvc.perform(negar(retiro, "No")).andExpect(status().isOk());
    mvc.perform(negar(retiro, "Otra vez")).andExpect(status().isConflict());
    mvc.perform(negar(UUID.randomUUID(), "No")).andExpect(status().isNotFound());
    UUID venta = insertarVenta();
    mvc.perform(negar(venta, "No")).andExpect(status().isNotFound());
    assertThat(estado(venta)).isEqualTo("PENDIENTE");

    mvc.perform(
            post("/api/v1/movements/{id}/withdrawal-rejection", retiro)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"No\"}")
                .with(
                    user(administrador.toString())
                        .authorities(() -> "movements:approve-withdrawal")))
        .andExpect(status().isForbidden());
    assertThat(saldo(jdbc, persona, "BILLETERA")).isEqualByComparingTo("100.00");
  }

  // ---------------------------------------------------------------------------

  private static MockHttpServletRequestBuilder pedir(String importe) {
    return post("/api/v1/movements/mine/withdrawals")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"currencyId\":\"" + USD + "\",\"amount\":" + importe + "}");
  }

  private UUID pedirYLeer(String importe) throws Exception {
    String cuerpo =
        mvc.perform(pedir(importe).with(propio(persona)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(cuerpo, "$.movement.id"));
  }

  private MockHttpServletRequestBuilder aprobar(UUID retiro, String cuerpo) {
    return post("/api/v1/movements/{id}/withdrawal-approval", retiro)
        .contentType(MediaType.APPLICATION_JSON)
        .content(cuerpo)
        .with(user(administrador.toString()).authorities(() -> "movements:approve-withdrawal"));
  }

  private MockHttpServletRequestBuilder negar(UUID retiro, String motivo) {
    return post("/api/v1/movements/{id}/withdrawal-rejection", retiro)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"" + motivo + "\"}")
        .with(user(administrador.toString()).authorities(() -> "movements:reject-withdrawal"));
  }

  private static RequestPostProcessor propio(UUID quien) {
    return user(quien.toString()).authorities(() -> "movements:request-withdrawal");
  }

  private BigDecimal saldoDeLaEmpresa(String cuenta) {
    return jdbc
        .query(
            "SELECT balance FROM accounts WHERE user_id IS NULL AND kind = ?"
                + " AND currency_id = CAST(? AS uuid)",
            (fila, n) -> fila.getBigDecimal(1),
            cuenta,
            USD)
        .stream()
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }

  /**
   * Una venta pendiente de la persona, escrita directamente: solo hace falta su identificador, para
   * comprobar que las operaciones del retiro no alcanzan un movimiento de otro tipo.
   */
  private UUID insertarVenta() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id,
                               currency_id, code, status, total_amount, discount_amount,
                               payable_amount, occurred_at)
        SELECT ?, t.id, s.id, ?, CAST(? AS uuid), ?, 'PENDIENTE', 10.00, 0, 10.00, now()
          FROM movement_types t
          JOIN movement_type_statuses s ON s.movement_type_id = t.id AND s.code = 'VALIDADO'
         WHERE t.code = 'VENTA'
        """,
        id,
        persona,
        USD,
        "VEN-WD-" + id.toString().substring(0, 8));
    return id;
  }

  private String estado(UUID movimiento) {
    return jdbc.queryForObject(
        "SELECT status FROM movements WHERE id = ?", String.class, movimiento);
  }

  private void limpiar() {
    LedgerFixtures.limpiar(jdbc);
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'MV'");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'wd-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'wd-%'");
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
    // Desde el 01-10-2026 un retiro exige una cuenta de cobro (`RN-MV-056`).
    PayoutFixtures.listaParaRetirar(jdbc, id);
    return id;
  }
}
