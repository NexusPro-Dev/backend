package com.factech.nexus.modules.commissions.interfaces;

import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.AGENTE;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.MANAGER;
import static com.factech.nexus.modules.commissions.interfaces.CommissionFixtures.centesimas;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.testing.CommissionCleanup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Mi avance de FTD en el periodo abierto (`RF-CM-030`): la liquidación del próximo cierre, corrida
 * ahora y sin escribir (`RN-CM-056`). Siembra como {@link AfftrackSettlementIT}: ventas FTD por
 * SQL, ya confirmadas y entregadas; los remanentes, con un cierre de verdad por la API.
 */
@AutoConfigureMockMvc
class AfftrackProgressIT extends IntegrationTestBase {

  private static final String RUTA = "/api/v1/afftrack-settlements/mine/progress";
  private static final String PERMISO = "afftrack-settlements:read-own-progress";
  private static final OffsetDateTime ACTIVADA = OffsetDateTime.now().minusDays(3);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private UUID ftd;
  private UUID cliente;
  private UUID jefa;
  private UUID agente;

  @BeforeEach
  void preparar() {
    limpiar();
    reponerElSuelo(jdbc);
    ftd = AfftrackFixtures.productoFtd(jdbc, "AVANCE", false);
    cliente = SettlementFixtures.persona(jdbc, "av-cliente", null);
    jefa = SettlementFixtures.persona(jdbc, "av-jefa", MANAGER);
    agente = SettlementFixtures.persona(jdbc, "av-agente", AGENTE);
    SettlementFixtures.superior(jdbc, agente, jefa);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  private void limpiar() {
    CommissionCleanup.limpiar(jdbc);
    jdbc.update("DELETE FROM user_afftrack_rates");
    jdbc.update("DELETE FROM afftrack_rates");
    SettlementFixtures.limpiar(jdbc);
    AfftrackFixtures.limpiar(jdbc);
  }

  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-CM-399 · directos, de la red, nuevos, remanente y disponibles = nuevos + remanente")
  void desglose() throws Exception {
    escalonDeRol(MANAGER, 50, "2");
    ftds(jefa, 3);
    cerrar(); // le quedan 3 de remanente
    ftds(jefa, 2);
    ftds(agente, 4);

    JsonNode p = producto(avance(jefa));

    assertThat(p.get("carriedIn").asInt()).isEqualTo(3);
    assertThat(p.get("ownFtds").asInt()).isEqualTo(2);
    assertThat(p.get("networkFtds").asInt()).isEqualTo(4);
    assertThat(p.get("newFtds").asInt()).isEqualTo(6);
    assertThat(p.get("available").asInt()).isEqualTo(9);
    assertThat(p.get("product").get("id").asText()).isEqualTo(ftd.toString());
  }

  @Test
  @DisplayName("CA-CM-400 · 55 con 50 y 60: alcanza el de 50 por 50 × valor, sobran 5, faltan 5")
  void cincuentaYCinco() throws Exception {
    escalonDeRol(MANAGER, 50, "2");
    escalonDeRol(MANAGER, 60, "2.5");
    ftds(jefa, 55);

    JsonNode p = producto(avance(jefa));

    JsonNode alcanzado = p.get("reachedTier");
    assertThat(alcanzado.get("threshold").asInt()).isEqualTo(50);
    assertThat(decimal(alcanzado.get("amountPerFtd"))).isEqualByComparingTo("2");
    assertThat(decimal(alcanzado.get("estimatedAmount"))).isEqualByComparingTo("100");
    assertThat(alcanzado.get("source").asText()).isEqualTo("ROL");
    assertThat(p.get("remainder").asInt()).isEqualTo(5);
    assertThat(p.get("nextTier").get("threshold").asInt()).isEqualTo(60);
    assertThat(p.get("nextTier").get("missing").asInt()).isEqualTo(5);
  }

  @Test
  @DisplayName("CA-CM-401 · con 40 no alcanza y faltan 10; con 130 alcanza el de 60 y no hay más")
  void extremos() throws Exception {
    escalonDeRol(MANAGER, 50, "2");
    escalonDeRol(MANAGER, 60, "2.5");
    ftds(jefa, 40);

    JsonNode p = producto(avance(jefa));
    assertThat(p.get("reachedTier").isNull()).isTrue();
    assertThat(p.get("remainder").asInt()).isEqualTo(40);
    assertThat(p.get("nextTier").get("threshold").asInt()).isEqualTo(50);
    assertThat(p.get("nextTier").get("missing").asInt()).isEqualTo(10);

    ftds(jefa, 90);
    p = producto(avance(jefa));
    assertThat(p.get("reachedTier").get("threshold").asInt()).isEqualTo(60);
    assertThat(p.get("remainder").asInt()).isEqualTo(70);
    assertThat(p.get("nextTier").isNull()).isTrue();
  }

  @Test
  @DisplayName(
      "CA-CM-402 · con FTD y sin escala sale sin escalones; sin FTD, remanente ni escala, no sale")
  void sinEscala() throws Exception {
    AfftrackFixtures.productoFtd(jdbc, "AVANCE_VACIO", false);
    ftds(jefa, 2);

    JsonNode cuerpo = avance(jefa);

    assertThat(cuerpo.get("products")).hasSize(1);
    JsonNode p = producto(cuerpo);
    assertThat(p.get("available").asInt()).isEqualTo(2);
    assertThat(p.get("reachedTier").isNull()).isTrue();
    assertThat(p.get("nextTier").isNull()).isTrue();
    assertThat(p.get("remainder").asInt()).isEqualTo(2);
  }

  @Test
  @DisplayName("CA-CM-403 · con escala personal vigente rige la suya entera, y lo dice")
  void escalaPersonal() throws Exception {
    escalonDeRol(MANAGER, 50, "2");
    escalonPersonal(jefa, 40, "1", "2026-01-01");
    ftds(jefa, 55);

    JsonNode p = producto(avance(jefa));

    assertThat(p.get("reachedTier").get("threshold").asInt()).isEqualTo(40);
    assertThat(p.get("reachedTier").get("source").asText()).isEqualTo("PERSONALIZADA");
    assertThat(p.get("nextTier").isNull()).isTrue();
  }

  @Test
  @DisplayName(
      "CA-CM-404 · no cuentan los ya contados, ni sin entregar, sin vendedor o no confirmadas")
  void loQueNoCuenta() throws Exception {
    escalonDeRol(MANAGER, 50, "2");
    ftds(jefa, 1);
    cerrar(); // ese ya se contó: es remanente, no nuevo
    venta(jefa, "CONFIRMADA", false);
    venta(null, "CONFIRMADA", true);
    venta(jefa, "PENDIENTE", true);

    JsonNode p = producto(avance(jefa));

    assertThat(p.get("newFtds").asInt()).isZero();
    assertThat(p.get("carriedIn").asInt()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "CA-CM-405 · cuenta el de quien era de mi red al activarse, no el de quien entró después")
  void cadenaDelDia() throws Exception {
    escalonDeRol(MANAGER, 50, "2");
    UUID seFue = SettlementFixtures.persona(jdbc, "av-sefue", AGENTE);
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at, ended_at)"
            + " VALUES (?, ?, ?, '2026-01-01T00:00:00Z', ?)",
        UUID.randomUUID(),
        seFue,
        jefa,
        ACTIVADA.plusDays(1));
    UUID llego = SettlementFixtures.persona(jdbc, "av-llego", AGENTE);
    jdbc.update(
        "INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at) VALUES (?, ?, ?, ?)",
        UUID.randomUUID(),
        llego,
        jefa,
        ACTIVADA.plusDays(1));
    ftds(seFue, 1);
    ftds(llego, 1);

    assertThat(producto(avance(jefa)).get("networkFtds").asInt()).isEqualTo(1);
  }

