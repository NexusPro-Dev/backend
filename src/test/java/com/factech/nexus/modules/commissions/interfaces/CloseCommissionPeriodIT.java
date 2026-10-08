package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.AGENTE;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.DIRECTOR;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.importe;
import static com.factech.nexus.modules.commissions.interfaces.SettlementFixtures.linea;
import static com.factech.nexus.modules.commissions.interfaces.SettlementFixtures.lineaDe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.commissions.application.CommissionClosingResponse;
import com.factech.nexus.modules.commissions.domain.service.CloseCommissionPeriodService;
import com.factech.nexus.modules.commissions.domain.service.CommissionAccrualService;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.shared.persistence.MinorUnits;
import com.factech.nexus.testing.ConcurrencyHarness;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * El cierre del periodo (`RF-CM-009`, `CA-CM-170` a `CA-CM-178` y `CA-CM-180`).
 *
 * <p>El programado se invoca por el servicio con un turno fijo —esperar al reloj no prueba nada—, y
 * el manual por la API. Las comisiones nacen <b>confirmando ventas por la API de `MV`</b>, como en
 * {@code CommissionAccrualIT}.
 *
 * <p><b>Desde el 08-10-2026 el programado paga lo que cierra</b> salvo elección manual
 * (`RN-CM-053`, `CA-CM-393` a `CA-CM-398`). El turno de las pruebas de antes, el 01-10-2026, se
 * siembra con elección <b>manual</b>, que es lo que el cierre hacía hasta entonces; los de pago
 * usan el turno siguiente, sin elección.
 */
@AutoConfigureMockMvc
class CloseCommissionPeriodIT extends IntegrationTestBase {

  private static final OffsetDateTime VENDIDA_EL =
      OffsetDateTime.of(2026, 9, 10, 15, 0, 0, 0, ZoneOffset.UTC);

  /** El turno de las pruebas de antes del 08-10-2026, elegido manual en la siembra. */
  private static final String OCTUBRE = "2026-10-01T05:00:00Z";

  /** Un turno sin elección: el cierre paga. */
  private static final String NOVIEMBRE = "2026-11-01T05:00:00Z";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CloseCommissionPeriodService cierre;
  @Autowired private CommissionAccrualService devengo;

  private UUID cliente;
  private UUID agente;
  private UUID director;
  private UUID producto;
  private UUID finanzas;

