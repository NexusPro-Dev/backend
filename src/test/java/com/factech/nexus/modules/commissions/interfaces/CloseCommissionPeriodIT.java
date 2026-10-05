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
 */
@AutoConfigureMockMvc
class CloseCommissionPeriodIT extends IntegrationTestBase {

  private static final OffsetDateTime VENDIDA_EL =
      OffsetDateTime.of(2026, 9, 10, 15, 0, 0, 0, ZoneOffset.UTC);

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
