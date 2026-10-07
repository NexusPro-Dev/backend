package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.AGENTE;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.importe;
import static com.factech.nexus.modules.commissions.interfaces.SettlementFixtures.linea;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.commissions.domain.service.CloseCommissionPeriodService;
import com.factech.nexus.modules.commissions.domain.service.PayCommissionBatchService;
import com.factech.nexus.modules.commissions.domain.service.ReturnCommissionService;
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
 * Devolver a su lote pendiente una comisión retirada (`RF-CM-023`, `CA-CM-282` a `CA-CM-289`),
 * sobre retiros hechos por la ruta de `RF-CM-022`.
 */
@AutoConfigureMockMvc
class ReturnCommissionIT extends IntegrationTestBase {

  private static final OffsetDateTime VENDIDA_EL =
      OffsetDateTime.of(2026, 9, 10, 15, 0, 0, 0, ZoneOffset.UTC);
  private static final String RETIRAR = "commission-batches:withdraw-commission";
  private static final String DEVOLVER = "commission-batches:return-commission";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CloseCommissionPeriodService cierre;
  @Autowired private PayCommissionBatchService pago;
  @Autowired private ReturnCommissionService devolucion;

  private UUID cliente;
  private UUID agente;
  private UUID producto;

  @BeforeEach
  void sembrar() {
    SettlementFixtures.limpiar(jdbc);
    cliente = SettlementFixtures.persona(jdbc, "rt-cliente", null);
    agente = SettlementFixtures.persona(jdbc, "rt-agente", AGENTE);
    producto = SettlementFixtures.producto(jdbc, "RT", "100.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    SettlementFixtures.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "CA-CM-282 — devolver la pone otra vez en su pendiente, sin origen anotado y con su importe;"
          + " el abierto baja y el pendiente sube")
  void devuelve() throws Exception {
    UUID pendiente = pendienteConDos();
    UUID comision = unaComisionDe(pendiente);
    retirar(pendiente, comision);
    confirmar(venta()); // que el abierto no se quede vacío (`RN-CM-052`)
    UUID abierto = abiertoDe(agente);

    mvc.perform(devolver(pendiente, comision).with(como(DEVOLVER))).andExpect(status().isOk());

    var fila = jdbc.queryForMap("SELECT * FROM commissions WHERE id = ?", comision);
    assertThat(fila.get("batch_id")).isEqualTo(pendiente);
    assertThat(fila.get("withdrawn_from_batch_id")).isNull();
    assertThat(importe(fila.get("commission_amount"))).isEqualByComparingTo("10");
    assertThat(total(pendiente)).isEqualByComparingTo("20");
    assertThat(total(abierto)).isEqualByComparingTo("10");
  }

  @Test
  @DisplayName(
      "CA-CM-283 — la respuesta es el pendiente como queda, con la comisión entre las suyas y"
          + " fuera de las retiradas")
  void laRespuesta() throws Exception {
    UUID pendiente = pendienteConDos();
    UUID comision = unaComisionDe(pendiente);
    retirar(pendiente, comision);

    mvc.perform(devolver(pendiente, comision).with(como(DEVOLVER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.commissions.length()").value(2))
        .andExpect(jsonPath("$.withdrawn.length()").value(0))
        .andExpect(jsonPath("$.totalAmount").value(20.0));
  }

  @Test
  @DisplayName(
      "CA-CM-366 — devolver la ÚNICA comisión de un abierto lo borra, auditado; la respuesta es el"
          + " pendiente, y lo siguiente que devenga esa persona abre otro")
  void elAbiertoVacioSeBorra() throws Exception {
    UUID pendiente = pendienteConDos();
    UUID comision = unaComisionDe(pendiente);
    retirar(pendiente, comision); // abre el abierto con ella sola
    UUID abierto = abiertoDe(agente);

    mvc.perform(devolver(pendiente, comision).with(como(DEVOLVER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(pendiente.toString()))
        .andExpect(jsonPath("$.totalAmount").value(20.0));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM commission_batches WHERE id = ?", Integer.class, abierto))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT snapshot::text FROM audit_deletion_log WHERE entity = 'commission_batches'"
                    + " AND entity_id = ? AND deletion_type = 'PHYSICAL'",
                String.class,
                abierto))
        .contains("ABIERTO")
        .contains(agente.toString());

    confirmar(venta());
    assertThat(abiertoDe(agente)).isNotEqualTo(abierto);
  }

  @Test
  @DisplayName(
      "CA-CM-285 — devolver a un lote del que no salió, o una comisión nacida en el abierto,"
          + " responde 404 y nada se mueve")
  void noSalioDeAhi() throws Exception {
    UUID pendiente = pendienteConDos();
    confirmar(venta());
    UUID nacidaEnElAbierto = unaComisionDe(abiertoDe(agente));

    mvc.perform(devolver(pendiente, nacidaEnElAbierto).with(como(DEVOLVER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("comisión")));
    assertThat(loteDe(nacidaEnElAbierto)).isEqualTo(abiertoDe(agente));

    UUID dePendiente = unaComisionDe(pendiente);
    mvc.perform(devolver(UUID.randomUUID(), dePendiente).with(como(DEVOLVER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("lote")));
  }

  @Test
  @DisplayName("CA-CM-286 — si el origen está PAGADO, 409 y la comisión se queda en el abierto")
  void origenPagado() throws Exception {
    UUID pendiente = pendienteConDos();
    UUID comision = unaComisionDe(pendiente);
    retirar(pendiente, comision);
    pago.pay(pendiente);

    mvc.perform(devolver(pendiente, comision).with(como(DEVOLVER)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
    assertThat(loteDe(comision)).isEqualTo(abiertoDe(agente));
  }

  @Test
  @DisplayName(
      "CA-CM-287 — si el abierto se CERRÓ después del retiro, 409 y se queda en su nuevo pendiente")
  void abiertoCerrado() throws Exception {
    UUID pendiente = pendienteConDos();
    List<UUID> comisiones = comisionesDe(pendiente);
    retirar(pendiente, comisiones.get(0)); // la otra se queda: un pendiente vacío se borra
    UUID abierto = abiertoDe(agente);

    cierre.closeManually(agente);

    mvc.perform(devolver(pendiente, comisiones.get(0)).with(como(DEVOLVER)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
    assertThat(loteDe(comisiones.get(0))).isEqualTo(abierto);
    assertThat(estado(abierto)).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName(
      "CA-CM-288 — devolver y cerrar a la vez: la comisión acaba en un solo lote, y los totales"
          + " cuadran con las comisiones")
  void devolverYCerrarALaVez() throws Exception {
    UUID pendiente = pendienteConDos();
    UUID comision = unaComisionDe(pendiente);
    retirar(pendiente, comision);
    confirmar(venta()); // que el abierto no se quede vacío si la comisión vuelve

    ConcurrencyHarness.runTogether(
        List.of(
            () -> devolucion.giveBack(pendiente, comision), () -> cierre.closeManually(agente)));

    Integer descuadrados =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM commission_batches b
             WHERE b.user_id = ?
               AND b.total_amount <> (SELECT COALESCE(sum(k.commission_amount), 0)
                                        FROM commissions k
                                       WHERE k.batch_id = b.id)
            """,
            Integer.class,
            agente);
    assertThat(descuadrados).isZero();
    UUID dondeEsta = loteDe(comision);
    assertThat(dondeEsta.equals(pendiente) || !"ABIERTO".equals(estado(dondeEsta))).isTrue();
  }

  @Test
  @DisplayName(
      "CA-CM-289 — sin commission-batches:return-commission, 403 aunque se porte"
          + " withdraw-commission; y queda AUDITADO")
  void permisoYAuditoria() throws Exception {
    UUID pendiente = pendienteConDos();
    UUID comision = unaComisionDe(pendiente);
    retirar(pendiente, comision);

    mvc.perform(devolver(pendiente, comision).with(como(RETIRAR)))
        .andExpect(status().isForbidden());
    mvc.perform(devolver(pendiente, comision).with(como(DEVOLVER))).andExpect(status().isOk());

    String cambios =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE entity = 'commissions' AND"
                + " entity_id = ? ORDER BY occurred_at DESC LIMIT 1",
            String.class,
            comision);
    assertThat(cambios).contains(pendiente.toString()).contains("commission_amount");
  }

  @Test
  @DisplayName(
      "CA-CM-300 — el cierre NO cierra un abierto sin comisiones vivas: sigue abierto, sin fin de"
          + " periodo, y no cuenta entre los cerrados")
  void elAbiertoVacioNoSeCierra() throws Exception {
    // Un abierto vacío de antes del 07-10-2026: devolver ya lo borraría (`RN-CM-052`).
    confirmar(venta());
    UUID abierto = abiertoDe(agente);
    jdbc.update("DELETE FROM commissions WHERE batch_id = ?", abierto);
    jdbc.update("UPDATE commission_batches SET total_amount = 0 WHERE id = ?", abierto);

    var constancia = cierre.closeManually(agente);

    assertThat(estado(abierto)).isEqualTo("ABIERTO");
    assertThat(
            jdbc.queryForObject(
                "SELECT period_end FROM commission_batches WHERE id = ?",
                OffsetDateTime.class,
                abierto))
        .isNull();
    assertThat(constancia.batchesClosed()).isZero();
  }

  // ---------------------------------------------------------------------------

  private UUID pendienteConDos() throws Exception {
    confirmar(venta());
    confirmar(venta());
    cierre.closeManually(agente);
    return jdbc.queryForObject(
        "SELECT id FROM commission_batches WHERE user_id = ? AND status = 'PENDIENTE'",
        UUID.class,
        agente);
  }

  private void retirar(UUID lote, UUID comision) throws Exception {
    mvc.perform(
            post(
                    "/api/v1/commission-batches/{id}/commissions/{commissionId}/withdrawal",
                    lote,
                    comision)
                .with(como(RETIRAR)))
        .andExpect(status().isOk());
  }

  private static MockHttpServletRequestBuilder devolver(UUID lote, UUID comision) {
    return post(
        "/api/v1/commission-batches/{id}/commissions/{commissionId}/return", lote, comision);
  }

  private static MockHttpServletRequestBuilder pagar(UUID lote) {
    return post("/api/v1/commission-batches/{id}/payment", lote)
        .with(como("commission-batches:pay"));
  }

  private UUID venta() {
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

  private UUID abiertoDe(UUID persona) {
    return jdbc.queryForObject(
        "SELECT id FROM commission_batches WHERE user_id = ? AND status = 'ABIERTO'",
        UUID.class,
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

  private static RequestPostProcessor como(String permiso) {
    return user(UUID.randomUUID().toString()).authorities(() -> permiso);
  }
}
