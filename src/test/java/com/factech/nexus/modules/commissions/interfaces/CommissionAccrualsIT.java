package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.AGENTE;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.DIRECTOR;
import static com.factech.nexus.modules.commissions.interfaces.SettlementFixtures.linea;
import static com.factech.nexus.modules.commissions.interfaces.SettlementFixtures.lineaDe;
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

/** El desenlace de las líneas (`RF-CM-014`, `CA-CM-203` a `CA-CM-208`). */
@AutoConfigureMockMvc
class CommissionAccrualsIT extends IntegrationTestBase {

  private static final OffsetDateTime VENDIDA_EL =
      OffsetDateTime.of(2026, 9, 10, 15, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CloseCommissionPeriodService cierre;
  @Autowired private SessionFactory sessionFactory;

  private UUID cliente;
  private UUID agente;
  private UUID caro;
  private UUID sinTasa;
  private UUID tasaCara;

  @BeforeEach
  void sembrar() {
    SettlementFixtures.limpiar(jdbc);
    cliente = SettlementFixtures.persona(jdbc, "cliente", null);
    agente = SettlementFixtures.persona(jdbc, "agente", AGENTE);
    UUID director = SettlementFixtures.persona(jdbc, "director", DIRECTOR);
    SettlementFixtures.superior(jdbc, agente, director);
    caro = SettlementFixtures.producto(jdbc, "CARO", "100.00");
    sinTasa = SettlementFixtures.producto(jdbc, "SIN", "100.00");
    tasaCara = CommissionFixtures.sembrarTasaDeRol(jdbc, caro, AGENTE, "90.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, caro, DIRECTOR, "20.00");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    SettlementFixtures.limpiar(jdbc);
  }

  @Test
  @DisplayName(
      "CA-CM-203 — una línea RECHAZADA sale con su motivo —la suma y el importe— y sus intentos")
  void rechazada() throws Exception {
    UUID venta = venta(caro);
    confirmar(venta);

    mvc.perform(get("/api/v1/commission-accruals").with(como("commission-accruals:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].movementDetailId").value(lineaDe(jdbc, venta).toString()))
        .andExpect(jsonPath("$.content[0].outcome").value("RECHAZADA"))
        .andExpect(
            jsonPath("$.content[0].reason").value(org.hamcrest.Matchers.containsString("110")))
        .andExpect(jsonPath("$.content[0].attempts").value(1))
        .andExpect(jsonPath("$.content[0].productName").value("Producto CARO"))
        .andExpect(jsonPath("$.content[0].sellerId").value(agente.toString()));
  }

  @Test
  @DisplayName(
      "CA-CM-204 — tras corregir la tasa y CERRAR, la misma línea sale DEVENGADA con los intentos"
          + " sumados")
  void recuperada() throws Exception {
    UUID venta = venta(caro);
    confirmar(venta);
    jdbc.update("UPDATE commission_rates SET percentage = 70.00 WHERE id = ?", tasaCara);

    cierre.closeManually(agente);

    mvc.perform(
            get("/api/v1/commission-accruals")
                .param("movementId", venta.toString())
                .with(como("commission-accruals:read")))
        .andExpect(jsonPath("$.content[0].outcome").value("DEVENGADA"))
        .andExpect(jsonPath("$.content[0].reason").doesNotExist())
        .andExpect(jsonPath("$.content[0].attempts").value(2));
  }

  @Test
  @DisplayName("CA-CM-205 — una línea sin tasa en toda su cadena sale SIN_COMISION, sin motivo")
  void sinComision() throws Exception {
    confirmar(venta(sinTasa));

    mvc.perform(
            get("/api/v1/commission-accruals")
                .param("outcome", "SIN_COMISION")
                .with(como("commission-accruals:read")))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].reason").doesNotExist());
  }

  @Test
  @DisplayName(
      "CA-CM-206 — filtra por desenlace, venta, producto y fechas; los filtros inválidos, todos"
          + " juntos")
  void filtros() throws Exception {
    UUID una = venta(caro);
    confirmar(una);
    confirmar(venta(sinTasa));

    mvc.perform(
            get("/api/v1/commission-accruals")
                .param("outcome", "RECHAZADA")
                .param("productId", caro.toString())
                .param("movementId", una.toString())
                .param("from", "2020-01-01T00:00:00Z")
                .with(como("commission-accruals:read")))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(
            get("/api/v1/commission-accruals")
                .param("productId", sinTasa.toString())
                .with(como("commission-accruals:read")))
        .andExpect(jsonPath("$.content[0].outcome").value("SIN_COMISION"));
    mvc.perform(
            get("/api/v1/commission-accruals")
                .param("outcome", "PERDIDA")
                .param("from", "2026-10-02T00:00:00Z")
                .param("to", "2026-10-01T00:00:00Z")
                .with(como("commission-accruals:read")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(2));
  }

  @Test
  @DisplayName("CA-CM-207 — una línea confirmada y con vendedor SIN desenlace todavía no aparece")
  void sinDesenlaceNoSale() throws Exception {
    UUID venta = venta(caro);
    jdbc.update(
        "UPDATE movements SET status = 'CONFIRMADA', confirmed_at = now() WHERE id = ?", venta);

    mvc.perform(get("/api/v1/commission-accruals").with(como("commission-accruals:read")))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("CA-CM-208 — sin commission-accruals:read, 403; y NO una consulta por fila")
  void permisoYSentencias() throws Exception {
    mvc.perform(get("/api/v1/commission-accruals").with(como("commission-batches:read")))
        .andExpect(status().isForbidden());

    confirmar(venta(caro));
    long conUna = sentencias();
    confirmar(venta(sinTasa));
    confirmar(venta(caro));
    long conTres = sentencias();

    assertThat(conTres).isEqualTo(conUna);
  }

  // ---------------------------------------------------------------------------

  private UUID venta(UUID producto) {
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

  private long sentencias() throws Exception {
    Statistics estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
    mvc.perform(get("/api/v1/commission-accruals").with(como("commission-accruals:read")))
        .andExpect(status().isOk());
    return estadisticas.getPrepareStatementCount();
  }

  private static RequestPostProcessor como(String permiso) {
    return user(UUID.randomUUID().toString()).authorities(() -> permiso);
  }
}
