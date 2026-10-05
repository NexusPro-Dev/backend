package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.AGENTE;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.MANAGER;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.centesimas;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.importe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.testing.CommissionCleanup;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La liquidación afftrack dentro del cierre (`RF-CM-020`), y los criterios de la configuración que
 * necesitan un cierre (`CA-CM-222`, `CA-CM-227`, `CA-CM-228`).
 *
 * <p><b>Se cierra por la API</b> ({@code POST /commission-batches/closing}), para que la
 * liquidación corra de verdad dentro de la transacción del cierre. Las ventas FTD se siembran por
 * SQL ya confirmadas y entregadas —la entrega es lo que la activación escribe (`RN-CM-036`)—, con
 * el prefijo de {@link SettlementFixtures}.
 */
@AutoConfigureMockMvc
class AfftrackSettlementIT extends IntegrationTestBase {

  private static final OffsetDateTime ACTIVADA = OffsetDateTime.now().minusDays(3);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private org.hibernate.SessionFactory sessionFactory;

  private UUID ftd;
  private UUID cliente;
  private UUID vendedora;

  @BeforeEach
  void preparar() {
    limpiar();
    reponerElSuelo(jdbc);
    ftd = AfftrackFixtures.productoFtd(jdbc, "FTD", false);
    cliente = SettlementFixtures.persona(jdbc, "af-cliente", null);
    vendedora = SettlementFixtures.persona(jdbc, "af-vendedora", MANAGER);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  private void limpiar() {
    jdbc.execute("ALTER TABLE commissions DROP CONSTRAINT IF EXISTS ck_prueba_afftrack_falla");
    CommissionCleanup.limpiar(jdbc);
    jdbc.update("DELETE FROM user_afftrack_rates");
    jdbc.update("DELETE FROM afftrack_rates");
    SettlementFixtures.limpiar(jdbc);
    AfftrackFixtures.limpiar(jdbc);
  }

  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-CM-241 · 55 FTD con escalones de 50 y 60: paga 50 × valor y quedan 5")
  void cincuentaYCinco() throws Exception {
    escalonDeRol(MANAGER, 50, "8000");
    escalonDeRol(MANAGER, 60, "9000");
    ftds(vendedora, 55);

    cerrar();

    assertThat(ultima(vendedora))
        .containsEntry("carried_in", 0)
        .containsEntry("new_ftds", 55)
        .containsEntry("paid_ftds", 50)
        .containsEntry("carried_out", 5);
    Map<String, Object> comision = comisionAfftrack(vendedora);
    assertThat(comision.get("quantity")).isEqualTo(50);
    assertThat(importe(comision.get("fixed_amount"))).isEqualByComparingTo("8000");
    assertThat(importe(comision.get("commission_amount"))).isEqualByComparingTo("400000");
    assertThat(comision.get("status")).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName(
      "CA-CM-242 · en el cierre siguiente, sin FTD nuevos, el remanente alcanza un escalón")
  void elRemanenteAlcanza() throws Exception {
    escalonDeRol(MANAGER, 50, "8000");
    ftds(vendedora, 55);
    cerrar();
    escalonDeRol(MANAGER, 5, "100");

    cerrar();

    assertThat(ultima(vendedora))
        .containsEntry("carried_in", 5)
        .containsEntry("new_ftds", 0)
        .containsEntry("paid_ftds", 5)
        .containsEntry("carried_out", 0);
  }

  @Test
  @DisplayName("CA-CM-243 · 130 con 50 y 60: paga una vez el de 60 y quedan 70")
  void unaVez() throws Exception {
    escalonDeRol(MANAGER, 50, "8000");
    escalonDeRol(MANAGER, 60, "9000");
    ftds(vendedora, 130);
    cerrar();
    assertThat(ultima(vendedora)).containsEntry("paid_ftds", 60).containsEntry("carried_out", 70);
    assertThat(comisionesAfftrack(vendedora)).isEqualTo(1);
  }

  @Test
  @DisplayName("CA-CM-244 · 40 con 50 y 60: nada pagado, 40 de remanente y ninguna fila en el lote")
  void noAlcanza() throws Exception {
    escalonDeRol(MANAGER, 50, "8000");
    escalonDeRol(MANAGER, 60, "9000");
    ftds(vendedora, 40);
    cerrar();
    assertThat(ultima(vendedora)).containsEntry("paid_ftds", 0).containsEntry("carried_out", 40);
    assertThat(ultima(vendedora).get("threshold_rate_id")).isNull();
    assertThat(comisionesAfftrack(vendedora)).isZero();
  }

  @Test
  @DisplayName(
      "CA-CM-245 · un FTD cuenta al vendedor y a sus dos superiores, cada uno con su escala")
  void cadena() throws Exception {
    UUID jefe = SettlementFixtures.persona(jdbc, "af-jefe", AGENTE);
    UUID jefa = SettlementFixtures.persona(jdbc, "af-jefa", AGENTE);
    SettlementFixtures.superior(jdbc, vendedora, jefe);
    SettlementFixtures.superior(jdbc, jefe, jefa);
    escalonDeRol(MANAGER, 2, "100");
    escalonDeRol(AGENTE, 3, "10");
    ftds(vendedora, 2);
    ftds(jefe, 1);

    cerrar();

    assertThat(ultima(vendedora)).containsEntry("new_ftds", 2).containsEntry("paid_ftds", 2);
    // El jefe cuenta el suyo y los dos de su red; la jefa, los tres de su red.
    assertThat(ultima(jefe)).containsEntry("new_ftds", 3).containsEntry("paid_ftds", 3);
    assertThat(ultima(jefa)).containsEntry("new_ftds", 3).containsEntry("paid_ftds", 3);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM afftrack_ftds WHERE user_id = ? AND chain_level = 0",
                Long.class,
                jefe))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("CA-CM-246 · la cadena es la del día de la activación")
  void cadenaDelDia() throws Exception {
    UUID nuevoJefe = SettlementFixtures.persona(jdbc, "af-nuevo", AGENTE);
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at) VALUES (?, ?, ?, ?)",
        UUID.randomUUID(),
        vendedora,
        nuevoJefe,
        ACTIVADA.plusDays(1));
    ftds(vendedora, 1);
    cerrar();
    assertThat(liquidaciones(nuevoJefe)).isZero();
  }

  @Test
  @DisplayName(
      "CA-CM-247 · con escala personal vigente se usa solo esa; vencida, vuelve la del rol")
  void escalaPersonal() throws Exception {
    escalonDeRol(MANAGER, 50, "8000");
    UUID personal = escalonPersonal(vendedora, 40, "1", "2026-01-01", null);
    ftds(vendedora, 55);
    cerrar();
    // Alcanzaría el de 50 de su rol, pero su escala propia lo sustituye entera.
    assertThat(ultima(vendedora))
        .containsEntry("paid_ftds", 40)
        .containsEntry("source", "PERSONALIZADA");

    jdbc.update(
        "UPDATE user_afftrack_rates SET valid_to = CURRENT_DATE - 30 WHERE id = ?", personal);
    ftds(vendedora, 45);
    cerrar();
    assertThat(ultima(vendedora)).containsEntry("source", "ROL").containsEntry("paid_ftds", 50);
  }

  @Test
  @DisplayName(
      "CA-CM-248 · no cuentan: sin activar, sin vendedor, sin confirmar, no FTD; tarde, al siguiente")
  void loQueNoCuenta() throws Exception {
    escalonDeRol(MANAGER, 1, "100");
    UUID bot = SettlementFixtures.producto(jdbc, "AF_BOT", "10.00");
    venta(vendedora, ftd, "CONFIRMADA", false, ACTIVADA);
    venta(null, ftd, "CONFIRMADA", true, ACTIVADA);
    venta(vendedora, ftd, "PENDIENTE", true, ACTIVADA);
    venta(vendedora, bot, "CONFIRMADA", true, ACTIVADA);
    venta(vendedora, ftd, "CONFIRMADA", true, OffsetDateTime.now().plusDays(1));

    cerrar();
    assertThat(liquidaciones(vendedora)).isZero();

    jdbc.update(
        "UPDATE movement_details SET delivered_at = ? WHERE delivered_at > now()", ACTIVADA);
    cerrar();
    assertThat(ultima(vendedora)).containsEntry("new_ftds", 1);
  }

  @Test
  @DisplayName(
      "CA-CM-339 · el FTD de un alta gratuita cuenta en el primer cierre tras el depósito, no antes"
          + " (05-10-2026)")
  void elAltaGratuitaCuentaDesdeElDeposito() throws Exception {
    escalonDeRol(MANAGER, 1, "100");
    // La venta del alta: confirmada desde el registro y con la línea pendiente
    // de activación (`RN-MV-075`). No es un FTD todavía.
    venta(vendedora, ftd, "CONFIRMADA", false, ACTIVADA);

    cerrar();
    assertThat(liquidaciones(vendedora)).isZero();

    // El primer depósito la activa: la entrega fija `delivered_at` AHORA, después
    // del cierre anterior. Es lo que escribe PublishedFirstDepositActivation.
    jdbc.update(
        "UPDATE movement_details SET delivery_status = 'ENTREGADA', delivered_at = ?"
            + " WHERE delivery_status = 'PENDIENTE'",
        OffsetDateTime.now());
    cerrar();
    assertThat(ultima(vendedora)).containsEntry("new_ftds", 1);
  }

  @Test
  @DisplayName("CA-CM-249 · relanzar el cierre no vuelve a contar ningún FTD")
  void relanzar() throws Exception {
    ftds(vendedora, 3);
    cerrar();
    cerrar();
    assertThat(ultima(vendedora)).containsEntry("carried_in", 3).containsEntry("new_ftds", 0);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM afftrack_ftds WHERE user_id = ?", Long.class, vendedora))
        .isEqualTo(3);
  }

  @Test
  @DisplayName("CA-CM-250 · sin escala se acumulan; con un escalón nuevo, el siguiente cierre paga")
  void seAcumulan() throws Exception {
    ftds(vendedora, 10);
    cerrar();
    assertThat(ultima(vendedora)).containsEntry("carried_out", 10).containsEntry("paid_ftds", 0);
    escalonDeRol(MANAGER, 10, "100");
    cerrar();
    assertThat(ultima(vendedora)).containsEntry("paid_ftds", 10).containsEntry("carried_out", 0);
  }

  @Test
  @DisplayName(
      "CA-CM-251 · la comisión POR_AFFTRACK: sin línea ni nivel, en la moneda del producto")
  void formaDeLaComision() throws Exception {
    UUID escalon = escalonDeRol(MANAGER, 2, "100");
    ftds(vendedora, 2);
    cerrar();
    Map<String, Object> c =
        jdbc.queryForMap(
            "SELECT c.commission_kind, c.movement_detail_id, c.chain_level, c.unit_price,"
                + " c.rate_id, c.source, c.rate_type, b.currency_id, b.total_amount"
                + " FROM commissions c JOIN commission_batches b ON b.id = c.batch_id"
                + " WHERE c.user_id = ?",
            vendedora);
    assertThat(c.get("commission_kind")).isEqualTo("POR_AFFTRACK");
    assertThat(c.get("movement_detail_id")).isNull();
    assertThat(c.get("chain_level")).isNull();
    assertThat(c.get("unit_price")).isNull();
    assertThat(c.get("rate_id")).isEqualTo(escalon);
    assertThat(c.get("source")).isEqualTo("ROL");
    assertThat(c.get("rate_type")).isEqualTo("FIJO");
    assertThat(c.get("currency_id"))
        .isEqualTo(
            jdbc.queryForObject("SELECT currency_id FROM products WHERE id = ?", UUID.class, ftd));
    assertThat(importe(c.get("total_amount"))).isEqualByComparingTo("200");
  }

  @Test
  @DisplayName("CA-CM-252 · si la liquidación falla, el cierre se revierte entero")
  void fallaYRevierte() throws Exception {
    // Hasta `V65` el fallo lo provocaba un `límite × valor` que no cabía en `numeric(14,4)`. En
    // centésimas `bigint` ningún dato admitido llega al techo, así que lo provoca una restricción
    // que solo existe en esta prueba: rechaza exactamente el importe testigo (2 × 100 = 200,00) y
    // se retira al acabar, aquí y en la limpieza.
    escalonDeRol(MANAGER, 2, "100");
    ftds(vendedora, 2);
    jdbc.execute(
        "ALTER TABLE commissions ADD CONSTRAINT ck_prueba_afftrack_falla"
            + " CHECK (commission_amount <> 20000) NOT VALID");
    try {
      mvc.perform(
              post("/api/v1/commission-batches/closing")
                  .with(user(SUPERADMIN.toString()).authorities(() -> "commission-batches:settle")))
          .andExpect(status().is5xxServerError());
    } finally {
      jdbc.execute("ALTER TABLE commissions DROP CONSTRAINT IF EXISTS ck_prueba_afftrack_falla");
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM afftrack_settlements", Long.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM afftrack_ftds", Long.class)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM commission_batches WHERE status <> 'ABIERTO'", Long.class))
        .isZero();
  }

  @Test
  @DisplayName("CA-CM-253 · una línea FTD no devenga por venta ni queda con desenlace")
  void noDevengaPorVenta() throws Exception {
    // Una tasa anterior a `RN-CM-037`, sembrada directamente: el alta ya la rechaza.
    CommissionFixtures.sembrarTasaDeRol(jdbc, ftd, MANAGER, "FIJO", "100");
    ftds(vendedora, 1);
    cerrar();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM commissions WHERE commission_kind = 'POR_VENTA'", Long.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM commission_accruals", Long.class))
        .isZero();
  }

  @Test
  @DisplayName("CA-CM-254 · el esquema rechaza una fila con la clase que no le corresponde")
  void esquema() throws Exception {
    escalonDeRol(MANAGER, 1, "100");
    ftds(vendedora, 1);
    cerrar();
    Map<String, Object> c =
        jdbc.queryForMap("SELECT id, batch_id, afftrack_settlement_id FROM commissions LIMIT 1");
    UUID linea = jdbc.queryForObject("SELECT id FROM movement_details LIMIT 1", UUID.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE commissions SET movement_detail_id = ? WHERE id = ?",
                    linea,
                    c.get("id")))
        .hasMessageContaining("ck_commissions_kind");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE commissions SET commission_kind = 'POR_VENTA' WHERE id = ?",
                    c.get("id")))
        .hasMessageContaining("ck_commissions_kind");
  }

  // ---- RF-CM-010 y RF-CM-012 · la clase de cada comisión en el lote --------

  @Test
  @DisplayName("CA-CM-262 · el detalle del lote dice la clase de cada comisión, y suma las dos")
  void detalleConLasDosClases() throws Exception {
    escalonDeRol(MANAGER, 1, "100");
    ftds(vendedora, 1);
    UUID bot = SettlementFixtures.producto(jdbc, "AF_VENTA", "1000.00");
    CommissionFixtures.sembrarTasaDeRol(jdbc, bot, MANAGER, "10.00");
    UUID venta =
        SettlementFixtures.venta(
            jdbc, cliente, ACTIVADA, SettlementFixtures.linea(bot, vendedora, 1, "1000.00"));
    mvc.perform(
            post(
                    "/api/v1/movements/payments/{id}/confirmation",
                    PaymentFixtures.pagoAConciliar(jdbc, venta))
                .with(user(SUPERADMIN.toString()).authorities(() -> "movements:confirm-payment")))
        .andExpect(status().isOk());
    cerrar();

    UUID lote = loteDe(vendedora);
    mvc.perform(
            get("/api/v1/commission-batches/{id}", lote)
                .with(
                    user(SUPERADMIN.toString())
                        .authorities(() -> "commission-batches:read-detail")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalAmount").value(200))
        .andExpect(jsonPath("$.commissions.length()").value(2))
        .andExpect(
            jsonPath("$.commissions[?(@.commissionKind == 'POR_VENTA')].movementCode").isNotEmpty())
        .andExpect(
            jsonPath("$.commissions[?(@.commissionKind == 'POR_AFFTRACK')].quantity").value(1))
        .andExpect(
            jsonPath("$.commissions[?(@.commissionKind == 'POR_AFFTRACK')].productId")
                .value(ftd.toString()))
        .andExpect(
            jsonPath("$.commissions[?(@.commissionKind == 'POR_AFFTRACK')].afftrackSettlementId")
                .isNotEmpty());
  }

  @Test
  @DisplayName("CA-CM-263 · en sus propios lotes, el vendedor ve la comisión POR_AFFTRACK")
  void losMios() throws Exception {
    escalonDeRol(MANAGER, 1, "100");
    ftds(vendedora, 1);
    cerrar();
    mvc.perform(
            get("/api/v1/commission-batches/mine/{id}", loteDe(vendedora))
                .with(user(vendedora.toString()).authorities(() -> "commission-batches:read-own")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalAmount").value(100))
        .andExpect(jsonPath("$.commissions[0].commissionKind").value("POR_AFFTRACK"))
        .andExpect(jsonPath("$.commissions[0].movementId").doesNotExist())
        .andExpect(jsonPath("$.commissions[0].chainLevel").doesNotExist());
  }

  // ---- RF-CM-021 · las liquidaciones --------------------------------------

  @Test
  @DisplayName("CA-CM-255 · 55 FTD con 50 y 60: 0 de entrada, 55 nuevos, 50 pagados, 5 de salida")
  void liquidacionLeida() throws Exception {
    escalonDeRol(MANAGER, 50, "8000");
    escalonDeRol(MANAGER, 60, "9000");
    ftds(vendedora, 55);
    cerrar();
    mvc.perform(liquidaciones().param("userId", vendedora.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].carriedIn").value(0))
        .andExpect(jsonPath("$.content[0].newFtds").value(55))
        .andExpect(jsonPath("$.content[0].paidFtds").value(50))
        .andExpect(jsonPath("$.content[0].carriedOut").value(5))
        .andExpect(jsonPath("$.content[0].tier.threshold").value(50))
        .andExpect(jsonPath("$.content[0].tier.amountPerFtd").value(8000))
        .andExpect(jsonPath("$.content[0].tier.source").value("ROL"))
        .andExpect(jsonPath("$.content[0].amount").value(400000))
        .andExpect(jsonPath("$.content[0].closing.closedAt").isNotEmpty());
  }

  @Test
  @DisplayName("CA-CM-256 · sin escalón alcanzado: sin tier ni importe, todo de remanente")
  void sinEscalon() throws Exception {
    ftds(vendedora, 4);
    cerrar();
    mvc.perform(liquidaciones().param("userId", vendedora.toString()))
        .andExpect(jsonPath("$.content[0].tier").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.content[0].amount").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.content[0].carriedOut").value(4));
  }

  @Test
  @DisplayName("CA-CM-257 · los nuevos de un superior se reparten en propios y de su red")
  void propiosYRed() throws Exception {
    UUID jefe = SettlementFixtures.persona(jdbc, "af-jefe", AGENTE);
    SettlementFixtures.superior(jdbc, vendedora, jefe);
    ftds(vendedora, 2);
    ftds(jefe, 1);
    cerrar();
    mvc.perform(liquidaciones().param("userId", jefe.toString()))
        .andExpect(jsonPath("$.content[0].newFtds").value(3))
        .andExpect(jsonPath("$.content[0].ownFtds").value(1))
        .andExpect(jsonPath("$.content[0].networkFtds").value(2));
  }

  @Test
  @DisplayName(
      "CA-CM-258 · filtros combinables; inválidos rechazados; la primera fila es el remanente")
  void filtros() throws Exception {
    escalonDeRol(MANAGER, 50, "8000");
    ftds(vendedora, 55);
    cerrar();
    cerrar();
    mvc.perform(
            liquidaciones()
                .param("userId", vendedora.toString())
                .param("productId", ftd.toString()))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].carriedIn").value(5))
        .andExpect(jsonPath("$.content[0].carriedOut").value(5));
    mvc.perform(liquidaciones().param("userId", vendedora.toString()).param("paid", "true"))
        .andExpect(jsonPath("$.totalElements").value(1));
    UUID cierre =
        jdbc.queryForObject(
            "SELECT closing_id FROM afftrack_settlements ORDER BY created_at LIMIT 1", UUID.class);
    mvc.perform(liquidaciones().param("closingId", cierre.toString()))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(
            liquidaciones()
                .param("from", "2026-12-31T00:00:00Z")
                .param("to", "2026-01-01T00:00:00Z"))
        .andExpect(status().isBadRequest());
    mvc.perform(liquidaciones().param("userId", "no-es-un-uuid"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("CA-CM-259 · sin afftrack-settlements:read, 403; y NO una consulta por fila")
  void liquidacionesPermisoYSentencias() throws Exception {
    mvc.perform(
            get("/api/v1/afftrack-settlements")
                .with(user(SUPERADMIN.toString()).authorities(() -> "afftrack-rates:read")))
        .andExpect(status().isForbidden());
    ftds(vendedora, 1);
    cerrar();
    long conUna = sentenciasDeLiquidaciones();
    UUID otra = SettlementFixtures.persona(jdbc, "af-otra", MANAGER);
    ftds(otra, 1);
    cerrar();
    assertThat(sentenciasDeLiquidaciones()).isEqualTo(conUna);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      liquidaciones() {
    return get("/api/v1/afftrack-settlements")
        .with(user(SUPERADMIN.toString()).authorities(() -> "afftrack-settlements:read"));
  }

  private long sentenciasDeLiquidaciones() throws Exception {
    org.hibernate.stat.Statistics estadisticas = sessionFactory.getStatistics();
    estadisticas.setStatisticsEnabled(true);
    estadisticas.clear();
    mvc.perform(liquidaciones()).andExpect(status().isOk());
    return estadisticas.getPrepareStatementCount();
  }

  private UUID loteDe(UUID persona) {
    return jdbc.queryForObject(
        "SELECT id FROM commission_batches WHERE user_id = ? ORDER BY created_at DESC LIMIT 1",
        UUID.class,
        persona);
  }

  // ---- La configuración, vista después de un cierre -----------------------

  @Test
  @DisplayName(
      "CA-CM-222 · corregir un escalón tras un cierre no cambia lo pagado ni su liquidación")
  void corregirNoTocaLoPagado() throws Exception {
    UUID escalon = escalonDeRol(MANAGER, 2, "100");
    ftds(vendedora, 2);
    cerrar();
    mvc.perform(
            patch("/api/v1/afftrack-rates/{id}", escalon)
                .with(user(SUPERADMIN.toString()).authorities(() -> "afftrack-rates:update"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"threshold\":1,\"amountPerFtd\":999}"))
        .andExpect(status().isOk());
    Map<String, Object> c = comisionAfftrack(vendedora);
    assertThat(c.get("quantity")).isEqualTo(2);
    assertThat(importe(c.get("fixed_amount"))).isEqualByComparingTo("100");
    assertThat(ultima(vendedora)).containsEntry("paid_ftds", 2);
  }

  @Test
  @DisplayName("CA-CM-227 y CA-CM-228 · retirado, el siguiente cierre no lo aplica; los FTD quedan")
  void retirarDejaElRemanente() throws Exception {
    UUID escalon = escalonDeRol(MANAGER, 2, "100");
    mvc.perform(
            post("/api/v1/afftrack-rates/{id}/deletion", escalon)
                .with(user(SUPERADMIN.toString()).authorities(() -> "afftrack-rates:delete"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Fuera\"}"))
        .andExpect(status().isNoContent());
    ftds(vendedora, 3);
    cerrar();
    assertThat(ultima(vendedora)).containsEntry("paid_ftds", 0).containsEntry("carried_out", 3);
    assertThat(comisionesAfftrack(vendedora)).isZero();
  }

  // ---------------------------------------------------------------------------

  private void cerrar() throws Exception {
    mvc.perform(
            post("/api/v1/commission-batches/closing")
                .with(user(SUPERADMIN.toString()).authorities(() -> "commission-batches:settle")))
        .andExpect(status().isOk());
  }

  private void ftds(UUID vendedor, int cuantos) {
    for (int i = 0; i < cuantos; i++) {
      venta(vendedor, ftd, "CONFIRMADA", true, ACTIVADA);
    }
  }

  /** Una venta de una línea, en el estado pedido, entregada o no. */
  private void venta(
      UUID vendedor, UUID producto, String estado, boolean entregada, OffsetDateTime entregadaEl) {
    UUID movimiento = UUID.randomUUID();
    boolean confirmada = "CONFIRMADA".equals(estado);
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id, currency_id, code,
                               status, total_amount, discount_amount, payable_amount, occurred_at,
                               confirmed_at)
        VALUES (?, CAST(? AS uuid),
                (SELECT s.id FROM movement_type_statuses s
                  WHERE s.movement_type_id = CAST(? AS uuid) AND s.code = 'VALIDADO'),
                ?, (SELECT currency_id FROM products WHERE id = ?), ?, ?, 0, 0, 0, ?, ?)
        """,
        movimiento,
        SettlementFixtures.VENTA,
        SettlementFixtures.VENTA,
        cliente,
        producto,
        "VTA-ST" + movimiento.toString().substring(0, 8).toUpperCase(),
        estado,
        entregadaEl.minusHours(1),
        confirmada ? entregadaEl.minusMinutes(30) : null);
    jdbc.update(
        """
        INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                      quantity, unit_price, line_amount, implementation,
                                      delivery_status, delivered_at)
        SELECT ?, ?, p.id, ?, p.name, 1, 0, 0, p.implementation, ?, ?
          FROM products p WHERE p.id = ?
        """,
        UUID.randomUUID(),
        movimiento,
        vendedor,
        entregada ? "ENTREGADA" : "PENDIENTE",
        entregada ? entregadaEl : null,
        producto);
  }

  private UUID escalonDeRol(String rol, int limite, String valor) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO afftrack_rates (id, product_id, role_id, threshold, amount_per_ftd,"
            + " created_at, updated_at) VALUES (?, ?, CAST(? AS uuid), ?, CAST(? AS bigint),"
            + " now(), now())",
        id,
        ftd,
        rol,
        limite,
        centesimas(valor));
    return id;
  }

  private UUID escalonPersonal(UUID persona, int limite, String valor, String desde, String hasta) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO user_afftrack_rates (id, user_id, product_id, threshold, amount_per_ftd,"
            + " valid_from, valid_to, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, CAST(? AS bigint), CAST(? AS date), CAST(? AS date), now(),"
            + " now())",
        id,
        persona,
        ftd,
        limite,
        centesimas(valor),
        desde,
        hasta);
    return id;
  }

  private Map<String, Object> ultima(UUID persona) {
    return jdbc.queryForMap(
        "SELECT carried_in, new_ftds, paid_ftds, carried_out, source, threshold_rate_id"
            + " FROM afftrack_settlements WHERE user_id = ? AND product_id = ?"
            + " ORDER BY created_at DESC, id DESC LIMIT 1",
        persona,
        ftd);
  }

  private long liquidaciones(UUID persona) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM afftrack_settlements WHERE user_id = ?", Long.class, persona);
  }

  private long comisionesAfftrack(UUID persona) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM commissions WHERE user_id = ? AND commission_kind = 'POR_AFFTRACK'",
        Long.class,
        persona);
  }

  private Map<String, Object> comisionAfftrack(UUID persona) {
    return jdbc.queryForMap(
        "SELECT c.quantity, c.fixed_amount, c.commission_amount, b.status"
            + " FROM commissions c JOIN commission_batches b ON b.id = c.batch_id"
            + " WHERE c.user_id = ? AND c.commission_kind = 'POR_AFFTRACK'"
            + " ORDER BY c.created_at LIMIT 1",
        persona);
  }
}
