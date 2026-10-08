package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.AGENTE;
import static com.factech.nexus.modules.commissions.interfaces.SettlementFixtures.linea;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.commissions.domain.service.CloseCommissionPeriodService;
import com.factech.nexus.modules.commissions.domain.service.DeleteEmptyBatchesService;
import com.factech.nexus.modules.commissions.domain.service.PayCommissionBatchService;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.testing.ConcurrencyHarness;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
 * Borrar los lotes vacíos (`RF-CM-027`, `CA-CM-368` a `CA-CM-375`), y lo que borran solos el cierre
 * (`RF-CM-009`, `CA-CM-376` y `CA-CM-377`) y el pago (`RF-CM-011`, `CA-CM-378` y `CA-CM-379`;
 * `RF-CM-025`, `CA-CM-380`). Los lotes nacen devengando por la API de `MV` y cerrando de verdad, y
 * se vacían <b>retirando y devolviendo</b>, que desde el 08-10-2026 los dejan donde están. La suite
 * no es transaccional porque el devengo es {@code AFTER_COMMIT}.
 */
@AutoConfigureMockMvc
class DeleteEmptyBatchesIT extends IntegrationTestBase {

  private static final OffsetDateTime VENDIDA_EL =
      OffsetDateTime.of(2026, 9, 10, 15, 0, 0, 0, ZoneOffset.UTC);
  private static final String BORRAR = "commission-batches:delete-empty";
  private static final String RUTA = "/api/v1/commission-batches/empty";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CloseCommissionPeriodService cierre;
  @Autowired private PayCommissionBatchService pago;
  @Autowired private DeleteEmptyBatchesService vacios;

  private UUID cliente;
  private UUID agente;
  private UUID otro;
  private UUID producto;