  @Test
  @DisplayName("CA-CM-406 · no escribe nada")
  void noEscribe() throws Exception {
    escalonDeRol(MANAGER, 50, "2");
    ftds(jefa, 55);
    String filas =
        "SELECT (SELECT count(*) FROM afftrack_settlements) || '/'"
            + " || (SELECT count(*) FROM afftrack_ftds) || '/'"
            + " || (SELECT count(*) FROM commissions) || '/'"
            + " || (SELECT count(*) FROM commission_batches) || '/'"
            + " || (SELECT coalesce(sum(total_amount), 0) FROM commission_batches)";
    String antes = jdbc.queryForObject(filas, String.class);

    avance(jefa);

    assertThat(jdbc.queryForObject(filas, String.class)).isEqualTo(antes);
  }

  @Test
  @DisplayName(
      "CA-CM-407 · el inicio del periodo es el último corte (nulo sin cierres) y el próximo cierre"
          + " es nulo con el programado apagado")
  void periodo() throws Exception {
    JsonNode cuerpo = avance(jefa);
    assertThat(cuerpo.get("periodStart").isNull()).isTrue();
    assertThat(cuerpo.get("nextClosingAt").isNull()).isTrue();
    assertThat(cuerpo.get("products")).isEmpty();

    cerrar();
    OffsetDateTime corte =
        jdbc.queryForObject("SELECT max(closed_at) FROM commission_closings", OffsetDateTime.class);
    cuerpo = avance(jefa);
    assertThat(OffsetDateTime.parse(cuerpo.get("periodStart").asText()).toInstant())
        .isEqualTo(corte.toInstant());
  }

