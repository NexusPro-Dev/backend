package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.AGENTE;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.importe;
import static com.factech.nexus.modules.commissions.interfaces.SettlementFixtures.linea;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.commissions.domain.service.CloseCommissionPeriodService;
import com.factech.nexus.modules.commissions.domain.service.PayCommissionBatchService;
import com.factech.nexus.modules.commissions.domain.service.WithdrawCommissionService;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.testing.ConcurrencyHarness;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Retirar una comisión de un lote pendiente (`RF-CM-022`, `CA-CM-273` a `CA-CM-281`, y desde el
 * 07-10-2026 `CA-CM-363` a `CA-CM-365`: el pendiente que se vacía se borra). Los lotes nacen
 * devengando por la API de `MV` y cerrando de verdad; la suite no es transaccional porque el
 * devengo es {@code AFTER_COMMIT}.
 */
@AutoConfigureMockMvc
class WithdrawCommissionIT extends IntegrationTestBase {

  private static final OffsetDateTime VENDIDA_EL =
      OffsetDateTime.of(2026, 9, 10, 15, 0, 0, 0, ZoneOffset.UTC);
  private static final String RETIRAR = "commission-batches:withdraw-commission";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CloseCommissionPeriodService cierre;
  @Autowired private PayCommissionBatchService pago;
  @Autowired private WithdrawCommissionService retiro;

  private UUID cliente;
  private UUID agente;
  private UUID producto;

