package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.AGENTE;
import static com.factech.nexus.modules.commissions.interfaces.SettlementFixtures.linea;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.commissions.application.CommissionBatchesPaymentResponse;
import com.factech.nexus.modules.commissions.application.PayCommissionBatchesRequest;
import com.factech.nexus.modules.commissions.domain.service.CloseCommissionPeriodService;
import com.factech.nexus.modules.commissions.domain.service.PayCommissionBatchService;
import com.factech.nexus.modules.commissions.domain.service.PayCommissionBatchesService;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.testing.ConcurrencyHarness;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
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

/**
 * Pagar varios lotes de una vez (`RF-CM-025`, `CA-CM-306` a `CA-CM-314`). Cada lote nace devengando
 * y cerrando de verdad, como en {@code PayCommissionBatchIT}; tres agentes dan tres lotes
 * pendientes de tres personas.
 */
@AutoConfigureMockMvc
class PayCommissionBatchesIT extends IntegrationTestBase {

  private static final OffsetDateTime VENDIDA_EL =
      OffsetDateTime.of(2026, 9, 10, 15, 0, 0, 0, ZoneOffset.UTC);
  private static final String PAGAR_VARIOS = "commission-batches:pay-batches";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CloseCommissionPeriodService cierre;
  @Autowired private PayCommissionBatchService pago;
  @Autowired private PayCommissionBatchesService pagoDeVarios;

  private UUID cliente;
  private UUID producto;
  private final List<UUID> agentes = new ArrayList<>();

