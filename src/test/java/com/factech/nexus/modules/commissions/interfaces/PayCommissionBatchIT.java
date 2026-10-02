package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.AGENTE;
import static com.factech.nexus.modules.commissions.interfaces.SettlementFixtures.linea;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.commissions.domain.service.CloseCommissionPeriodService;
import com.factech.nexus.modules.commissions.domain.service.PayCommissionBatchService;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.testing.ConcurrencyHarness;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Pagar un lote es abonarlo en la billetera (`RF-CM-011`, `CA-CM-189` a `CA-CM-196`). Los lotes
 * nacen devengando y cerrando de verdad; el abono lo escribe `MV` (`RF-MV-024`).
 */
@AutoConfigureMockMvc
class PayCommissionBatchIT extends IntegrationTestBase {

  private static final OffsetDateTime VENDIDA_EL =
      OffsetDateTime.of(2026, 9, 10, 15, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CloseCommissionPeriodService cierre;
  @Autowired private PayCommissionBatchService pago;

  private UUID cliente;
  private UUID agente;
  private UUID producto;

  @BeforeEach
  void sembrar() {
    SettlementFixtures.limpiar(jdbc);
    cliente = SettlementFixtures.persona(jdbc, "cliente", null);
    agente = SettlementFixtures.persona(jdbc, "agente", AGENTE);
    producto = SettlementFixtures.producto(jdbc, "BOT", "100.00");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    SettlementFixtures.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "CA-CM-189 — pagar un lote PENDIENTE lo deja PAGADO con fecha y movimiento, y la billetera"
          + " sube en el total REDONDEADO a la moneda")
  void pagaYAbona() throws Exception {
    // El lote suma 3,3350 con cuatro decimales: la billetera recibe 3,34.
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "3.33");
    UUID lote = pendiente(1);
    jdbc.update("UPDATE commission_batches SET total_amount = 3.3350 WHERE id = ?", lote);

    mvc.perform(pagar(lote).with(como("commission-batches:pay")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAGADO"))
        .andExpect(jsonPath("$.paidAt").isNotEmpty())
        .andExpect(jsonPath("$.movementId").isNotEmpty())
        .andExpect(jsonPath("$.paidAmount").value(3.34));

    assertThat(billeteraDe(agente)).isEqualByComparingTo("3.34");
  }

  @Test
  @DisplayName("CA-CM-190 — un lote ABIERTO responde 409 y no se abona nada")
  void abiertoNo() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    confirmar(venta(1));
    UUID lote = loteDe(agente);

    mvc.perform(pagar(lote).with(como("commission-batches:pay")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));

    assertThat(billeteraDe(agente)).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("CA-CM-191 — un lote YA PAGADO responde 409 y no se abona otra vez")
  void yaPagado() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    UUID lote = pendiente(1);
    mvc.perform(pagar(lote).with(como("commission-batches:pay"))).andExpect(status().isOk());

    mvc.perform(pagar(lote).with(como("commission-batches:pay")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    assertThat(billeteraDe(agente)).isEqualByComparingTo("10");
  }

  @Test
  @DisplayName(
      "CA-CM-192 — dos pagos simultáneos: uno paga, el otro 409, y la billetera sube UNA vez")
  void dosPagosALaVez() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    UUID lote = pendiente(1);

    var resultados = ConcurrencyHarness.runTogether(2, i -> pago.pay(lote));

    assertThat(ConcurrencyHarness.exitos(resultados)).isEqualTo(1);
    assertThat(billeteraDe(agente)).isEqualByComparingTo("10");
  }

  @Test
  @DisplayName("CA-CM-193 — si el abono FALLA, el lote sigue PENDIENTE y no queda movimiento")
  void siFallaNoPaga() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    UUID lote = pendiente(1);
    // Un total negativo: `MV` rechaza la orden (`RF-MV-024` `EX-001`). Se
    // salta el CHECK del lote solo para esta prueba.
    jdbc.execute("ALTER TABLE commission_batches DROP CONSTRAINT ck_commission_batches_total");
    try {
      jdbc.update("UPDATE commission_batches SET total_amount = -1 WHERE id = ?", lote);

      try {
        pago.pay(lote);
      } catch (RuntimeException esperado) {
        // El abono falló, y con él la transacción entera.
      }
    } finally {
      jdbc.update("UPDATE commission_batches SET total_amount = 10 WHERE id = ?", lote);
      jdbc.execute(
          "ALTER TABLE commission_batches ADD CONSTRAINT ck_commission_batches_total"
              + " CHECK (total_amount >= 0)");
    }

    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM commission_batches WHERE id = ?", String.class, lote))
        .isEqualTo("PENDIENTE");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM movements WHERE idempotency_key = ?",
                Integer.class,
                "lote-" + lote))
        .isZero();
  }

  @Test
  @DisplayName("CA-CM-194 — un lote que redondea a CERO se paga, con su movimiento de importe cero")
  void ceroSePaga() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "0.00");
    UUID lote = pendiente(1);

    mvc.perform(pagar(lote).with(como("commission-batches:pay")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paidAmount").value(0.0))
        .andExpect(jsonPath("$.movementId").isNotEmpty());
  }

  @Test
  @DisplayName("CA-CM-195 — un lote que no existe responde 404; sin permiso, 403")
  void inexistenteYPermiso() throws Exception {
    mvc.perform(pagar(UUID.randomUUID()).with(como("commission-batches:pay")))
        .andExpect(status().isNotFound());
    mvc.perform(pagar(UUID.randomUUID()).with(como("commission-batches:read")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("CA-CM-196 — queda AUDITADO, con el lote, el importe y el movimiento")
  void auditado() throws Exception {
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    UUID lote = pendiente(1);

    mvc.perform(pagar(lote).with(como("commission-batches:pay"))).andExpect(status().isOk());

    String cambios =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE entity = 'commission_batches' AND"
                + " entity_id = ? ORDER BY occurred_at DESC LIMIT 1",
            String.class,
            lote);
    assertThat(cambios).contains("PAGADO").contains("paid_amount").contains("movement_id");
  }

  // ---------------------------------------------------------------------------

  /** Un lote PENDIENTE del agente: devenga una venta y cierra. */
  private UUID pendiente(int cantidad) throws Exception {
    confirmar(venta(cantidad));
    cierre.closeManually(agente);
    return loteDe(agente);
  }

  private UUID venta(int cantidad) {
    return SettlementFixtures.venta(
        jdbc, cliente, VENDIDA_EL, linea(producto, agente, cantidad, "100.00"));
  }

  private void confirmar(UUID venta) throws Exception {
    mvc.perform(
            post(
                    "/api/v1/movements/payments/{id}/confirmation",
                    PaymentFixtures.pagoAConciliar(jdbc, venta))
                .with(como("movements:confirm-payment")))
        .andExpect(status().isOk());
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pagar(
      UUID lote) {
    return post("/api/v1/commission-batches/{id}/payment", lote);
  }

  private UUID loteDe(UUID persona) {
    return jdbc.queryForObject(
        "SELECT id FROM commission_batches WHERE user_id = ? ORDER BY period_start DESC LIMIT 1",
        UUID.class,
        persona);
  }

  private BigDecimal billeteraDe(UUID persona) {
    return jdbc
        .queryForList(
            "SELECT balance FROM accounts WHERE user_id = ? AND kind = 'BILLETERA'",
            BigDecimal.class,
            persona)
        .stream()
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }

  private static RequestPostProcessor como(String permiso) {
    return user(UUID.randomUUID().toString()).authorities(() -> permiso);
  }
}
