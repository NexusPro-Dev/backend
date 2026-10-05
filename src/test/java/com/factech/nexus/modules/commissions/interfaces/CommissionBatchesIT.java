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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Los lotes para administración (`RF-CM-010`, `CA-CM-181` a `CA-CM-188`) y los propios
 * (`RF-CM-012`, `CA-CM-197` a `CA-CM-202`). La cadena: el agente vende y el director es su
 * superior; las comisiones nacen confirmando ventas por la API de `MV`.
 */
@AutoConfigureMockMvc
class CommissionBatchesIT extends IntegrationTestBase {

  private static final OffsetDateTime VENDIDA_EL =
      OffsetDateTime.of(2026, 9, 10, 15, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CloseCommissionPeriodService cierre;
  @Autowired private SessionFactory sessionFactory;

  private UUID cliente;
  private UUID agente;
  private UUID director;
  private UUID producto;

  @BeforeEach
  void sembrar() {
    SettlementFixtures.limpiar(jdbc);
    cliente = SettlementFixtures.persona(jdbc, "cliente", null);
    agente = SettlementFixtures.persona(jdbc, "agente", AGENTE);
    director = SettlementFixtures.persona(jdbc, "director", DIRECTOR);
    SettlementFixtures.superior(jdbc, agente, director);
    producto = SettlementFixtures.producto(jdbc, "BOT", "100.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, AGENTE, "10.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, producto, DIRECTOR, "5.00");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    SettlementFixtures.limpiar(jdbc);
  }

  // ---------------------------------------------------------------------------
  // RF-CM-010
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-CM-181 — el listado trae los lotes de TODAS las personas, con su total y su número de"
          + " comisiones")
  void listaTodos() throws Exception {
    confirmar(venta(1));

    mvc.perform(get("/api/v1/commission-batches").with(como("commission-batches:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.sort").value("periodStart,desc"))
        .andExpect(jsonPath("$.content[?(@.user.id == '%s')].totalAmount", agente).value(10.0))
        .andExpect(jsonPath("$.content[?(@.user.id == '%s')].commissionsCount", director).value(1))
        .andExpect(jsonPath("$.content[0].currency.code").value("USD"));
  }

  @Test
  @DisplayName("CA-CM-182 — filtra por estado, persona, moneda y fechas, combinables")
  void filtra() throws Exception {
    confirmar(venta(1));
    cierre.closeManually(agente);
    confirmar(venta(2));

    mvc.perform(
            get("/api/v1/commission-batches")
                .param("status", "PENDIENTE")
                .param("userId", agente.toString())
                .param("currencyId", SettlementFixtures.USD)
                .param("from", "2026-01-01T00:00:00Z")
                .with(como("commission-batches:read")))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].totalAmount").value(10.0))
        .andExpect(jsonPath("$.content[0].periodEnd").isNotEmpty());
  }

  @Test
  @DisplayName("CA-CM-183 — un lote ABIERTO sale sin fin de periodo y con su total AL DÍA")
  void abiertoAlDia() throws Exception {
    confirmar(venta(1));
    mvc.perform(
            get("/api/v1/commission-batches")
                .param("userId", agente.toString())
                .with(como("commission-batches:read")))
        .andExpect(jsonPath("$.content[0].status").value("ABIERTO"))
        .andExpect(jsonPath("$.content[0].periodEnd").doesNotExist())
        .andExpect(jsonPath("$.content[0].totalAmount").value(10.0));

    confirmar(venta(2));

    mvc.perform(
            get("/api/v1/commission-batches")
                .param("userId", agente.toString())
                .with(como("commission-batches:read")))
        .andExpect(jsonPath("$.content[0].totalAmount").value(30.0));
  }

