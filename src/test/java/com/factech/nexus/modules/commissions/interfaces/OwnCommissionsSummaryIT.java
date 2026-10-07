package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.AGENTE;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.DIRECTOR;
import static com.factech.nexus.modules.commissions.interfaces.SettlementFixtures.linea;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.commissions.domain.service.CloseCommissionPeriodService;
import com.factech.nexus.modules.movements.PaymentFixtures;
import jakarta.persistence.EntityManagerFactory;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * `RF-IN-008` · `T-05` y `T-08` — el resumen de mis comisiones (`CA-IN-090` a `CA-IN-097`).
 *
 * <p><b>Vive en el paquete de las pruebas de `CM`</b> y no en el de `IN` (`tasks.md` §3): las
 * comisiones nacen confirmando ventas por la API de `MV`, con los fixtures de liquidación, que son
 * de este paquete. La cadena: dos agentes con el mismo director; cada venta de 100,00 da 10,00 al
 * agente y 5,00 al director.
 */
@AutoConfigureMockMvc
class OwnCommissionsSummaryIT extends IntegrationTestBase {

  private static final OffsetDateTime VENDIDA_EL =
      OffsetDateTime.of(2026, 9, 10, 15, 0, 0, 0, ZoneOffset.UTC);
  private static final String RUTA = "/api/v1/indicators/commissions/mine/summary";
  private static final String PERMISO = "indicators:read-own-commissions-summary";
  private static final String COP = "01a03336-6d00-7002-9c4f-5e7ad3000002";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CloseCommissionPeriodService cierre;
  @Autowired private EntityManagerFactory emf;

  private UUID admin;
  private UUID cliente;
  private UUID agente;
  private UUID otro;
  private UUID director;
  private UUID producto;