  @BeforeEach
  void sembrar() {
    SettlementFixtures.limpiar(jdbc);
    cliente = SettlementFixtures.persona(jdbc, "cliente", null);
    agente = SettlementFixtures.persona(jdbc, "agente", AGENTE);
    director = SettlementFixtures.persona(jdbc, "director", DIRECTOR);
    finanzas = SettlementFixtures.persona(jdbc, "finanzas", null);
    SettlementFixtures.superior(jdbc, agente, director);
    producto = SettlementFixtures.producto(jdbc, "BOT", "100.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, DIRECTOR, "5.00");
    elegir(OCTUBRE, "MANUAL");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    SettlementFixtures.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "CA-CM-170 — el cierre pasa TODOS los lotes abiertos a PENDIENTE, con el instante del cierre"
          + " como fin y el cierre que los cerró; su total no cambia")
  void cierraLosAbiertos() throws Exception {
    confirmar(venta(1));

    CommissionClosingResponse hecho = cierre.closeScheduled(turno("2026-10-01T05:00:00Z")).get();

    assertThat(hecho.batchesClosed()).isEqualTo(2);
    List<Map<String, Object>> lotes = lotes();
    assertThat(lotes).hasSize(2);
    assertThat(lotes)
        .allSatisfy(
            l -> {
              assertThat(l.get("status")).isEqualTo("PENDIENTE");
              assertThat(l.get("closing_id")).isEqualTo(hecho.id());
              assertThat(l.get("period_end")).isNotNull();
            });
    assertThat(totalDe(agente, "PENDIENTE")).isEqualByComparingTo("10");
    assertThat(totalDe(director, "PENDIENTE")).isEqualByComparingTo("5");
  }

  @Test
  @DisplayName(
      "CA-CM-171 — lo que devenga DESPUÉS del cierre abre un lote NUEVO, que empieza después del"
          + " fin del cerrado")
  void despuesDelCierreOtroLote() throws Exception {
    confirmar(venta(1));
    cierre.closeScheduled(turno("2026-10-01T05:00:00Z"));

    confirmar(venta(2));

    Map<String, Object> cerrado = loteDe(agente, "PENDIENTE");
    Map<String, Object> nuevo = loteDe(agente, "ABIERTO");
    assertThat(importe(nuevo.get("total_amount"))).isEqualByComparingTo("20");
    assertThat(instante(nuevo.get("period_start")))
        .isAfterOrEqualTo(instante(cerrado.get("period_end")));
  }

  @Test
  @DisplayName(
      "CA-CM-172 — el barrido devenga una línea SIN desenlace y su comisión entra en el lote que"
          + " este cierre cierra")
  void barreLoPerdido() {
    // Confirmada por SQL: el aviso no llegó nunca, como si la aplicación
    // hubiese caído justo después del commit.
    UUID venta = venta(1);
    jdbc.update(
        "UPDATE movements SET status = 'CONFIRMADA', confirmed_at = now() WHERE id = ?", venta);

    CommissionClosingResponse hecho = cierre.closeScheduled(turno("2026-10-01T05:00:00Z")).get();

    assertThat(hecho.linesSwept()).isEqualTo(1);
    assertThat(totalDe(agente, "PENDIENTE")).isEqualByComparingTo("10");
  }

  @Test
  @DisplayName(
      "CA-CM-173 — el barrido reintenta una RECHAZADA cuya tasa se corrigió y devenga en este"
          + " cierre; una SIN_COMISION no se reintenta")
  void reintentaLasRechazadas() throws Exception {
    UUID otro = SettlementFixtures.producto(jdbc, "OTRO", "100.00");
    UUID caro = SettlementFixtures.producto(jdbc, "CARO", "100.00");
    UUID tasaCara = CommissionFixtures.sembrarTasaDeRol(jdbc, caro, AGENTE, "90.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, caro, DIRECTOR, "20.00");
    UUID sinTasa =
        SettlementFixtures.venta(jdbc, cliente, VENDIDA_EL, linea(otro, agente, 1, "100.00"));
    UUID rechazada =
        SettlementFixtures.venta(jdbc, cliente, VENDIDA_EL, linea(caro, agente, 1, "100.00"));
    confirmar(sinTasa);
    confirmar(rechazada);
    CommissionFixtures.sembrarTasaDeRol(jdbc, otro, AGENTE, "10.00");
    jdbc.update("UPDATE commission_rates SET percentage = 70.00 WHERE id = ?", tasaCara);

    CommissionClosingResponse hecho = cierre.closeScheduled(turno("2026-10-01T05:00:00Z")).get();

    assertThat(hecho.linesRetried()).isEqualTo(1);
    assertThat(hecho.linesRecovered()).isEqualTo(1);
    assertThat(desenlace(lineaDe(jdbc, rechazada))).isEqualTo("DEVENGADA");
    assertThat(desenlace(lineaDe(jdbc, sinTasa))).isEqualTo("SIN_COMISION");
    assertThat(totalDe(agente, "PENDIENTE")).isEqualByComparingTo("70");
  }

  @Test
  @DisplayName(
      "CA-CM-174 — dos instancias disparando el MISMO turno producen UN cierre, y ningún lote con"
          + " periodo vacío")
  void unTurnoUnCierre() throws Exception {
    confirmar(venta(1));
    OffsetDateTime elTurno = turno("2026-10-01T05:00:00Z");

    List<Outcome<Optional<CommissionClosingResponse>>> resultados =
        ConcurrencyHarness.runTogether(2, i -> cierre.closeScheduled(elTurno));

    assertThat(resultados).allSatisfy(r -> assertThat(r.succeeded()).isTrue());
    assertThat(resultados.stream().filter(r -> r.value().isPresent()).count()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM commission_closings WHERE scheduled_for = ?",
                Integer.class,
                elTurno))
        .isEqualTo(1);
    assertThat(lotes()).hasSize(2);
  }

  @Test
  @DisplayName("CA-CM-175 — un cierre sin nada abierto deja constancia con CERO lotes")
  void cierreVacio() {
    CommissionClosingResponse hecho = cierre.closeScheduled(turno("2026-10-01T05:00:00Z")).get();

    assertThat(hecho.batchesClosed()).isZero();
    assertThat(hecho.closedAt()).isNotNull();
    assertThat(hecho.origin()).isEqualTo("PROGRAMADO");
  }

  @Test
  @DisplayName(
      "CA-CM-176 — el cierre a mano hace lo mismo, deja constancia de QUIÉN y la devuelve; sin"
          + " permiso, 403")
  void cierreAMano() throws Exception {
    confirmar(venta(1));

    mvc.perform(
            post("/api/v1/commission-batches/closing")
                .with(como(finanzas, "commission-batches:settle")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.origin").value("MANUAL"))
        .andExpect(jsonPath("$.triggeredBy").value(finanzas.toString()))
        .andExpect(jsonPath("$.batchesClosed").value(2))
        .andExpect(jsonPath("$.closedAt").isNotEmpty());

    mvc.perform(
            post("/api/v1/commission-batches/closing")
                .with(como(finanzas, "commission-batches:read")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/commission-batches/closing")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("CA-CM-177 — un cierre a mano con otro en curso responde 409 y no cierra nada")
  void otroEnCurso() throws Exception {
    confirmar(venta(1));
    // El cierre en curso es otra transacción que tiene el bloqueo tomado.
    jdbc.execute(
        (org.springframework.jdbc.core.ConnectionCallback<Object>)
            conexion -> {
              conexion.setAutoCommit(false);
              try (var sentencia = conexion.createStatement()) {
                sentencia.execute("SELECT pg_advisory_xact_lock(4309, 0)");
                mvc.perform(
                        post("/api/v1/commission-batches/closing")
                            .with(como(finanzas, "commission-batches:settle")))
                    .andExpect(status().isConflict());
              } catch (Exception e) {
                throw new IllegalStateException(e);
              } finally {
                conexion.rollback();
                conexion.setAutoCommit(true);
              }
              return null;
            });

    assertThat(lotes()).allSatisfy(l -> assertThat(l.get("status")).isEqualTo("ABIERTO"));
  }

  @Test
  @DisplayName(
      "CA-CM-178 — una comisión que devenga MIENTRAS se cierra acaba en el lote cerrado o en uno"
          + " nuevo, nunca en ninguno ni en los dos; los totales cuadran")
  void devengoYCierreALaVez() throws Exception {
    confirmar(venta(1));
    UUID otra = venta(3);
    jdbc.update(
        "UPDATE movements SET status = 'CONFIRMADA', confirmed_at = now() WHERE id = ?", otra);
    UUID linea = lineaDe(jdbc, otra);

    ConcurrencyHarness.runTogether(
        List.of(
            () -> cierre.closeScheduled(turno("2026-10-01T05:00:00Z")),
            () -> devengo.accrue(List.of(linea))));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM commissions WHERE movement_detail_id = ?",
                Integer.class,
                linea))
        .isEqualTo(2);
    BigDecimal lotes =
        importe(
            jdbc.queryForObject("SELECT sum(total_amount) FROM commission_batches", Long.class));
    BigDecimal comisiones =
        importe(jdbc.queryForObject("SELECT sum(commission_amount) FROM commissions", Long.class));
    assertThat(lotes).isEqualByComparingTo(comisiones);
    assertThat(lotes).isEqualByComparingTo("60");
  }

  @Test
  @DisplayName(
      "CA-CM-180 — los cierres se listan del más reciente al más antiguo, filtrables; filtros"
          + " inválidos juntos; sin commission-closings:read, 403")
  void consultarLosCierres() throws Exception {
    CommissionClosingResponse primero = cierre.closeScheduled(turno("2026-10-01T05:00:00Z")).get();
    CommissionClosingResponse segundo = cierre.closeManually(finanzas);

    mvc.perform(get("/api/v1/commission-closings").with(como(finanzas, "commission-closings:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(segundo.id().toString()))
        .andExpect(jsonPath("$.content[1].id").value(primero.id().toString()))
        .andExpect(jsonPath("$.sort").value("closedAt,desc"));
    mvc.perform(
            get("/api/v1/commission-closings")
                .param("origin", "PROGRAMADO")
                .with(como(finanzas, "commission-closings:read")))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].scheduledFor").isNotEmpty());
    mvc.perform(
            get("/api/v1/commission-closings")
                .param("origin", "NUNCA")
                .param("from", "2026-10-02T00:00:00Z")
                .param("to", "2026-10-01T00:00:00Z")
                .with(como(finanzas, "commission-closings:read")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(2));
    mvc.perform(get("/api/v1/commission-closings").with(como(finanzas, "commission-batches:read")))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------------------
  // El pago del cierre (`RN-CM-053`, 08-10-2026)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-CM-393 — un cierre programado SIN elección paga cada lote que cerró: PAGADO, con su abono"
          + " en la billetera, y el cierre registra AUTOMATICO y cuántos pagó")
  void sinEleccionPaga() throws Exception {
    confirmar(venta(1));

    CommissionClosingResponse hecho = cierre.closeScheduled(turno(NOVIEMBRE)).get();

    assertThat(hecho.paymentMode()).isEqualTo("AUTOMATICO");
    assertThat(hecho.batchesClosed()).isEqualTo(2);
    assertThat(hecho.batchesPaid()).isEqualTo(2);
    assertThat(hecho.batchesNotPaid()).isZero();
    assertThat(lotes()).allSatisfy(l -> assertThat(l.get("status")).isEqualTo("PAGADO"));
    assertThat(billeteraDe(agente)).isEqualByComparingTo("10");
    assertThat(billeteraDe(director)).isEqualByComparingTo("5");
  }

  @Test
  @DisplayName(
      "CA-CM-394 — con MANUAL elegido para su turno, los lotes se quedan PENDIENTE y nada se abona;"
          + " el cierre registra MANUAL y cero pagados")
  void manualNoPaga() throws Exception {
    confirmar(venta(1));

    CommissionClosingResponse hecho = cierre.closeScheduled(turno(OCTUBRE)).get();

    assertThat(hecho.paymentMode()).isEqualTo("MANUAL");
    assertThat(hecho.batchesPaid()).isZero();
    assertThat(lotes()).allSatisfy(l -> assertThat(l.get("status")).isEqualTo("PENDIENTE"));
    assertThat(billeteraDe(agente)).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("CA-CM-395 — el pago automático NO paga los PENDIENTE de cierres anteriores")
  void soloLosDeEseCierre() throws Exception {
    confirmar(venta(1));
    CommissionClosingResponse octubre = cierre.closeScheduled(turno(OCTUBRE)).get();
    confirmar(venta(2));

    CommissionClosingResponse noviembre = cierre.closeScheduled(turno(NOVIEMBRE)).get();

    assertThat(noviembre.batchesPaid()).isEqualTo(2);
    assertThat(estadosDelCierre(octubre.id())).containsOnly("PENDIENTE").hasSize(2);
    assertThat(estadosDelCierre(noviembre.id())).containsOnly("PAGADO").hasSize(2);
    assertThat(billeteraDe(agente)).isEqualByComparingTo("20");
  }

  @Test
  @DisplayName(
      "CA-CM-396 — un lote que NO se puede pagar se queda PENDIENTE y los demás se pagan; el"
          + " cierre registra cuántos no")
  void unoQueNoSePaga() throws Exception {
    confirmar(venta(1));
    // Una restricción que solo existe en esta prueba rechaza pagar el lote del director, como el
    // fallo provocado de `CA-CM-166`; se retira al acabar.
    jdbc.execute(
        "ALTER TABLE commission_batches ADD CONSTRAINT ck_prueba_pago_falla CHECK (status <>"
            + " 'PAGADO' OR user_id <> '"
            + director
            + "') NOT VALID");
    CommissionClosingResponse hecho;
    try {
      hecho = cierre.closeScheduled(turno(NOVIEMBRE)).get();
    } finally {
      jdbc.execute("ALTER TABLE commission_batches DROP CONSTRAINT IF EXISTS ck_prueba_pago_falla");
    }

    assertThat(hecho.batchesPaid()).isEqualTo(1);
    assertThat(hecho.batchesNotPaid()).isEqualTo(1);
    assertThat(loteDe(agente, "PAGADO")).isNotNull();
    assertThat(loteDe(director, "PENDIENTE")).isNotNull();
    assertThat(billeteraDe(director)).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName(
      "CA-CM-397 — el cierre A MANO no paga, aunque no haya elección; su fila no tiene modo")
  void aManoNoPaga() throws Exception {
    confirmar(venta(1));

    CommissionClosingResponse hecho = cierre.closeManually(finanzas);

    assertThat(hecho.paymentMode()).isNull();
    assertThat(hecho.batchesPaid()).isZero();
    assertThat(lotes()).allSatisfy(l -> assertThat(l.get("status")).isEqualTo("PENDIENTE"));
  }

  @Test
  @DisplayName("CA-CM-398 — la elección de un turno NO vale para el siguiente")
  void laEleccionNoSeArrastra() throws Exception {
    CommissionClosingResponse octubre = cierre.closeScheduled(turno(OCTUBRE)).get();
    confirmar(venta(1));

    CommissionClosingResponse noviembre = cierre.closeScheduled(turno(NOVIEMBRE)).get();

    assertThat(octubre.paymentMode()).isEqualTo("MANUAL");
    assertThat(noviembre.paymentMode()).isEqualTo("AUTOMATICO");
    assertThat(noviembre.batchesPaid()).isEqualTo(2);
  }

  // ---------------------------------------------------------------------------

  private UUID venta(int cantidad) {
    return SettlementFixtures.venta(
        jdbc, cliente, VENDIDA_EL, linea(producto, agente, cantidad, "100.00"));
  }

  private void confirmar(UUID venta) throws Exception {
    mvc.perform(
            post(
                    "/api/v1/movements/payments/{id}/confirmation",
                    PaymentFixtures.pagoAConciliar(jdbc, venta))
                .with(como(finanzas, "movements:confirm-payment")))
        .andExpect(status().isOk());
  }

  private static RequestPostProcessor como(UUID persona, String permiso) {
    return user(persona.toString()).authorities(() -> permiso);
  }

  private static OffsetDateTime turno(String instante) {
    return OffsetDateTime.parse(instante);
  }

  /** La elección de un turno, escrita como la deja `RF-CM-029`. */
  private void elegir(String elTurno, String modo) {
    jdbc.update(
        "INSERT INTO commission_payment_choices"
            + " (id, scheduled_for, payment_mode, chosen_by, chosen_at, created_at)"
            + " VALUES (?, ?, ?, ?, now(), now())",
        UUID.randomUUID(),
        turno(elTurno),
        modo,
        finanzas);
  }

  private List<String> estadosDelCierre(UUID cierreId) {
    return jdbc.queryForList(
        "SELECT status FROM commission_batches WHERE closing_id = ?", String.class, cierreId);
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

  private List<Map<String, Object>> lotes() {
    return jdbc.queryForList(
        "SELECT b.* FROM commission_batches b JOIN users u ON u.id = b.user_id"
            + " WHERE u.username LIKE 'st-%'");
  }

  private Map<String, Object> loteDe(UUID persona, String estado) {
    return jdbc.queryForMap(
        "SELECT * FROM commission_batches WHERE user_id = ? AND status = ?", persona, estado);
  }

  private BigDecimal totalDe(UUID persona, String estado) {
    return MinorUnits.fromMinor(loteDe(persona, estado).get("total_amount"));
  }

  private String desenlace(UUID linea) {
    return jdbc.queryForObject(
        "SELECT outcome FROM commission_accruals WHERE movement_detail_id = ?",
        String.class,
        linea);
  }

  private static OffsetDateTime instante(Object valor) {
    if (valor instanceof OffsetDateTime odt) {
      return odt;
    }
    return ((java.sql.Timestamp) valor).toInstant().atOffset(ZoneOffset.UTC);
  }
}