  @Test
  @DisplayName(
      "CA-CM-184 y CA-CM-185 — el detalle trae cada comisión con lo que aplicó, y muestra lo"
          + " COPIADO aunque la tasa se corrija después")
  void detalle() throws Exception {
    UUID venta = venta(1);
    confirmar(venta);
    UUID lote = loteDe(agente);
    jdbc.update(
        "UPDATE commission_rates SET percentage = 50.00 WHERE role_id = CAST(? AS uuid)", AGENTE);

    mvc.perform(
            get("/api/v1/commission-batches/{id}", lote)
                .with(como("commission-batches:read-detail")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.commissions.length()").value(1))
        .andExpect(jsonPath("$.commissions[0].movementId").value(venta.toString()))
        .andExpect(jsonPath("$.commissions[0].movementCode").isNotEmpty())
        .andExpect(jsonPath("$.commissions[0].productId").value(producto.toString()))
        .andExpect(jsonPath("$.commissions[0].productName").value("Producto BOT"))
        .andExpect(jsonPath("$.commissions[0].chainLevel").value(0))
        .andExpect(jsonPath("$.commissions[0].source").value("ROL"))
        .andExpect(jsonPath("$.commissions[0].rateType").value("PORCENTAJE"))
        .andExpect(jsonPath("$.commissions[0].percentage").value(10.0))
        .andExpect(jsonPath("$.commissions[0].unitPrice").value(100.0))
        .andExpect(jsonPath("$.commissions[0].commissionAmount").value(10.0))
        .andExpect(jsonPath("$.commissions[0].resolvedOn").value("2026-09-10"))
        .andExpect(jsonPath("$.commissions[0].accruedAt").isNotEmpty());
  }

  @Test
  @DisplayName("CA-CM-186 — un lote que no existe responde 404")
  void inexistente() throws Exception {
    mvc.perform(
            get("/api/v1/commission-batches/{id}", UUID.randomUUID())
                .with(como("commission-batches:read-detail")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("CA-CM-187 — los filtros inválidos se rechazan TODOS juntos")
  void filtrosInvalidos() throws Exception {
    mvc.perform(
            get("/api/v1/commission-batches")
                .param("status", "CERRADO")
                .param("from", "2026-10-02T00:00:00Z")
                .param("to", "2026-10-01T00:00:00Z")
                .with(como("commission-batches:read")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(2));
  }

  @Test
  @DisplayName(
      "CA-CM-188 — sin el permiso de cada operación, 403; y las lecturas NO hacen una consulta"
          + " por fila")
  void permisosYSentencias() throws Exception {
    confirmar(venta(1));
    mvc.perform(get("/api/v1/commission-batches").with(como("commission-batches:read-detail")))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/commission-batches/{id}", loteDe(agente))
                .with(como("commission-batches:read")))
        .andExpect(status().isForbidden());

    long conDos = sentenciasDelListado();
    confirmar(venta(1));
    SettlementFixtures.superior(jdbc, SettlementFixtures.persona(jdbc, "otro", AGENTE), director);
    long conMas = sentenciasDelListado();

    assertThat(conMas).isEqualTo(conDos);
  }

  // ---------------------------------------------------------------------------
  // RF-CM-012
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-CM-197 — mis lotes son SOLO los míos, de todas mis monedas y estados")
  void soloLosMios() throws Exception {
    confirmar(venta(1));

    mvc.perform(
            get("/api/v1/commission-batches/mine")
                .with(propio(agente, "commission-batches:list-own")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].user.id").value(agente.toString()));
  }

  @Test
  @DisplayName("CA-CM-198 — tras confirmarse una venta mía, mi lote abierto ya la incluye")
  void creceConCadaVenta() throws Exception {
    confirmar(venta(1));
    confirmar(venta(4));

    mvc.perform(
            get("/api/v1/commission-batches/mine")
                .with(propio(agente, "commission-batches:list-own")))
        .andExpect(jsonPath("$.content[0].status").value("ABIERTO"))
        .andExpect(jsonPath("$.content[0].totalAmount").value(50.0));
  }

  @Test
  @DisplayName(
      "CA-CM-199 — un superior ve en SU lote lo que cobra por la venta de su subordinado, con su"
          + " nivel")
  void elSuperiorVeLoSuyo() throws Exception {
    confirmar(venta(1));
    UUID suyo = loteDe(director);

    mvc.perform(
            get("/api/v1/commission-batches/mine/{id}", suyo)
                .with(propio(director, "commission-batches:read-own")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.commissions[0].chainLevel").value(1))
        .andExpect(jsonPath("$.commissions[0].commissionAmount").value(5.0));
  }

  @Test
  @DisplayName("CA-CM-200 — el detalle propio tiene la forma del de administración")
  void mismaForma() throws Exception {
    confirmar(venta(1));
    UUID mio = loteDe(agente);

    mvc.perform(
            get("/api/v1/commission-batches/mine/{id}", mio)
                .with(propio(agente, "commission-batches:read-own")))
        .andExpect(jsonPath("$.id").value(mio.toString()))
        .andExpect(jsonPath("$.code").isNotEmpty())
        .andExpect(jsonPath("$.commissions[0].rateId").isNotEmpty());
  }

  @Test
  @DisplayName("CA-CM-201 — el lote AJENO responde 404, igual que uno que no existe")
  void elAjenoNoExiste() throws Exception {
    confirmar(venta(1));

    mvc.perform(
            get("/api/v1/commission-batches/mine/{id}", loteDe(director))
                .with(propio(agente, "commission-batches:read-own")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "CA-CM-202 — sin el permiso de cada operación, 403; un rol vendedor lo porta desde su"
          + " siembra")
  void permisosPropios() throws Exception {
    mvc.perform(
            get("/api/v1/commission-batches/mine")
                .with(propio(agente, "commission-batches:read-own")))
        .andExpect(status().isForbidden());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM role_permissions rp JOIN permissions p ON p.id ="
                    + " rp.permission_id WHERE rp.role_id = CAST(? AS uuid) AND p.code IN"
                    + " ('commission-batches:list-own', 'commission-batches:read-own')",
                Integer.class,
                AGENTE))
        .isEqualTo(2);
  }

  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-CM-271 y CA-CM-272 — la venta propia de un superior sale con fuente DIRECTA y su tasa de"
          + " rol como tasa, en el detalle y en su lote propio")
  void laFuenteDirecta() throws Exception {
    // Desde el 05-10-2026 la directa es de la tasa de rol (`RN-CM-050`), y
    // `rateId` apunta a ella.
    jdbc.update(
        "UPDATE commission_rates SET direct_rate_type = 'PORCENTAJE', direct_percentage = 8"
            + " WHERE product_id = ? AND role_id = CAST(? AS uuid)",
        producto,
        DIRECTOR);
    UUID tasaDelDirector =
        jdbc.queryForObject(
            "SELECT id FROM commission_rates WHERE product_id = ? AND role_id = CAST(? AS uuid)",
            UUID.class,
            producto,
            DIRECTOR);
    confirmar(
        SettlementFixtures.venta(
            jdbc, cliente, VENDIDA_EL, linea(producto, director, 1, "100.00")));
    UUID suyo = loteDe(director);

    mvc.perform(
            get("/api/v1/commission-batches/{id}", suyo)
                .with(como("commission-batches:read-detail")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.commissions[0].chainLevel").value(0))
        .andExpect(jsonPath("$.commissions[0].source").value("DIRECTA"))
        .andExpect(jsonPath("$.commissions[0].rateId").value(tasaDelDirector.toString()))
        .andExpect(jsonPath("$.commissions[0].commissionAmount").value(8.0));
    mvc.perform(
            get("/api/v1/commission-batches/mine/{id}", suyo)
                .with(propio(director, "commission-batches:read-own")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.commissions[0].source").value("DIRECTA"));
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

  private long sentenciasDelListado() throws Exception {
    Statistics estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
    mvc.perform(get("/api/v1/commission-batches").with(como("commission-batches:read")))
        .andExpect(status().isOk());
    return estadisticas.getPrepareStatementCount();
  }

  private UUID loteDe(UUID persona) {
    return jdbc.queryForObject(
        "SELECT id FROM commission_batches WHERE user_id = ? ORDER BY period_start DESC LIMIT 1",
        UUID.class,
        persona);
  }

  private static RequestPostProcessor como(String permiso) {
    return user(UUID.randomUUID().toString()).authorities(() -> permiso);
  }

  private static RequestPostProcessor propio(UUID persona, String permiso) {
    return user(persona.toString()).authorities(() -> permiso);
  }
}