  @BeforeEach
  void sembrar() {
    limpiar();
    admin = SettlementFixtures.persona(jdbc, "oc-admin", null);
    cliente = SettlementFixtures.persona(jdbc, "oc-cliente", null);
    agente = SettlementFixtures.persona(jdbc, "oc-agente", AGENTE);
    otro = SettlementFixtures.persona(jdbc, "oc-otro", AGENTE);
    director = SettlementFixtures.persona(jdbc, "oc-director", DIRECTOR);
    SettlementFixtures.superior(jdbc, agente, director);
    SettlementFixtures.superior(jdbc, otro, director);
    vincular(agente, "REGISTRO");
    vincular(otro, "HOTLINK");
    producto = SettlementFixtures.producto(jdbc, "OC1", "100.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, DIRECTOR, "5.00");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName(
      "CA-IN-090 y CA-IN-092 — por estado del lote, cuántas y cuánto; el total es la suma; sin"
          + " pagadas, el bloque viene en cero")
  void porEstado() throws Exception {
    confirmada(agente);
    confirmada(agente);
    cierre.closeManually(admin);
    confirmada(agente);

    resumen(agente)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.open.commissions").value(1))
        .andExpect(jsonPath("$.open.amounts[0].currency.code").value("USD"))
        .andExpect(jsonPath("$.open.amounts[0].amount").value(10.0))
        .andExpect(jsonPath("$.pending.commissions").value(2))
        .andExpect(jsonPath("$.pending.amounts[0].amount").value(20.0))
        .andExpect(jsonPath("$.paid.commissions").value(0))
        .andExpect(jsonPath("$.paid.amounts").isEmpty())
        .andExpect(jsonPath("$.total.commissions").value(3))
        .andExpect(jsonPath("$.total.amounts[0].amount").value(30.0))
        .andExpect(jsonPath("$.period.from").isEmpty())
        .andExpect(jsonPath("$.period.zone").value("America/Bogota"));
  }

  @Test
  @DisplayName(
      "CA-IN-091 — solo lo mío: el director ve sus 5,00 por venta, no los 10,00 de sus agentes;"
          + " un sellerId se ignora")
  void soloLoMio() throws Exception {
    confirmada(agente);
    confirmada(otro);

    resumen(director)
        .andExpect(jsonPath("$.total.commissions").value(2))
        .andExpect(jsonPath("$.total.amounts[0].amount").value(10.0));
    resumen(otro)
        .andExpect(jsonPath("$.total.commissions").value(1))
        .andExpect(jsonPath("$.total.amounts[0].amount").value(10.0));

    String sinFiltro = resumen(agente).andReturn().getResponse().getContentAsString();
    String conOtro =
        mvc.perform(get(RUTA).param("sellerId", director.toString()).with(conPermiso(agente)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(conOtro).isEqualTo(sinFiltro);

    // Quien no tiene comisiones, ceros.
    resumen(cliente)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.open.commissions").value(0))
        .andExpect(jsonPath("$.pending.commissions").value(0))
        .andExpect(jsonPath("$.paid.commissions").value(0))
        .andExpect(jsonPath("$.total.commissions").value(0))
        .andExpect(jsonPath("$.total.amounts").isEmpty());
  }

  @Test
  @DisplayName(
      "CA-IN-093 — el estado es el de hoy: pagar pasa las pendientes a pagadas, y una retirada al"
          + " abierto cuenta como abierta")
  void elEstadoDeHoy() throws Exception {
    confirmada(agente);
    confirmada(agente);
    cierre.closeManually(admin);
    UUID pendiente = loteDe(agente);

    UUID una =
        jdbc.queryForObject(
            "SELECT id FROM commissions WHERE batch_id = ? ORDER BY id LIMIT 1",
            UUID.class,
            pendiente);
    mvc.perform(
            post(
                    "/api/v1/commission-batches/{id}/commissions/{commissionId}/withdrawal",
                    pendiente,
                    una)
                .with(como("commission-batches:withdraw-commission")))
        .andExpect(status().isOk());
    resumen(agente)
        .andExpect(jsonPath("$.open.commissions").value(1))
        .andExpect(jsonPath("$.pending.commissions").value(1))
        .andExpect(jsonPath("$.total.commissions").value(2));

    mvc.perform(
            post("/api/v1/commission-batches/{id}/payment", pendiente)
                .with(como("commission-batches:pay")))
        .andExpect(status().isOk());
    resumen(agente)
        .andExpect(jsonPath("$.open.commissions").value(1))
        .andExpect(jsonPath("$.pending.commissions").value(0))
        .andExpect(jsonPath("$.paid.commissions").value(1))
        .andExpect(jsonPath("$.paid.amounts[0].amount").value(10.0))
        .andExpect(jsonPath("$.total.amounts[0].amount").value(20.0));
  }

  @Test
  @DisplayName("CA-IN-094 — por moneda; una inexistente da ceros; una mal formada, 400")
  void porMoneda() throws Exception {
    confirmada(agente);

    mvc.perform(get(RUTA).param("currencyId", SettlementFixtures.USD).with(conPermiso(agente)))
        .andExpect(jsonPath("$.total.commissions").value(1));
    mvc.perform(get(RUTA).param("currencyId", COP).with(conPermiso(agente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total.commissions").value(0))
        .andExpect(jsonPath("$.total.amounts").isEmpty());
    mvc.perform(
            get(RUTA).param("currencyId", UUID.randomUUID().toString()).with(conPermiso(agente)))
        .andExpect(jsonPath("$.total.commissions").value(0));
    mvc.perform(get(RUTA).param("currencyId", "no-es-uuid").with(conPermiso(agente)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "CA-IN-095 — por fechas de nacimiento, los dos días incluidos, en su estado de hoy; desde"
          + " posterior a hasta, 400 con VAL-002")
  void porFechas() throws Exception {
    confirmada(agente);
    confirmada(agente);
    // Una nació el 10-09 a las 23:30 de Bogotá; la otra, el 01-10 a las 00:00 de Bogotá.
    jdbc.update(
        "UPDATE commissions SET accrued_at = CAST(? AS timestamptz) WHERE id = (SELECT id FROM"
            + " commissions WHERE user_id = ? ORDER BY id LIMIT 1)",
        "2026-09-11T04:30:00Z",
        agente);
    jdbc.update(
        "UPDATE commissions SET accrued_at = CAST(? AS timestamptz) WHERE id = (SELECT id FROM"
            + " commissions WHERE user_id = ? ORDER BY id DESC LIMIT 1)",
        "2026-10-01T05:00:00Z",
        agente);

    mvc.perform(
            get(RUTA)
                .param("from", "2026-09-01")
                .param("to", "2026-09-10")
                .with(conPermiso(agente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.period.from").value("2026-09-01"))
        .andExpect(jsonPath("$.period.to").value("2026-09-10"))
        .andExpect(jsonPath("$.open.commissions").value(1))
        .andExpect(jsonPath("$.total.commissions").value(1));
    mvc.perform(get(RUTA).param("to", "2026-09-30").with(conPermiso(agente)))
        .andExpect(jsonPath("$.total.commissions").value(1));
    mvc.perform(get(RUTA).param("from", "2026-10-01").with(conPermiso(agente)))
        .andExpect(jsonPath("$.total.commissions").value(1));
    resumen(agente).andExpect(jsonPath("$.total.commissions").value(2));

    mvc.perform(
            get(RUTA)
                .param("from", "2026-09-30")
                .param("to", "2026-09-01")
                .with(conPermiso(agente)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
  }

  @Test
  @DisplayName(
      "CA-IN-096 — sin el permiso, 403, también con los de lotes y comisiones propias; sin token,"
          + " 401; sembrado a todo rol que ve sus lotes")
  void permisos() throws Exception {
    mvc.perform(get(RUTA).with(user(agente.toString()))).andExpect(status().isForbidden());
    for (String otroPermiso :
        new String[] {
          "commission-batches:list-own",
          "commission-batches:list-own-commissions",
          "indicators:read-commission-batches-summary"
        }) {
      mvc.perform(get(RUTA).with(user(agente.toString()).authorities(() -> otroPermiso)))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());

    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM (
                  (SELECT rp.role_id FROM role_permissions rp
                     JOIN permissions p ON p.id = rp.permission_id
                    WHERE p.code = 'commission-batches:list-own'
                   EXCEPT
                   SELECT rp.role_id FROM role_permissions rp
                     JOIN permissions p ON p.id = rp.permission_id
                    WHERE p.code = ?)
                  UNION ALL
                  (SELECT rp.role_id FROM role_permissions rp
                     JOIN permissions p ON p.id = rp.permission_id
                    WHERE p.code = ?
                   EXCEPT
                   SELECT rp.role_id FROM role_permissions rp
                     JOIN permissions p ON p.id = rp.permission_id
                    WHERE p.code = 'commission-batches:list-own')) AS diferencia
                """,
                Integer.class,
                PERMISO,
                PERMISO))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM role_permissions rp
                  JOIN permissions p ON p.id = rp.permission_id
                 WHERE p.code = ? AND rp.role_id = CAST(? AS uuid)
                """,
                Integer.class,
                PERMISO,
                AGENTE))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "CA-IN-097 — con cliente, solo las de sus ventas, en su estado de hoy, y las mismas que lista"
          + " RF-CM-026; uno inexistente da ceros; uno mal formado, 400")
  void porCliente() throws Exception {
    UUID otroCliente = SettlementFixtures.persona(jdbc, "oc-cliente2", null);
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin) VALUES (?, ?, 'REGISTRO')",
        otroCliente,
        agente);
    confirmadaPara(otroCliente, agente);
    cierre.closeManually(admin);
    confirmada(agente);
    confirmada(agente);

    mvc.perform(get(RUTA).param("clientId", otroCliente.toString()).with(conPermiso(agente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pending.commissions").value(1))
        .andExpect(jsonPath("$.open.commissions").value(0))
        .andExpect(jsonPath("$.total.commissions").value(1))
        .andExpect(jsonPath("$.total.amounts[0].amount").value(10.0));
    mvc.perform(get(RUTA).param("clientId", cliente.toString()).with(conPermiso(agente)))
        .andExpect(jsonPath("$.open.commissions").value(2))
        .andExpect(jsonPath("$.pending.commissions").value(0))
        .andExpect(jsonPath("$.total.commissions").value(2));
    mvc.perform(
            get("/api/v1/commission-batches/mine/commissions")
                .param("clientId", cliente.toString())
                .with(
                    user(agente.toString())
                        .authorities(() -> "commission-batches:list-own-commissions")))
        .andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(get(RUTA).param("clientId", otroCliente.toString()).with(conPermiso(director)))
        .andExpect(jsonPath("$.total.commissions").value(1))
        .andExpect(jsonPath("$.total.amounts[0].amount").value(5.0));

    mvc.perform(get(RUTA).param("clientId", UUID.randomUUID().toString()).with(conPermiso(agente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total.commissions").value(0))
        .andExpect(jsonPath("$.total.amounts").isEmpty());
    mvc.perform(get(RUTA).param("clientId", "no-es-uuid").with(conPermiso(agente)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("RNF-PERF — las mismas sentencias con una comisión que con tres")
  void elCosteNoCreceConLasComisiones() throws Exception {
    Statistics estadisticas = emf.unwrap(SessionFactory.class).getStatistics();
    confirmada(agente);

    estadisticas.clear();
    resumen(agente).andExpect(status().isOk());
    long una = estadisticas.getPrepareStatementCount();

    confirmada(agente);
    cierre.closeManually(admin);
    confirmada(agente);
    estadisticas.clear();
    resumen(agente).andExpect(jsonPath("$.total.commissions").value(3));
    assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(una);
  }

  // ---------------------------------------------------------------------------

  private ResultActions resumen(UUID actor) throws Exception {
    return mvc.perform(get(RUTA).with(conPermiso(actor)));
  }

  private UUID confirmada(UUID vendedor) throws Exception {
    return confirmadaPara(cliente, vendedor);
  }

  private UUID confirmadaPara(UUID comprador, UUID vendedor) throws Exception {
    UUID venta =
        SettlementFixtures.venta(
            jdbc, comprador, VENDIDA_EL, linea(producto, vendedor, 1, "100.00"));
    mvc.perform(
            post(
                    "/api/v1/movements/payments/{id}/confirmation",
                    PaymentFixtures.pagoAConciliar(jdbc, venta))
                .with(como("movements:confirm-payment")))
        .andExpect(status().isOk());
    return venta;
  }

  private UUID loteDe(UUID persona) {
    return jdbc.queryForObject(
        "SELECT id FROM commission_batches WHERE user_id = ? ORDER BY period_start DESC LIMIT 1",
        UUID.class,
        persona);
  }

  private void vincular(UUID vendedor, String origen) {
    jdbc.update(
        "INSERT INTO client_sellers (client_id, seller_id, origin) VALUES (?, ?, ?)",
        cliente,
        vendedor,
        origen);
  }

  private void limpiar() {
    jdbc.update(
        "DELETE FROM client_sellers WHERE client_id IN (SELECT id FROM users WHERE username LIKE"
            + " 'st-oc-%')");
    SettlementFixtures.limpiar(jdbc);
  }

  private static RequestPostProcessor conPermiso(UUID persona) {
    return user(persona.toString()).authorities(() -> PERMISO);
  }

  private static RequestPostProcessor como(String permiso) {
    return user(UUID.randomUUID().toString()).authorities(() -> permiso);
  }
}