  @BeforeEach
  void sembrar() {
    SettlementFixtures.limpiar(jdbc);
    cliente = SettlementFixtures.persona(jdbc, "wd-cliente", null);
    agente = SettlementFixtures.persona(jdbc, "wd-agente", AGENTE);
    producto = SettlementFixtures.producto(jdbc, "WD", "100.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    SettlementFixtures.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "CA-CM-273 — retirar la pone en el ABIERTO con su importe, su tasa y su devengo intactos y el"
          + " origen anotado; el pendiente baja y el abierto sube")
  void retira() throws Exception {
    UUID pendiente = pendienteConDos();
    confirmar(venta(1)); // un abierto que ya existe, con 10
    UUID abierto = abiertoDe(agente);
    UUID comision = unaComisionDe(pendiente);
    var antes = jdbc.queryForMap("SELECT * FROM commissions WHERE id = ?", comision);

    mvc.perform(retirar(pendiente, comision).with(como(RETIRAR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(pendiente.toString()))
        .andExpect(jsonPath("$.totalAmount").value(10.0));

    var despues = jdbc.queryForMap("SELECT * FROM commissions WHERE id = ?", comision);
    assertThat(despues.get("batch_id")).isEqualTo(abierto);
    assertThat(despues.get("withdrawn_from_batch_id")).isEqualTo(pendiente);
    assertThat(despues.get("commission_amount")).isEqualTo(antes.get("commission_amount"));
    assertThat(despues.get("rate_id")).isEqualTo(antes.get("rate_id"));
    assertThat(despues.get("accrued_at")).isEqualTo(antes.get("accrued_at"));
    assertThat(total(pendiente)).isEqualByComparingTo("10");
    assertThat(total(abierto)).isEqualByComparingTo("20");
  }

  @Test
  @DisplayName(
      "CA-CM-274 — sin lote abierto en esa moneda, retirar abre uno con la comisión dentro")
  void abreElAbierto() throws Exception {
    UUID pendiente = pendienteConDos();
    assertThat(abiertos(agente)).isZero();

    mvc.perform(retirar(pendiente, unaComisionDe(pendiente)).with(como(RETIRAR)))
        .andExpect(status().isOk());

    UUID abierto = abiertoDe(agente);
    assertThat(total(abierto)).isEqualByComparingTo("10");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM commissions WHERE batch_id = ?", Integer.class, abierto))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("CA-CM-275 — una comisión AFFTRACK se retira igual, y su liquidación no cambia")
  void afftrack() throws Exception {
    UUID pendiente = pendienteConDos();
    UUID liquidacion = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO afftrack_settlements (id, closing_id, user_id, product_id, carried_in,"
            + " new_ftds, paid_ftds, carried_out, source, threshold_rate_id, created_at)"
            + " SELECT ?, closing_id, user_id, ?, 0, 1, 1, 0, 'ROL', ?, now()"
            + " FROM commission_batches WHERE id = ?",
        liquidacion,
        producto,
        UUID.randomUUID(),
        pendiente);
    UUID escalon = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO commissions (id, batch_id, commission_kind, afftrack_settlement_id, user_id,"
            + " source, rate_id, resolved_on, rate_type, fixed_amount, quantity,"
            + " commission_amount, accrued_at, created_at)"
            + " VALUES (?, ?, 'POR_AFFTRACK', ?, ?, 'ROL', ?, DATE '2026-09-30', 'FIJO', 500, 1, 500,"
            + " now(), now())",
        escalon,
        pendiente,
        liquidacion,
        agente,
        UUID.randomUUID());
    jdbc.update(
        "UPDATE commission_batches SET total_amount = total_amount + 500 WHERE id = ?", pendiente);
    var antes = jdbc.queryForMap("SELECT * FROM afftrack_settlements WHERE id = ?", liquidacion);

    mvc.perform(retirar(pendiente, escalon).with(como(RETIRAR))).andExpect(status().isOk());

    assertThat(total(pendiente)).isEqualByComparingTo("20");
    assertThat(total(abiertoDe(agente))).isEqualByComparingTo("5");
    assertThat(jdbc.queryForMap("SELECT * FROM afftrack_settlements WHERE id = ?", liquidacion))
        .isEqualTo(antes);
  }

  @Test
  @DisplayName(
      "CA-CM-276 — la respuesta es el pendiente como queda, con la retirada entre sus retiradas y"
          + " el lote en que está")
  void laRespuesta() throws Exception {
    UUID pendiente = pendienteConDos();
    UUID comision = unaComisionDe(pendiente);

    mvc.perform(retirar(pendiente, comision).with(como(RETIRAR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.commissions.length()").value(1))
        .andExpect(jsonPath("$.withdrawn.length()").value(1))
        .andExpect(jsonPath("$.withdrawn[0].commission.id").value(comision.toString()))
        .andExpect(
            jsonPath("$.withdrawn[0].commission.withdrawnFrom.id").value(pendiente.toString()))
        .andExpect(jsonPath("$.withdrawn[0].currentBatch.id").value(abiertoDe(agente).toString()))
        .andExpect(jsonPath("$.withdrawn[0].currentStatus").value("ABIERTO"))
        .andExpect(jsonPath("$.withdrawn[0].returnable").value(true));
  }

  @Test
  @DisplayName("CA-CM-277 — un lote ABIERTO o PAGADO responde 409, y nada se mueve")
  void abiertoOPagado() throws Exception {
    confirmar(venta(1));
    UUID abierto = abiertoDe(agente);
    mvc.perform(retirar(abierto, unaComisionDe(abierto)).with(como(RETIRAR)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));

    cierre.closeManually(agente);
    pago.pay(abierto);
    UUID comision = unaComisionDe(abierto);
    mvc.perform(retirar(abierto, comision).with(como(RETIRAR)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
    assertThat(loteDe(comision)).isEqualTo(abierto);
  }

  @Test
  @DisplayName("CA-CM-278 — una comisión de otro lote o inexistente responde 404")
  void ajenaOInexistente() throws Exception {
    UUID pendiente = pendienteConDos();
    confirmar(venta(1));
    UUID deOtro = unaComisionDe(abiertoDe(agente));

    mvc.perform(retirar(pendiente, deOtro).with(como(RETIRAR)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("comisión")));
    mvc.perform(retirar(pendiente, UUID.randomUUID()).with(como(RETIRAR)))
        .andExpect(status().isNotFound());
    mvc.perform(retirar(UUID.randomUUID(), deOtro).with(como(RETIRAR)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("lote")));
  }

  @Test
  @DisplayName(
      "CA-CM-363 — retirar la ÚLTIMA borra el pendiente: no hay detalle ni pago (404), y la"
          + " respuesta es el abierto con la comisión")
  void laUltimaBorraElPendiente() throws Exception {
    UUID pendiente = pendienteConDos();
    List<UUID> comisiones = comisionesDe(pendiente);

    mvc.perform(retirar(pendiente, comisiones.get(0)).with(como(RETIRAR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(pendiente.toString()));
    UUID abierto = abiertoDe(agente);
    mvc.perform(retirar(pendiente, comisiones.get(1)).with(como(RETIRAR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(abierto.toString()))
        .andExpect(jsonPath("$.status").value("ABIERTO"))
        .andExpect(jsonPath("$.totalAmount").value(20.0))
        .andExpect(jsonPath("$.commissions.length()").value(2));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM commission_batches WHERE id = ?", Integer.class, pendiente))
        .isZero();
    mvc.perform(
            get("/api/v1/commission-batches/{id}", pendiente)
                .with(como("commission-batches:read-detail")))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/commission-batches/{id}/payment", pendiente)
                .with(como("commission-batches:pay")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "CA-CM-364 — todo lo retirado del pendiente borrado pierde su origen: nada es devolvible, y"
          + " devolverlo responde 404")
  void loRetiradoPierdeSuOrigen() throws Exception {
    UUID pendiente = pendienteConDos();
    List<UUID> comisiones = comisionesDe(pendiente);
    for (UUID comision : comisiones) {
      mvc.perform(retirar(pendiente, comision).with(como(RETIRAR))).andExpect(status().isOk());
    }

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM commissions WHERE withdrawn_from_batch_id IS NOT NULL"
                    + " AND id IN (?, ?)",
                Integer.class,
                comisiones.get(0),
                comisiones.get(1)))
        .isZero();
    assertThat(comisionesDe(abiertoDe(agente))).containsExactlyInAnyOrderElementsOf(comisiones);
    mvc.perform(
            post(
                    "/api/v1/commission-batches/{id}/commissions/{commissionId}/return",
                    pendiente,
                    comisiones.get(0))
                .with(como("commission-batches:return-commission")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "CA-CM-365 — el borrado queda AUDITADO como eliminación física con lo que era el lote; un"
          + " pendiente al que le queda alguna comisión no se borra")
  void elBorradoSeAudita() throws Exception {
    UUID pendiente = pendienteConDos();
    String codigo =
        jdbc.queryForObject(
            "SELECT code FROM commission_batches WHERE id = ?", String.class, pendiente);
    List<UUID> comisiones = comisionesDe(pendiente);

    mvc.perform(retirar(pendiente, comisiones.get(0)).with(como(RETIRAR)))
        .andExpect(status().isOk());
    assertThat(estado(pendiente)).isEqualTo("PENDIENTE");
    assertThat(borrados(pendiente)).isZero();

    mvc.perform(retirar(pendiente, comisiones.get(1)).with(como(RETIRAR)))
        .andExpect(status().isOk());
    assertThat(borrados(pendiente)).isEqualTo(1);
    var fila =
        jdbc.queryForMap(
            "SELECT module, deletion_type, reason, snapshot::text AS snapshot"
                + " FROM audit_deletion_log WHERE entity = 'commission_batches' AND entity_id = ?",
            pendiente);
    assertThat(fila.get("module")).isEqualTo("CM");
    assertThat(fila.get("deletion_type")).isEqualTo("PHYSICAL");
    assertThat((String) fila.get("reason")).contains("RN-CM-052");
    assertThat((String) fila.get("snapshot"))
        .contains(codigo)
        .contains(agente.toString())
        .contains("PENDIENTE")
        .contains("period_end");
  }

  @Test
  @DisplayName(
      "CA-CM-301 — un pendiente vacío de ANTES del 07-10-2026 sigue ahí, y pagarlo responde 409")
  void elVacioDeAntesNoSePaga() throws Exception {
    UUID pendiente = pendienteConDos();
    // Retirar ya lo borraría (`RN-CM-052`): el vacío de antes se fabrica por SQL.
    jdbc.update("DELETE FROM commissions WHERE batch_id = ?", pendiente);
    jdbc.update("UPDATE commission_batches SET total_amount = 0 WHERE id = ?", pendiente);

    mvc.perform(
            post("/api/v1/commission-batches/{id}/payment", pendiente)
                .with(como("commission-batches:pay")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-005"));
  }

  @Test
  @DisplayName(
      "CA-CM-280 — retirar y pagar a la vez: nunca se abona una comisión que también está en el"
          + " abierto")
  void retirarYPagarALaVez() throws Exception {
    UUID pendiente = pendienteConDos();
    UUID comision = unaComisionDe(pendiente);

    ConcurrencyHarness.runTogether(
        List.of(() -> retiro.withdraw(pendiente, comision), () -> pago.pay(pendiente)));

    BigDecimal abonado = billeteraDe(agente);
    UUID dondeEsta = loteDe(comision);
    if (dondeEsta.equals(pendiente)) {
      // El pago llegó antes: se abonaron las dos, y la comisión no se movió.
      assertThat(abonado).isEqualByComparingTo("20");
    } else {
      // El retiro llegó antes: se abonó solo la que quedó.
      assertThat(abonado).isEqualByComparingTo("10");
      assertThat(estado(dondeEsta)).isEqualTo("ABIERTO");
    }
    assertThat(estado(pendiente)).isEqualTo("PAGADO");
  }

  @Test
  @DisplayName(
      "CA-CM-281 — sin commission-batches:withdraw-commission, 403; y queda AUDITADO con la"
          + " comisión, los dos lotes y el importe")
  void permisoYAuditoria() throws Exception {
    UUID pendiente = pendienteConDos();
    UUID comision = unaComisionDe(pendiente);

    mvc.perform(retirar(pendiente, comision).with(como("commission-batches:pay")))
        .andExpect(status().isForbidden());
    mvc.perform(retirar(pendiente, comision).with(como(RETIRAR))).andExpect(status().isOk());

    String cambios =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE entity = 'commissions' AND"
                + " entity_id = ? ORDER BY occurred_at DESC LIMIT 1",
            String.class,
            comision);
    assertThat(cambios)
        .contains(pendiente.toString())
        .contains(abiertoDe(agente).toString())
        .contains("withdrawn_from_batch_id")
        .contains("commission_amount");
  }

  // ---------------------------------------------------------------------------

  /** Un lote PENDIENTE del agente con dos comisiones de 10: dos ventas devengadas y un cierre. */
  private UUID pendienteConDos() throws Exception {
    confirmar(venta(1));
    confirmar(venta(1));
    cierre.closeManually(agente);
    return jdbc.queryForObject(
        "SELECT id FROM commission_batches WHERE user_id = ? AND status = 'PENDIENTE'",
        UUID.class,
        agente);
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

  private static MockHttpServletRequestBuilder retirar(UUID lote, UUID comision) {
    return post(
        "/api/v1/commission-batches/{id}/commissions/{commissionId}/withdrawal", lote, comision);
  }

  private UUID abiertoDe(UUID persona) {
    return jdbc.queryForObject(
        "SELECT id FROM commission_batches WHERE user_id = ? AND status = 'ABIERTO'",
        UUID.class,
        persona);
  }

  private int abiertos(UUID persona) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM commission_batches WHERE user_id = ? AND status = 'ABIERTO'",
        Integer.class,
        persona);
  }

  private List<UUID> comisionesDe(UUID lote) {
    return jdbc.queryForList(
        "SELECT id FROM commissions WHERE batch_id = ? ORDER BY id", UUID.class, lote);
  }

  private UUID unaComisionDe(UUID lote) {
    return comisionesDe(lote).get(0);
  }

  private UUID loteDe(UUID comision) {
    return jdbc.queryForObject(
        "SELECT batch_id FROM commissions WHERE id = ?", UUID.class, comision);
  }

  private BigDecimal total(UUID lote) {
    return importe(
        jdbc.queryForObject(
            "SELECT total_amount FROM commission_batches WHERE id = ?", Long.class, lote));
  }

  private String estado(UUID lote) {
    return jdbc.queryForObject(
        "SELECT status FROM commission_batches WHERE id = ?", String.class, lote);
  }

  private int borrados(UUID lote) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM audit_deletion_log WHERE entity = 'commission_batches' AND"
            + " entity_id = ?",
        Integer.class,
        lote);
  }

  private BigDecimal billeteraDe(UUID persona) {
    return jdbc
        .queryForList(
            "SELECT balance FROM accounts WHERE user_id = ? AND kind = 'BILLETERA'",
            Long.class,
            persona)
        .stream()
        .findFirst()
        .map(CommissionFixtures::importe)
        .orElse(BigDecimal.ZERO);
  }

  private static RequestPostProcessor como(String permiso) {
    return user(UUID.randomUUID().toString()).authorities(() -> permiso);
  }
}