  @Test
  @DisplayName("CA-CM-408 · cada quien ve solo lo suyo")
  void soloLoPropio() throws Exception {
    escalonDeRol(MANAGER, 50, "2");
    UUID otra = SettlementFixtures.persona(jdbc, "av-otra", MANAGER);
    ftds(jefa, 7);

    assertThat(producto(avance(jefa)).get("ownFtds").asInt()).isEqualTo(7);
    assertThat(producto(avance(otra)).get("newFtds").asInt()).isZero();
  }

  @Test
  @DisplayName(
      "CA-CM-409 · sin read-own-progress se rechaza, también con afftrack-settlements:read; lo"
          + " portan exactamente los roles con commission-batches:list-own")
  void permiso() throws Exception {
    mvc.perform(
            get(RUTA).with(user(jefa.toString()).authorities(() -> "afftrack-settlements:read")))
        .andExpect(status().isForbidden());

    Integer difieren =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM (
              (SELECT rp.role_id FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                WHERE p.code = 'commission-batches:list-own'
               EXCEPT
               SELECT rp.role_id FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                WHERE p.code = ?)
              UNION ALL
              (SELECT rp.role_id FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                WHERE p.code = ?
               EXCEPT
               SELECT rp.role_id FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                WHERE p.code = 'commission-batches:list-own')) d
            """,
            Integer.class,
            PERMISO,
            PERMISO);
    assertThat(difieren).isZero();
  }

  // ---------------------------------------------------------------------------

  private JsonNode avance(UUID persona) throws Exception {
    String cuerpo =
        mvc.perform(get(RUTA).with(user(persona.toString()).authorities(() -> PERMISO)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(cuerpo);
  }

  /** La entrada del producto FTD de la suite; tiene que estar. */
  private JsonNode producto(JsonNode cuerpo) {
    for (JsonNode p : cuerpo.get("products")) {
      if (p.get("product").get("id").asText().equals(ftd.toString())) {
        return p;
      }
    }
    throw new AssertionError("sin el producto FTD: " + cuerpo);
  }

  private static BigDecimal decimal(JsonNode n) {
    return new BigDecimal(n.asText());
  }

  private void cerrar() throws Exception {
    mvc.perform(
            post("/api/v1/commission-batches/closing")
                .with(user(SUPERADMIN.toString()).authorities(() -> "commission-batches:settle")))
        .andExpect(status().isOk());
  }

  private void ftds(UUID vendedor, int cuantos) {
    for (int i = 0; i < cuantos; i++) {
      venta(vendedor, "CONFIRMADA", true);
    }
  }

  /**
   * Una venta FTD de una línea, en el estado pedido, entregada o no; el vendedor puede ser nulo.
   */
  private void venta(UUID vendedor, String estado, boolean entregada) {
    UUID movimiento = UUID.randomUUID();
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
        ftd,
        "VTA-ST" + movimiento.toString().substring(0, 8).toUpperCase(),
        estado,
        ACTIVADA.minusHours(1),
        "CONFIRMADA".equals(estado) ? ACTIVADA.minusMinutes(30) : null);
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
        entregada ? ACTIVADA : null,
        ftd);
  }

  private void escalonDeRol(String rol, int limite, String valor) {
    jdbc.update(
        "INSERT INTO afftrack_rates (id, product_id, role_id, threshold, amount_per_ftd,"
            + " created_at, updated_at) VALUES (?, ?, CAST(? AS uuid), ?, CAST(? AS bigint),"
            + " now(), now())",
        UUID.randomUUID(),
        ftd,
        rol,
        limite,
        centesimas(valor));
  }

  private void escalonPersonal(UUID persona, int limite, String valor, String desde) {
    jdbc.update(
        "INSERT INTO user_afftrack_rates (id, user_id, product_id, threshold, amount_per_ftd,"
            + " valid_from, valid_to, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, CAST(? AS bigint), CAST(? AS date), NULL, now(), now())",
        UUID.randomUUID(),
        persona,
        ftd,
        limite,
        centesimas(valor),
        desde);
  }
}