  @BeforeEach
  void sembrar() {
    SettlementFixtures.limpiar(jdbc);
    cliente = SettlementFixtures.persona(jdbc, "eb-cliente", null);
    agente = SettlementFixtures.persona(jdbc, "eb-agente", AGENTE);
    otro = SettlementFixtures.persona(jdbc, "eb-otro", AGENTE);
    producto = SettlementFixtures.producto(jdbc, "EB", "100.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    SettlementFixtures.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "CA-CM-368 — borra TODOS los abiertos y pendientes sin comisiones, de varias personas; los"
          + " que tienen alguna —también de importe cero— y los pagados no cambian")
  void borraLosVacios() throws Exception {
    // Un solo cierre: desde el 08-10-2026 cada cierre borra los abiertos vacíos.
    ventasYCierre(otro, agente);
    // El otro: un pendiente que se paga (por el servicio: no borra) y un abierto
    // que se vació al devolver.
    UUID pagado = pendienteDe(otro);
    UUID comision = comisionesDe(pagado).get(0);
    retirar(pagado, comision);
    UUID abiertoVacio = abiertoDe(otro);
    devolver(pagado, comision);
    pago.pay(pagado);
    // El agente: un pendiente vaciado por retiros, y el abierto con las dos.
    UUID pendienteVacio = pendienteDe(agente);
    for (UUID c : comisionesDe(pendienteVacio)) {
      retirar(pendienteVacio, c);
    }
    UUID abiertoConDos = abiertoDe(agente);
    jdbc.update("UPDATE commissions SET commission_amount = 0 WHERE batch_id = ?", abiertoConDos);
    jdbc.update("UPDATE commission_batches SET total_amount = 0 WHERE id = ?", abiertoConDos);

    mvc.perform(delete(RUTA).with(como(BORRAR))).andExpect(status().isOk());

    assertThat(existe(pendienteVacio)).isFalse();
    assertThat(existe(abiertoVacio)).isFalse();
    assertThat(existe(abiertoConDos)).isTrue();
    assertThat(comisionesDe(abiertoConDos)).hasSize(2);
    assertThat(estado(pagado)).isEqualTo("PAGADO");
  }

  @Test
  @DisplayName(
      "CA-CM-369 — la respuesta trae cada lote borrado con lo que era, y cuántos; después no"
          + " aparece en el listado ni en su detalle")
  void laRespuesta() throws Exception {
    ventasYCierre(otro, agente);
    UUID delOtro = pendienteDe(otro);
    UUID suya = comisionesDe(delOtro).get(0);
    retirar(delOtro, suya);
    UUID abiertoVacio = abiertoDe(otro);
    devolver(delOtro, suya);
    UUID pendiente = pendienteDe(agente);
    for (UUID c : comisionesDe(pendiente)) {
      retirar(pendiente, c);
    }
    UUID abierto = abiertoDe(agente);
    String codigo = codigoDe(pendiente);

    mvc.perform(delete(RUTA).with(como(BORRAR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletedCount").value(2))
        .andExpect(
            jsonPath("$.deleted[*].id")
                .value(containsInAnyOrder(pendiente.toString(), abiertoVacio.toString())))
        .andExpect(jsonPath("$.deleted[?(@.id == '" + pendiente + "')].code").value(codigo))
        .andExpect(
            jsonPath("$.deleted[?(@.id == '" + pendiente + "')].userId").value(agente.toString()))
        .andExpect(jsonPath("$.deleted[?(@.id == '" + pendiente + "')].status").value("PENDIENTE"))
        .andExpect(jsonPath("$.deleted[?(@.id == '" + abiertoVacio + "')].status").value("ABIERTO"))
        .andExpect(
            jsonPath("$.deleted[?(@.id == '" + abiertoVacio + "')].periodEnd").value(empty()));

    mvc.perform(
            get("/api/v1/commission-batches/{id}", pendiente)
                .with(como("commission-batches:read-detail")))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/commission-batches")
                .param("userId", agente.toString())
                .with(como("commission-batches:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(abierto.toString()));
  }

  @Test
  @DisplayName("CA-CM-370 — sin lotes vacíos responde 200, sin ninguno y con cero")
  void ninguno() throws Exception {
    UUID pendiente = pendienteConDos(agente);

    mvc.perform(delete(RUTA).with(como(BORRAR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletedCount").value(0))
        .andExpect(jsonPath("$.deleted.length()").value(0));

    assertThat(existe(pendiente)).isTrue();
  }

  @Test
  @DisplayName(
      "CA-CM-371 — cada borrado queda AUDITADO como eliminación física con lo que era el lote")
  void seAudita() throws Exception {
    UUID pendiente = pendienteConDos(agente);
    for (UUID c : comisionesDe(pendiente)) {
      retirar(pendiente, c);
    }
    String codigo = codigoDe(pendiente);

    mvc.perform(delete(RUTA).with(como(BORRAR))).andExpect(status().isOk());

    var fila =
        jdbc.queryForMap(
            "SELECT module, deletion_type, reason, snapshot::text AS snapshot"
                + " FROM audit_deletion_log WHERE entity = 'commission_batches' AND entity_id = ?",
            pendiente);
    assertThat(fila.get("module")).isEqualTo("CM");
    assertThat(fila.get("deletion_type")).isEqualTo("PHYSICAL");
    assertThat((String) fila.get("reason")).contains("RN-CM-052").contains("RF-CM-027");
    assertThat((String) fila.get("snapshot"))
        .contains(codigo)
        .contains(agente.toString())
        .contains("PENDIENTE")
        .contains("period_end");
  }

  @Test
  @DisplayName(
      "CA-CM-372 — lo retirado de un pendiente borrado pierde su origen y devolverlo responde 404;"
          + " antes del borrado sí se podía devolver")
  void loRetiradoPierdeSuOrigen() throws Exception {
    UUID pendiente = pendienteConDos(agente);
    List<UUID> comisiones = comisionesDe(pendiente);
    for (UUID c : comisiones) {
      retirar(pendiente, c);
    }
    // Vacío y todavía ahí: se le puede devolver, y se retira otra vez.
    devolver(pendiente, comisiones.get(0));
    retirar(pendiente, comisiones.get(0));

    mvc.perform(delete(RUTA).with(como(BORRAR))).andExpect(status().isOk());

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM commissions WHERE withdrawn_from_batch_id IS NOT NULL"
                    + " AND id IN (?, ?)",
                Integer.class,
                comisiones.get(0),
                comisiones.get(1)))
        .isZero();
    assertThat(comisionesDe(abiertoDe(agente))).containsExactlyInAnyOrderElementsOf(comisiones);
    mvc.perform(devolucion(pendiente, comisiones.get(0)).with(como(DEVOLVER)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "CA-CM-373 — tras borrar el abierto vacío, lo siguiente que devenga esa persona abre otro, y"
          + " el cierre lo cierra")
  void elAbiertoSeReabre() throws Exception {
    UUID abierto = abiertoVacioDe(agente);

    mvc.perform(delete(RUTA).with(como(BORRAR))).andExpect(status().isOk());
    assertThat(existe(abierto)).isFalse();

    confirmar(venta(agente));
    UUID nuevo = abiertoDe(agente);
    assertThat(nuevo).isNotEqualTo(abierto);
    assertThat(comisionesDe(nuevo)).hasSize(1);

    cierre.closeManually(agente);
    assertThat(estado(nuevo)).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName(
      "CA-CM-374 — borrar y devengar a la vez sobre el abierto vacío: la comisión acaba SIEMPRE en"
          + " un lote, nunca en ninguno")
  void borrarYDevengarALaVez() throws Exception {
    for (int vuelta = 0; vuelta < 5; vuelta++) {
      if (vuelta > 0) {
        sembrar();
      }
      UUID abierto = abiertoVacioDe(agente);
      UUID venta = venta(agente);
      UUID cobro = PaymentFixtures.pagoAConciliar(jdbc, venta);

      var resultados =
          ConcurrencyHarness.runTogether(
              List.<Callable<Object>>of(
                  () -> vacios.deleteAll(),
                  () ->
                      mvc.perform(
                              post("/api/v1/movements/payments/{id}/confirmation", cobro)
                                  .with(como("movements:confirm-payment")))
                          .andReturn()
                          .getResponse()
                          .getStatus()));

      assertThat(resultados).allSatisfy(r -> assertThat(r.failure()).isNull());
      assertThat(resultados.get(1).value()).isEqualTo(200);
      UUID linea = SettlementFixtures.lineaDe(jdbc, venta);
      List<UUID> lotes =
          jdbc.queryForList(
              "SELECT batch_id FROM commissions WHERE movement_detail_id = ?", UUID.class, linea);
      assertThat(lotes).as("vuelta %d", vuelta).hasSize(1);
      assertThat(existe(lotes.get(0))).isTrue();
      assertThat(estado(lotes.get(0))).isEqualTo("ABIERTO");
      if (existe(abierto)) {
        assertThat(lotes.get(0)).isEqualTo(abierto);
      }
    }
  }

  @Test
  @DisplayName(
      "CA-CM-375 — sin commission-batches:delete-empty se rechaza, también con"
          + " commission-batches:settle, y no se borra nada")
  void permiso() throws Exception {
    UUID abierto = abiertoVacioDe(agente);

    mvc.perform(delete(RUTA).with(como("commission-batches:settle")))
        .andExpect(status().isForbidden());

    assertThat(existe(abierto)).isTrue();
  }

  @Test
  @DisplayName(
      "CA-CM-376, CA-CM-377 — el cierre BORRA los abiertos vacíos, auditados; los abiertos con"
          + " comisiones pasan a pendiente, los pendientes vacíos no se tocan, y la auditoría del"
          + " cierre cuenta los borrados")
  void elCierreBorraLosAbiertosVacios() throws Exception {
    ventasYCierre(agente, otro);
    UUID delAgente = pendienteDe(agente);
    UUID comision = comisionesDe(delAgente).get(0);
    retirar(delAgente, comision);
    UUID abiertoVacio = abiertoDe(agente);
    devolver(delAgente, comision);
    UUID pendienteVacio = pendienteDe(otro);
    for (UUID c : comisionesDe(pendienteVacio)) {
      retirar(pendienteVacio, c);
    }
    UUID abiertoConDos = abiertoDe(otro);

    var constancia = cierre.closeManually(agente);

    assertThat(existe(abiertoVacio)).isFalse();
    assertThat(estado(abiertoConDos)).isEqualTo("PENDIENTE");
    assertThat(existe(pendienteVacio)).isTrue();
    assertThat(motivoDelBorrado(abiertoVacio)).contains("RN-CM-052").contains("cerrar");
    assertThat(
            jdbc.queryForObject(
                "SELECT (changes->'after'->>'empty_batches_deleted')::int FROM audit_change_log"
                    + " WHERE entity = 'commission_closings' AND entity_id = ?",
                Integer.class,
                constancia.id()))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "CA-CM-378 — pagar un lote borra después TODOS los pendientes vacíos, de cualquier persona;"
          + " los abiertos vacíos no, y lo retirado del borrado pierde su origen")
  void elPagoBorraLosPendientesVacios() throws Exception {
    ventasYCierre(agente, otro);
    UUID pendienteVacio = pendienteDe(agente);
    List<UUID> retiradas = comisionesDe(pendienteVacio);
    for (UUID c : retiradas) {
      retirar(pendienteVacio, c);
    }
    UUID aPagar = pendienteDe(otro);
    UUID suya = comisionesDe(aPagar).get(0);
    retirar(aPagar, suya);
    UUID abiertoVacio = abiertoDe(otro);
    devolver(aPagar, suya);

    mvc.perform(post("/api/v1/commission-batches/{id}/payment", aPagar).with(como(PAGAR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(aPagar.toString()));

    assertThat(estado(aPagar)).isEqualTo("PAGADO");
    assertThat(existe(pendienteVacio)).isFalse();
    assertThat(existe(abiertoVacio)).isTrue();
    assertThat(motivoDelBorrado(pendienteVacio)).contains("RN-CM-052").contains("pago");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM commissions WHERE withdrawn_from_batch_id IS NOT NULL"
                    + " AND id IN (?, ?)",
                Integer.class,
                retiradas.get(0),
                retiradas.get(1)))
        .isZero();
  }

  @Test
  @DisplayName("CA-CM-379 — un pago que no se hace (409 o 404) no borra ningún lote")
  void unPagoQueNoSeHaceNoBorra() throws Exception {
    ventasYCierre(agente);
    UUID pendienteVacio = pendienteDe(agente);
    for (UUID c : comisionesDe(pendienteVacio)) {
      retirar(pendienteVacio, c);
    }

    mvc.perform(post("/api/v1/commission-batches/{id}/payment", pendienteVacio).with(como(PAGAR)))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/v1/commission-batches/{id}/payment", UUID.randomUUID()).with(como(PAGAR)))
        .andExpect(status().isNotFound());

    assertThat(existe(pendienteVacio)).isTrue();
  }

  @Test
  @DisplayName(
      "CA-CM-380 — pagar varios borra los pendientes vacíos al final si se pagó alguno; si no se"
          + " pagó ninguno, no borra nada")
  void pagarVariosBorraAlFinal() throws Exception {
    ventasYCierre(agente, otro);
    UUID pendienteVacio = pendienteDe(agente);
    for (UUID c : comisionesDe(pendienteVacio)) {
      retirar(pendienteVacio, c);
    }
    UUID aPagar = pendienteDe(otro);

    mvc.perform(pagarVarios(pendienteVacio))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paidCount").value(0));
    assertThat(existe(pendienteVacio)).isTrue();

    mvc.perform(pagarVarios(aPagar))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paidCount").value(1));
    assertThat(existe(pendienteVacio)).isFalse();
  }

  // ---------------------------------------------------------------------------

  private static final String RETIRAR = "commission-batches:withdraw-commission";
  private static final String DEVOLVER = "commission-batches:return-commission";
  private static final String PAGAR = "commission-batches:pay";

  /** Dos ventas de 10 por persona y UN cierre: un pendiente con dos comisiones para cada una. */
  private void ventasYCierre(UUID... personas) throws Exception {
    for (UUID persona : personas) {
      confirmar(venta(persona));
      confirmar(venta(persona));
    }
    cierre.closeManually(agente);
  }

  private UUID pendienteDe(UUID persona) {
    return jdbc.queryForObject(
        "SELECT id FROM commission_batches WHERE user_id = ? AND status = 'PENDIENTE'",
        UUID.class,
        persona);
  }

  private String motivoDelBorrado(UUID lote) {
    return jdbc.queryForObject(
        "SELECT reason FROM audit_deletion_log WHERE entity = 'commission_batches'"
            + " AND entity_id = ?",
        String.class,
        lote);
  }

  private static MockHttpServletRequestBuilder pagarVarios(UUID lote) {
    return post("/api/v1/commission-batches/payments")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"batchIds\": [\"" + lote + "\"]}")
        .with(como("commission-batches:pay-batches"));
  }

  /** Un lote PENDIENTE de esa persona con dos comisiones de 10: dos ventas y un cierre. */
  private UUID pendienteConDos(UUID persona) throws Exception {
    confirmar(venta(persona));
    confirmar(venta(persona));
    cierre.closeManually(agente);
    return jdbc.queryForObject(
        "SELECT id FROM commission_batches WHERE user_id = ? AND status = 'PENDIENTE'",
        UUID.class,
        persona);
  }

  /** Un ABIERTO vacío: se retira una comisión de un pendiente, que lo abre, y se devuelve. */
  private UUID abiertoVacioDe(UUID persona) throws Exception {
    UUID pendiente = pendienteConDos(persona);
    UUID comision = comisionesDe(pendiente).get(0);
    retirar(pendiente, comision);
    UUID abierto = abiertoDe(persona);
    devolver(pendiente, comision);
    assertThat(comisionesDe(abierto)).isEmpty();
    return abierto;
  }

  private UUID venta(UUID vendedor) {
    return SettlementFixtures.venta(
        jdbc, cliente, VENDIDA_EL, linea(producto, vendedor, 1, "100.00"));
  }

  private void confirmar(UUID venta) throws Exception {
    mvc.perform(
            post(
                    "/api/v1/movements/payments/{id}/confirmation",
                    PaymentFixtures.pagoAConciliar(jdbc, venta))
                .with(como("movements:confirm-payment")))
        .andExpect(status().isOk());
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

  private void devolver(UUID lote, UUID comision) throws Exception {
    mvc.perform(devolucion(lote, comision).with(como(DEVOLVER))).andExpect(status().isOk());
  }

  private static MockHttpServletRequestBuilder devolucion(UUID lote, UUID comision) {
    return post(
        "/api/v1/commission-batches/{id}/commissions/{commissionId}/return", lote, comision);
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

  private boolean existe(UUID lote) {
    return jdbc.queryForObject(
        "SELECT EXISTS (SELECT 1 FROM commission_batches WHERE id = ?)", Boolean.class, lote);
  }

  private String estado(UUID lote) {
    return jdbc.queryForObject(
        "SELECT status FROM commission_batches WHERE id = ?", String.class, lote);
  }

  private String codigoDe(UUID lote) {
    return jdbc.queryForObject(
        "SELECT code FROM commission_batches WHERE id = ?", String.class, lote);
  }

  private static RequestPostProcessor como(String permiso) {
    return user(UUID.randomUUID().toString()).authorities(() -> permiso);
  }
}