  @BeforeEach
  void sembrar() {
    SettlementFixtures.limpiar(jdbc);
    cliente = SettlementFixtures.persona(jdbc, "pv-cliente", null);
    producto = SettlementFixtures.producto(jdbc, "PV", "100.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    agentes.clear();
    for (int i = 1; i <= 3; i++) {
      agentes.add(SettlementFixtures.persona(jdbc, "pv-agente" + i, AGENTE));
    }
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    SettlementFixtures.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "CA-CM-306 — una lista de PENDIENTES queda toda pagada, cada lote abonado a su persona con su"
          + " movimiento")
  void pagaTodos() throws Exception {
    List<UUID> lotes = pendientesDeLosTres();

    mvc.perform(pagar(lotes).with(como(PAGAR_VARIOS)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paidCount").value(3))
        .andExpect(jsonPath("$.notPaidCount").value(0))
        .andExpect(jsonPath("$.results[0].paid").value(true))
        .andExpect(jsonPath("$.results[0].paidAmount").value(10.0))
        .andExpect(jsonPath("$.results[0].movementId").isNotEmpty())
        .andExpect(jsonPath("$.results[0].code").isNotEmpty());

    for (UUID agente : agentes) {
      assertThat(billeteraDe(agente)).isEqualByComparingTo("10");
    }
    for (UUID lote : lotes) {
      assertThat(estado(lote)).isEqualTo("PAGADO");
    }
  }

  @Test
  @DisplayName(
      "CA-CM-307 — con un lote abierto, uno ya pagado, uno vacío y uno inexistente, se pagan los"
          + " demás y esos cuatro salen NO pagados con el motivo de RF-CM-011")
  void losQueNoSePuedenNoFrenan() throws Exception {
    List<UUID> pendientes = pendientesDeLosTres();
    UUID yaPagado = pendientes.get(0);
    pago.pay(yaPagado);
    UUID vacio = pendientes.get(1);
    jdbc.update(
        "UPDATE commissions SET reverted_at = now(), reverted_by = ? WHERE batch_id = ?",
        agentes.get(1),
        vacio);
    confirmar(venta(agentes.get(0)));
    UUID abierto = abiertoDe(agentes.get(0));
    UUID inexistente = UUID.randomUUID();
    UUID bueno = pendientes.get(2);

    var respuesta = pagarPorServicio(List.of(abierto, yaPagado, bueno, vacio, inexistente));

    assertThat(respuesta.paidCount()).isEqualTo(1);
    assertThat(
            respuesta.results().stream()
                .collect(
                    Collectors.toMap(r -> r.batchId(), r -> r.paid() ? "PAGADO" : r.reasonCode())))
        .containsEntry(abierto, "EX-002")
        .containsEntry(yaPagado, "EX-003")
        .containsEntry(bueno, "PAGADO")
        .containsEntry(vacio, "EX-005")
        .containsEntry(inexistente, "EX-001");
    assertThat(respuesta.results())
        .allSatisfy(r -> assertThat(r.paid() || r.reason() != null).isTrue());
    assertThat(estado(bueno)).isEqualTo("PAGADO");
    assertThat(estado(abierto)).isEqualTo("ABIERTO");
  }

  @Test
  @DisplayName(
      "CA-CM-308 — si el ABONO de uno falla, ese sigue pendiente y sin movimiento, y los demás se"
          + " pagan")
  void unAbonoQueFalla() throws Exception {
    List<UUID> lotes = pendientesDeLosTres();
    UUID roto = lotes.get(1);
    // Como CA-CM-193: un total negativo, que `MV` rechaza; el CHECK se retira
    // solo durante la prueba.
    jdbc.execute("ALTER TABLE commission_batches DROP CONSTRAINT ck_commission_batches_total");
    CommissionBatchesPaymentResponse respuesta;
    try {
      jdbc.update("UPDATE commission_batches SET total_amount = -1 WHERE id = ?", roto);
      respuesta = pagarPorServicio(lotes);
    } finally {
      jdbc.update("UPDATE commission_batches SET total_amount = 10 WHERE id = ?", roto);
      jdbc.execute(
          "ALTER TABLE commission_batches ADD CONSTRAINT ck_commission_batches_total"
              + " CHECK (total_amount >= 0)");
    }

    assertThat(respuesta.paidCount()).isEqualTo(2);
    assertThat(estado(roto)).isEqualTo("PENDIENTE");
    assertThat(
            jdbc.queryForObject(
                "SELECT movement_id FROM commission_batches WHERE id = ?", UUID.class, roto))
        .isNull();
    assertThat(estado(lotes.get(0))).isEqualTo("PAGADO");
    assertThat(estado(lotes.get(2))).isEqualTo("PAGADO");
  }

  @Test
  @DisplayName(
      "CA-CM-309 — una fila por lote, EN EL ORDEN PEDIDO, y cuántos se pagaron y cuántos no")
  void elOrden() throws Exception {
    List<UUID> lotes = pendientesDeLosTres();
    UUID inexistente = UUID.randomUUID();
    List<UUID> pedidos = List.of(lotes.get(2), inexistente, lotes.get(0), lotes.get(1));

    var respuesta = pagarPorServicio(pedidos);

    assertThat(respuesta.results().stream().map(r -> r.batchId()).toList()).isEqualTo(pedidos);
    assertThat(respuesta.paidCount()).isEqualTo(3);
    assertThat(respuesta.notPaidCount()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "CA-CM-310 — vacía o con repetidos se rechaza con todos los errores juntos y no paga nada;"
          + " una de MÁS DE CIEN se acepta")
  void validaciones() throws Exception {
    mvc.perform(pagar(List.of()).with(como(PAGAR_VARIOS)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-001"));

    List<UUID> lotes = pendientesDeLosTres();
    mvc.perform(
            pagar(List.of(lotes.get(0), lotes.get(1), lotes.get(0), lotes.get(1)))
                .with(como(PAGAR_VARIOS)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
    assertThat(estado(lotes.get(0))).isEqualTo("PENDIENTE");

    List<UUID> cientoCinco = new ArrayList<>(lotes);
    for (int i = 0; i < 102; i++) {
      cientoCinco.add(UUID.randomUUID());
    }
    mvc.perform(pagar(cientoCinco).with(como(PAGAR_VARIOS)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results.length()").value(105))
        .andExpect(jsonPath("$.paidCount").value(3));
  }

  @Test
  @DisplayName(
      "CA-CM-311 — dos listas que comparten un lote, a la vez: se abona UNA vez, y en una sale no"
          + " pagado por ya estar pagado")
  void dosListasALaVez() throws Exception {
    List<UUID> lotes = pendientesDeLosTres();
    UUID compartido = lotes.get(0);

    var resultados =
        ConcurrencyHarness.runTogether(
            List.<java.util.concurrent.Callable<CommissionBatchesPaymentResponse>>of(
                () -> pagarPorServicio(List.of(compartido, lotes.get(1))),
                () -> pagarPorServicio(List.of(compartido, lotes.get(2)))));

    assertThat(billeteraDe(agentes.get(0))).isEqualByComparingTo("10");
    long yaPagado =
        resultados.stream()
            .map(r -> r.value())
            .flatMap(r -> r.results().stream())
            .filter(r -> r.batchId().equals(compartido) && !r.paid())
            .filter(r -> "EX-003".equals(r.reasonCode()))
            .count();
    assertThat(yaPagado).isEqualTo(1);
  }

  @Test
  @DisplayName("CA-CM-312 — si NINGUNO se puede pagar, responde 200 con todos no pagados")
  void ninguno() throws Exception {
    mvc.perform(pagar(List.of(UUID.randomUUID(), UUID.randomUUID())).with(como(PAGAR_VARIOS)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paidCount").value(0))
        .andExpect(jsonPath("$.notPaidCount").value(2))
        .andExpect(jsonPath("$.results[1].reasonCode").value("EX-001"));
  }

  @Test
  @DisplayName("CA-CM-313 — cada lote pagado queda AUDITADO como un pago suelto")
  void auditado() throws Exception {
    List<UUID> lotes = pendientesDeLosTres();

    pagarPorServicio(lotes);

    for (UUID lote : lotes) {
      String cambios =
          jdbc.queryForObject(
              "SELECT changes::text FROM audit_change_log WHERE entity = 'commission_batches' AND"
                  + " entity_id = ? ORDER BY occurred_at DESC LIMIT 1",
              String.class,
              lote);
      assertThat(cambios).contains("PAGADO").contains("paid_amount").contains("movement_id");
    }
  }

  @Test
  @DisplayName(
      "CA-CM-314 — sin commission-batches:pay-batches, 403, también con commission-batches:pay")
  void permiso() throws Exception {
    mvc.perform(pagar(List.of(UUID.randomUUID())).with(como("commission-batches:pay")))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------

  /** Un lote PENDIENTE por agente, de 10 cada uno: una venta por agente y un cierre. */
  private List<UUID> pendientesDeLosTres() throws Exception {
    for (UUID agente : agentes) {
      confirmar(venta(agente));
    }
    cierre.closeManually(agentes.get(0));
    List<UUID> lotes = new ArrayList<>();
    for (UUID agente : agentes) {
      lotes.add(
          jdbc.queryForObject(
              "SELECT id FROM commission_batches WHERE user_id = ? AND status = 'PENDIENTE'",
              UUID.class,
              agente));
    }
    return lotes;
  }

  private CommissionBatchesPaymentResponse pagarPorServicio(List<UUID> lotes) {
    return pagoDeVarios.payAll(new PayCommissionBatchesRequest(lotes));
  }

  private UUID venta(UUID agente) {
    return SettlementFixtures.venta(
        jdbc, cliente, VENDIDA_EL, linea(producto, agente, 1, "100.00"));
  }

  private void confirmar(UUID venta) throws Exception {
    mvc.perform(
            post(
                    "/api/v1/movements/payments/{id}/confirmation",
                    PaymentFixtures.pagoAConciliar(jdbc, venta))
                .with(como("movements:confirm-payment")))
        .andExpect(status().isOk());
  }

  private static MockHttpServletRequestBuilder pagar(List<UUID> lotes) {
    String ids = lotes.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
    return post("/api/v1/commission-batches/payments")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"batchIds\":[" + ids + "]}");
  }

  private UUID abiertoDe(UUID persona) {
    return jdbc.queryForObject(
        "SELECT id FROM commission_batches WHERE user_id = ? AND status = 'ABIERTO'",
        UUID.class,
        persona);
  }

  private String estado(UUID lote) {
    return jdbc.queryForObject(
        "SELECT status FROM commission_batches WHERE id = ?", String.class, lote);
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
