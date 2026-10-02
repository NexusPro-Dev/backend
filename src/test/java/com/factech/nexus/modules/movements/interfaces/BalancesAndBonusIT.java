package com.factech.nexus.modules.movements.interfaces;

import static com.factech.nexus.modules.movements.LedgerFixtures.USD;
import static com.factech.nexus.modules.movements.LedgerFixtures.llenarBilletera;
import static com.factech.nexus.modules.movements.LedgerFixtures.saldo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.LedgerFixtures;
import com.factech.nexus.modules.movements.PayoutFixtures;
import com.factech.nexus.modules.movements.domain.service.CreditService;
import com.jayway.jsonpath.JsonPath;
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

/** `RF-MV-022` — mis saldos y su historial; y `RF-MV-023` — otorgar un bono. */
@AutoConfigureMockMvc
class BalancesAndBonusIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CreditService abonos;

  private UUID persona;
  private UUID otra;
  private UUID administrador;

  @BeforeEach
  void sembrar() {
    limpiar();
    persona = persona("bb-persona", "ACTIVO");
    otra = persona("bb-otra", "ACTIVO");
    administrador = persona("bb-admin", "ACTIVO");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-022`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-251 a CA-MV-254 — los tres saldos por moneda, vacío sin cuentas, y nada ajeno")
  void saldos() throws Exception {
    mvc.perform(get("/api/v1/movements/mine/balances").with(saldosDe(persona)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    llenarBilletera(abonos, persona, "100.00");
    llenarBilletera(abonos, otra, "999.00");
    UUID retiro = pedirRetiro(persona, "30.00");

    mvc.perform(get("/api/v1/movements/mine/balances").with(saldosDe(persona)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].currency.code").value("USD"))
        .andExpect(jsonPath("$[0].wallet").value(70.00))
        .andExpect(jsonPath("$[0].held").value(30.00))
        .andExpect(jsonPath("$[0].points").value(0));

    // CA-MV-253, la segunda mitad: tras negarlo, los saldos vuelven.
    negarRetiro(retiro);
    mvc.perform(get("/api/v1/movements/mine/balances").with(saldosDe(persona)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].wallet").value(100.00))
        .andExpect(jsonPath("$[0].held").value(0));
  }

  @Test
  @DisplayName(
      "CA-MV-255 a CA-MV-258 — el historial asiento a asiento, con el movimiento, filtrable y"
          + " paginado; sin cuentas de la empresa")
  void historial() throws Exception {
    llenarBilletera(abonos, persona, "100.00");
    UUID retiro = pedirRetiro(persona, "30.00");

    mvc.perform(get("/api/v1/movements/mine/balances/entries").with(historialDe(persona)))
        .andExpect(status().isOk())
        // El abono del bono (1: su billetera; la de BONOS es de la empresa) y la
        // solicitud del retiro (2: billetera y retenido).
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content[0].event").value("SOLICITUD"))
        .andExpect(jsonPath("$.content[0].movement.type").value("RETIRO"))
        .andExpect(jsonPath("$.content[2].event").value("ABONO"))
        .andExpect(jsonPath("$.content[2].movement.type").value("BONO"))
        .andExpect(jsonPath("$.content[2].balanceAfter").value(100.00));

    mvc.perform(
            get("/api/v1/movements/mine/balances/entries")
                .param("account", "retenido")
                .with(historialDe(persona)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].amount").value(30.00));

    // CA-MV-256: aprobado, el retiro suma UNA fila de APROBACION —la de lo
    // retenido; la otra pata es de la empresa— a las dos de SOLICITUD.
    aprobarRetiro(retiro);
    mvc.perform(get("/api/v1/movements/mine/balances/entries").with(historialDe(persona)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(jsonPath("$.content[?(@.event == 'SOLICITUD')]", hasSize(2)))
        .andExpect(jsonPath("$.content[?(@.event == 'APROBACION')]", hasSize(1)))
        .andExpect(jsonPath("$.content[?(@.event == 'APROBACION')].account").value("RETENIDO"));

    // CA-MV-257: moneda, cuenta y periodo se combinan.
    mvc.perform(
            get("/api/v1/movements/mine/balances/entries")
                .param("currencyId", USD)
                .param("account", "RETENIDO")
                .param("from", "2000-01-01T00:00:00Z")
                .param("to", "2100-01-01T00:00:00Z")
                .with(historialDe(persona)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(
            get("/api/v1/movements/mine/balances/entries")
                // Una moneda en la que la persona no tiene cuentas: el filtro no
                // valida que exista, solo filtra.
                .param("currencyId", UUID.randomUUID().toString())
                .param("account", "RETENIDO")
                .with(historialDe(persona)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(
            get("/api/v1/movements/mine/balances/entries")
                .param("currencyId", USD)
                .param("to", "2000-01-01T00:00:00Z")
                .with(historialDe(persona)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));

    mvc.perform(
            get("/api/v1/movements/mine/balances/entries")
                .param("account", "BONOS")
                .param("from", "2026-12-01T00:00:00Z")
                .param("to", "2026-01-01T00:00:00Z")
                .with(historialDe(persona)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(2));

    mvc.perform(
            get("/api/v1/movements/mine/balances/entries")
                .param("size", "1")
                .param("page", "1")
                .with(historialDe(persona)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1));
  }

  @Test
  @DisplayName("CA-MV-259 — cada operación exige su permiso")
  void permisosDeSaldos() throws Exception {
    mvc.perform(get("/api/v1/movements/mine/balances").with(historialDe(persona)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/movements/mine/balances/entries").with(saldosDe(persona)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/movements/mine/balances")).andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-023`
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-260 y CA-MV-261 — el bono nace confirmado, sin pago, y abona la billetera desde la"
          + " cuenta de bonos")
  void otorga() throws Exception {
    mvc.perform(bono(persona, "25.50", "Premio de septiembre", "bono-000001"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("BONO"))
        .andExpect(jsonPath("$.status").value("CONFIRMADA"))
        .andExpect(jsonPath("$.concept").value("Premio de septiembre"))
        .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("BON-")))
        .andExpect(jsonPath("$.payments.length()").value(0));

    assertThat(saldo(jdbc, persona, "BILLETERA")).isEqualByComparingTo("25.50");
    assertThat(
            jdbc.queryForObject(
                "SELECT balance FROM accounts WHERE user_id IS NULL AND kind = 'BONOS'",
                java.math.BigDecimal.class))
        .isEqualByComparingTo("-25.50");
  }

  @Test
  @DisplayName(
      "CA-MV-262 y CA-MV-263 — la misma petición no abona dos veces, también a la vez; la misma"
          + " clave con otros datos es 409")
  void idempotente() throws Exception {
    mvc.perform(bono(persona, "10.00", "Uno", "bono-000002")).andExpect(status().isCreated());
    mvc.perform(bono(persona, "10.00", "Uno", "bono-000002")).andExpect(status().isOk());
    mvc.perform(bono(persona, "11.00", "Uno", "bono-000002"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-005"));
    assertThat(saldo(jdbc, persona, "BILLETERA")).isEqualByComparingTo("10.00");

    CountDownLatch salida = new CountDownLatch(1);
    ExecutorService hilos = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> r = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        r.add(
            hilos.submit(
                () -> {
                  salida.await();
                  return mvc.perform(bono(otra, "5.00", "Doble clic", "bono-000003"))
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
    assertThat(saldo(jdbc, otra, "BILLETERA")).isEqualByComparingTo("5.00");
  }

  @Test
  @DisplayName(
      "CA-MV-264 y CA-MV-265 — a quien espera su depósito se le otorga; a quien no existe no; y"
          + " sin motivo, sin clave o con importe malo, nada cambia")
  void bordesDelBono() throws Exception {
    UUID enEspera = persona("bb-espera", "FTD_PENDIENTE");
    mvc.perform(bono(enEspera, "5.00", "Bienvenida", "bono-000004"))
        .andExpect(status().isCreated());
    mvc.perform(bono(UUID.randomUUID(), "5.00", "Nadie", "bono-000005"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(bono(persona, "5.00", "  ", "bono-000006")).andExpect(status().isBadRequest());
    mvc.perform(bono(persona, "5.00", "Sin clave", null)).andExpect(status().isBadRequest());
    mvc.perform(bono(persona, "0", "Cero", "bono-000007")).andExpect(status().isBadRequest());
    mvc.perform(bono(persona, "1.001", "Decimales", "bono-000008"))
        .andExpect(status().isBadRequest());
    assertThat(saldo(jdbc, persona, "BILLETERA")).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName(
      "CA-MV-266 a CA-MV-268 — permiso, historial y retiro del bono, y su auditoría con quien lo"
          + " otorgó")
  void bonoPermisosYUso() throws Exception {
    mvc.perform(
            post("/api/v1/movements/bonuses")
                .header("Idempotency-Key", "bono-000009")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoDeBono(persona, "5.00", "x"))
                .with(user(administrador.toString()).authorities(() -> "movements:read")))
        .andExpect(status().isForbidden());

    mvc.perform(bono(persona, "40.00", "Retirable", "bono-000010")).andExpect(status().isCreated());
    mvc.perform(get("/api/v1/movements/mine/balances/entries").with(historialDe(persona)))
        .andExpect(jsonPath("$.content[0].movement.concept").value("Retirable"));
    pedirRetiro(persona, "40.00");
    assertThat(saldo(jdbc, persona, "BILLETERA")).isEqualByComparingTo("0");

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE entity = 'movements'"
                    + " AND action = 'CREATE' AND actor_id = ?",
                Integer.class,
                administrador))
        .isPositive();
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder bono(
      UUID quien, String importe, String motivo, String clave) {
    MockHttpServletRequestBuilder peticion =
        post("/api/v1/movements/bonuses")
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpoDeBono(quien, importe, motivo))
            .with(user(administrador.toString()).authorities(() -> "movements:grant-bonus"));
    return clave == null ? peticion : peticion.header("Idempotency-Key", clave);
  }

  private static String cuerpoDeBono(UUID quien, String importe, String motivo) {
    return "{\"userId\":\"%s\",\"currencyId\":\"%s\",\"amount\":%s,\"concept\":\"%s\"}"
        .formatted(quien, USD, importe, motivo);
  }

  private UUID pedirRetiro(UUID quien, String importe) throws Exception {
    PayoutFixtures.listaParaRetirar(jdbc, quien);
    String cuerpo =
        mvc.perform(
                post("/api/v1/movements/mine/withdrawals")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currencyId\":\"" + USD + "\",\"amount\":" + importe + "}")
                    .with(user(quien.toString()).authorities(() -> "movements:request-withdrawal")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(cuerpo, "$.movement.id"));
  }

  private void aprobarRetiro(UUID retiro) throws Exception {
    mvc.perform(
            post("/api/v1/movements/{id}/withdrawal-approval", retiro)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(
                    user(administrador.toString())
                        .authorities(() -> "movements:approve-withdrawal")))
        .andExpect(status().isOk());
  }

  private void negarRetiro(UUID retiro) throws Exception {
    mvc.perform(
            post("/api/v1/movements/{id}/withdrawal-rejection", retiro)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"No procede\"}")
                .with(
                    user(administrador.toString())
                        .authorities(() -> "movements:reject-withdrawal")))
        .andExpect(status().isOk());
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor saldosDe(
      UUID quien) {
    return user(quien.toString()).authorities(() -> "movements:read-own-balances");
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor historialDe(
      UUID quien) {
    return user(quien.toString()).authorities(() -> "movements:list-own-entries");
  }

  private void limpiar() {
    LedgerFixtures.limpiar(jdbc);
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'bb-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'bb-%'");
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
