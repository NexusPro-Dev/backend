package com.factech.nexus.modules.movements.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
 * `RF-MV-003` — confirmar el pago de una venta pendiente, y lo que confirmar entrega.
 *
 * <p><b>Las ventas se siembran por SQL</b>, como en {@code MyMovementsIT}: lo que se prueba es la
 * transición y la entrega, no el registro. <b>La membresía se comprueba en {@code
 * user_memberships}</b>, que es donde `SP` la escribe: es el efecto del requerimiento, y una
 * respuesta HTTP que dijera «entregada» sin esa fila sería la avería que nadie reporta.
 */
@AutoConfigureMockMvc
class ConfirmSaleIT extends IntegrationTestBase {
  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  private static final String ORO = "01a04ad0-e800-7004-9c4f-5e7ad7000004";
  private static final String PLATINO = "01a04ad0-e800-7003-9c4f-5e7ad7000003";
  private static final String VIP = "01a04ad0-e800-7002-9c4f-5e7ad7000002";
  private static final String BECA = "01a04ad0-e800-7001-9c4f-5e7ad7000001";

  private static final OffsetDateTime VENDIDA_EL =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID cajero;
  private UUID cliente;
  private UUID vendedor;

  /** `CF_VIP`: upgrade automático a VIP, 30 días. */
  private UUID upgradeVip;

  /** `CF_PLAT`: upgrade automático a PLATINO, sin vigencia. */
  private UUID upgradePlatino;

  /** `CF_BOT`: bot automático, 15 días. */
  private UUID bot;

  /** `CF_MANUAL`: bot manual. */
  private UUID botManual;

  @BeforeEach
  void sembrar() {
    limpiar();
    reponerLaCadena();

    cajero = persona("cf-cajero");
    cliente = persona("cf-cliente");
    vendedor = persona("cf-vendedor");

    upgradeVip = upgrade("CF_VIP", "Ascenso a VIP", VIP, 30);
    upgradePlatino = upgrade("CF_PLAT", "Ascenso a Platino", PLATINO, null);
    bot = bot("CF_BOT", "AUTOMATICA", 15);
    botManual = bot("CF_MANUAL", "MANUAL", null);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // La transición
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-083 — una venta pendiente pasa a CONFIRMADA con su instante")
  void confirma() throws Exception {
    UUID venta = venta(cliente, "PENDIENTE", bot);

    mvc.perform(confirmar(venta).with(conPermiso(cajero)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(venta.toString()))
        .andExpect(jsonPath("$.status").value("CONFIRMADA"))
        .andExpect(jsonPath("$.confirmedAt").isNotEmpty());

    assertThat(estadoDe(venta)).isEqualTo("CONFIRMADA");
    assertThat(
            jdbc.queryForObject(
                "SELECT confirmed_at FROM movements WHERE id = ?", Object.class, venta))
        .isNotNull();
  }

  @Test
  @DisplayName("CA-MV-084 — la segunda confirmación es 409, no cambia nada y no concede dos veces")
  void laSegundaNoConcedeOtraVez() throws Exception {
    UUID venta = venta(cliente, "PENDIENTE", upgradeVip);

    mvc.perform(confirmar(venta).with(conPermiso(cajero))).andExpect(status().isOk());
    int filasDespuesDeLaPrimera = periodosDe(cliente);

    mvc.perform(confirmar(venta).with(conPermiso(cajero)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("CONFIRMADA")));

    // UNA membresía nueva, no dos: la segunda confirmación afectó cero filas y
    // no llegó a recorrer las líneas.
    assertThat(periodosDe(cliente)).isEqualTo(filasDespuesDeLaPrimera);
    assertThat(vigenteDe(cliente)).isEqualTo("VIP");
  }

  @Test
  @DisplayName("CA-MV-085 — una venta rechazada o anulada es 409 y dice su estado")
  void noPendiente() throws Exception {
    UUID anulada = venta(cliente, "ANULADA", bot);

    mvc.perform(confirmar(anulada).with(conPermiso(cajero)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ANULADA")));
    assertThat(estadoDe(anulada)).isEqualTo("ANULADA");
  }

  @Test
  @DisplayName("CA-MV-086 — una venta que no existe es 404")
  void inexistente() throws Exception {
    mvc.perform(confirmar(UUID.randomUUID()).with(conPermiso(cajero)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("CA-MV-087 — sin movements:confirm es 403; sin autenticar, 401")
  void permiso() throws Exception {
    UUID venta = venta(cliente, "PENDIENTE", bot);

    // El propio comprador, sin permiso: confirmar es afirmar que el dinero ENTRÓ,
    // y eso lo sabe quien lo recibe.
    mvc.perform(confirmar(venta).with(propio(cliente))).andExpect(status().isForbidden());
    mvc.perform(confirmar(venta)).andExpect(status().isUnauthorized());
    assertThat(estadoDe(venta)).isEqualTo("PENDIENTE");
  }

  // ---------------------------------------------------------------------------
  // Lo que confirmar entrega
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-088 y CA-MV-089 — el upgrade automático concede VIP desde la confirmación")
  void concedeElUpgrade() throws Exception {
    UUID venta = venta(cliente, "PENDIENTE", upgradeVip);

    mvc.perform(confirmar(venta).with(conPermiso(cajero)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines[0].deliveryStatus").value("ENTREGADA"))
        .andExpect(jsonPath("$.lines[0].deliveredAt").isNotEmpty())
        .andExpect(jsonPath("$.lines[0].implementation").value("AUTOMATICA"))
        .andExpect(jsonPath("$.lines[0].deliveryNote").value(org.hamcrest.Matchers.nullValue()));

    // La membresía la escribió SP: la comprada abierta, la anterior cerrada.
    assertThat(vigenteDe(cliente)).isEqualTo("VIP");
    Map<String, Object> abierta = abiertaDe(cliente);
    OffsetDateTime confirmada =
        instante(
            jdbc.queryForObject(
                "SELECT confirmed_at FROM movements WHERE id = ?", Object.class, venta));
    // DESDE LA CONFIRMACIÓN y no desde la venta: la venta es del 1 de agosto y
    // treinta días desde ahí ya habrían pasado.
    assertThat(instante(abierta.get("started_at"))).isEqualTo(confirmada);
    assertThat(instante(abierta.get("ends_at"))).isEqualTo(confirmada.plusDays(30));
    assertThat(cerradasDe(cliente)).hasSize(1);
    assertThat(cerradasDe(cliente).get(0).get("code")).isEqualTo("BECA");
  }

  @Test
  @DisplayName("CA-MV-090 — un upgrade sin vigencia concede una membresía sin fecha de fin")
  void sinVigencia() throws Exception {
    UUID venta = venta(cliente, "PENDIENTE", upgradePlatino);

    mvc.perform(confirmar(venta).with(conPermiso(cajero))).andExpect(status().isOk());

    assertThat(vigenteDe(cliente)).isEqualTo("PLATINO");
    assertThat(abiertaDe(cliente).get("ends_at")).isNull();
  }

  @Test
  @DisplayName("CA-MV-091 — renovar el mismo nivel cierra el periodo vigente y abre uno nuevo")
  void renovacion() throws Exception {
    // VIGENTE todavía: si ya hubiera vencido no habría nada que renovar, y el
    // caso sería el de la persona sin membresía vigente (FA-004).
    darMembresia(cliente, VIP, OffsetDateTime.now(ZoneOffset.UTC).plusDays(10));
    UUID venta = venta(cliente, "PENDIENTE", upgradeVip);

    mvc.perform(confirmar(venta).with(conPermiso(cajero)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines[0].deliveryStatus").value("ENTREGADA"));

    // Dos periodos de VIP: el que había, cerrado, y el comprado, abierto. Los
    // días que quedaban del anterior no se suman (§5.4, decisión 2).
    assertThat(vigenteDe(cliente)).isEqualTo("VIP");
    assertThat(periodosDe(cliente)).isEqualTo(3);
    assertThat(cerradasDe(cliente)).extracting(fila -> fila.get("code")).contains("VIP", "BECA");
  }

  @Test
  @DisplayName("CA-MV-092 — un upgrade inferior al vigente se RETIENE y la membresía no se toca")
  void noBajaDeNivel() throws Exception {
    // Entre registrar y confirmar, la persona subió a PLATINO por otra vía.
    darMembresia(cliente, PLATINO, null);
    UUID venta = venta(cliente, "PENDIENTE", upgradeVip);

    mvc.perform(confirmar(venta).with(conPermiso(cajero)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMADA"))
        .andExpect(jsonPath("$.lines[0].deliveryStatus").value("RETENIDA"))
        .andExpect(jsonPath("$.lines[0].deliveredAt").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(
            jsonPath("$.lines[0].deliveryNote")
                .value("La membresía comprada (VIP) es inferior a la vigente (PLATINO)."));

    // La venta cobró, y la membresía quedó como estaba.
    assertThat(estadoDe(venta)).isEqualTo("CONFIRMADA");
    assertThat(vigenteDe(cliente)).isEqualTo("PLATINO");
    assertThat(periodosDe(cliente)).isEqualTo(2);
  }

  @Test
  @DisplayName("CA-MV-093 — una línea manual queda PENDIENTE y la venta confirma igual")
  void manualEspera() throws Exception {
    UUID venta = venta(cliente, "PENDIENTE", botManual);

    mvc.perform(confirmar(venta).with(conPermiso(cajero)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMADA"))
        .andExpect(jsonPath("$.lines[0].implementation").value("MANUAL"))
        .andExpect(jsonPath("$.lines[0].deliveryStatus").value("PENDIENTE"))
        .andExpect(jsonPath("$.lines[0].deliveredAt").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  @DisplayName("CA-MV-094 — un bot automático queda ENTREGADO sin tocar ninguna membresía")
  void botAutomatico() throws Exception {
    UUID venta = venta(cliente, "PENDIENTE", bot);

    mvc.perform(confirmar(venta).with(conPermiso(cajero)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines[0].deliveryStatus").value("ENTREGADA"))
        .andExpect(jsonPath("$.lines[0].deliveredAt").isNotEmpty());

    assertThat(vigenteDe(cliente)).isEqualTo("BECA");
    assertThat(periodosDe(cliente)).isEqualTo(1);
  }

  @Test
  @DisplayName("CA-MV-095 — una venta con varias líneas entrega cada una por su regla")
  void variasLineas() throws Exception {
    UUID venta = venta(cliente, "PENDIENTE", upgradeVip, botManual, bot);

    mvc.perform(confirmar(venta).with(conPermiso(cajero)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines.length()").value(3))
        // Las líneas van por código de producto: CF_BOT, CF_MANUAL, CF_VIP.
        .andExpect(jsonPath("$.lines[0].productCode").value("CF_BOT"))
        .andExpect(jsonPath("$.lines[0].deliveryStatus").value("ENTREGADA"))
        .andExpect(jsonPath("$.lines[1].productCode").value("CF_MANUAL"))
        .andExpect(jsonPath("$.lines[1].deliveryStatus").value("PENDIENTE"))
        .andExpect(jsonPath("$.lines[2].productCode").value("CF_VIP"))
        .andExpect(jsonPath("$.lines[2].deliveryStatus").value("ENTREGADA"));

    assertThat(vigenteDe(cliente)).isEqualTo("VIP");
    assertThat(periodosDe(cliente)).isEqualTo(2);
  }

  // ---------------------------------------------------------------------------
  // Auditoría y detalle
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-096 — el cambio queda auditado en MV con las líneas, y la membresía en SP")
  void auditoria() throws Exception {
    jdbc.update("DELETE FROM audit_change_log WHERE entity IN ('movements', 'user_memberships')");
    UUID venta = venta(cliente, "PENDIENTE", upgradeVip);

    mvc.perform(confirmar(venta).with(conPermiso(cajero))).andExpect(status().isOk());

    String asientoMv =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE entity = 'movements' AND entity_id = ?"
                + " AND action = 'UPDATE'",
            String.class,
            venta);
    assertThat(asientoMv).contains("CONFIRMADA").contains("CF_VIP").contains("ENTREGADA");

    String asientoSp =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE entity = 'user_memberships'"
                + " AND entity_id = ? ORDER BY occurred_at DESC LIMIT 1",
            String.class,
            cliente);
    assertThat(asientoSp).contains("VIP").contains("PURCHASE");
  }

  @Test
  @DisplayName("CA-MV-098 — el detalle propio muestra la confirmación y la entrega de cada línea")
  void elDetalleLoMuestra() throws Exception {
    UUID venta = venta(cliente, "PENDIENTE", bot);
    mvc.perform(confirmar(venta).with(conPermiso(cajero))).andExpect(status().isOk());

    mvc.perform(get("/api/v1/movements/mine/{id}", venta).with(propio(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMADA"))
        .andExpect(jsonPath("$.confirmedAt").isNotEmpty())
        .andExpect(jsonPath("$.lines[0].deliveryStatus").value("ENTREGADA"))
        .andExpect(jsonPath("$.lines[0].deliveredAt").isNotEmpty());

    // Y el listado propio también lleva la confirmación.
    mvc.perform(get("/api/v1/movements/mine").with(propio(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].confirmedAt").isNotEmpty());
  }

  // ---------------------------------------------------------------------------
  // Auxiliares
  // ---------------------------------------------------------------------------

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      confirmar(UUID venta) {
    return post("/api/v1/movements/{id}/confirmation", venta);
  }

  private RequestPostProcessor conPermiso(UUID persona) {
    return user(persona.toString()).authorities(() -> "movements:confirm");
  }

  private String estadoDe(UUID venta) {
    return jdbc.queryForObject("SELECT status FROM movements WHERE id = ?", String.class, venta);
  }

  /** El código de la membresía abierta (sin cerrar). */
  private String vigenteDe(UUID persona) {
    return jdbc.queryForObject(
        "SELECT m.code FROM user_memberships um JOIN memberships m ON m.id = um.membership_id"
            + " WHERE um.user_id = ? AND um.closed_at IS NULL",
        String.class,
        persona);
  }

  private Map<String, Object> abiertaDe(UUID persona) {
    return jdbc.queryForMap(
        "SELECT started_at, ends_at FROM user_memberships WHERE user_id = ? AND closed_at IS NULL",
        persona);
  }

  private List<Map<String, Object>> cerradasDe(UUID persona) {
    return jdbc.queryForList(
        "SELECT m.code AS code, um.closed_at FROM user_memberships um"
            + " JOIN memberships m ON m.id = um.membership_id"
            + " WHERE um.user_id = ? AND um.closed_at IS NOT NULL",
        persona);
  }

  private int periodosDe(UUID persona) {
    Integer total =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_memberships WHERE user_id = ?", Integer.class, persona);
    return total == null ? 0 : total;
  }

  private static OffsetDateTime instante(Object valor) {
    if (valor == null) {
      return null;
    }
    if (valor instanceof OffsetDateTime momento) {
      return momento.withOffsetSameInstant(ZoneOffset.UTC);
    }
    if (valor instanceof java.sql.Timestamp marca) {
      return marca.toInstant().atOffset(ZoneOffset.UTC);
    }
    throw new IllegalStateException("Tipo temporal inesperado: " + valor.getClass());
  }

  private void limpiar() {
    jdbc.update("DELETE FROM movement_detail_discounts");
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM products WHERE code LIKE 'CF\\_%'");
    jdbc.update(
        "DELETE FROM user_memberships WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'cf-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'cf-%'");
  }

  /** La cadena entera de `V9`, porque las pruebas de `SP` la dejan como quieren. */
  private void reponerLaCadena() {
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM memberships");
    jdbc.update(
        """
        INSERT INTO memberships (id, code, name, description, parent_membership_id, level, color)
        VALUES
          (CAST(? AS uuid), 'ORO', 'Oro', 'Nivel más alto.', NULL, 1, 'FFB300'),
          (CAST(? AS uuid), 'PLATINO', 'Platino', 'Nivel intermedio.', CAST(? AS uuid), 2, 'B0BEC5'),
          (CAST(? AS uuid), 'VIP', 'VIP', 'Primer nivel de pago.', CAST(? AS uuid), 3, '7E57C2'),
          (CAST(? AS uuid), 'BECA', 'Beca', 'Nivel de entrada.', CAST(? AS uuid), 4, '9E9E9E')
        """,
        ORO,
        PLATINO,
        ORO,
        VIP,
        PLATINO,
        BECA,
        VIP);
  }

  private UUID persona(String username) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, 'Nombre', 'Apellido', 'x', false, 'ACTIVO', (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@factech.co");
    darElSuelo(jdbc, id);
    return id;
  }

  /** Cierra lo abierto y abre la membresía dada, como si otra vía la hubiera concedido. */
  private void darMembresia(UUID persona, String membresia, OffsetDateTime hasta) {
    jdbc.update(
        "UPDATE user_memberships SET closed_at = now() WHERE user_id = ? AND closed_at IS NULL",
        persona);
    jdbc.update(
        "INSERT INTO user_memberships (id, user_id, membership_id, started_at, ends_at)"
            + " VALUES (gen_random_uuid(), ?, CAST(? AS uuid), now(), CAST(? AS timestamptz))",
        persona,
        membresia,
        hasta == null ? null : hasta.toString());
  }

  private UUID upgrade(String codigo, String nombre, String destino, Integer vigencia) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description,"
            + " source_membership_id, target_membership_id, price, currency_id, validity_days, status)"
            + " VALUES ('TIENDA', 'AUTOMATICA', ?, ?, 'UPGRADE_MEMBRESIA', ?, 'Un ascenso',"
            + " CAST(? AS uuid), CAST(? AS uuid), 100.00, CAST(? AS uuid), ?, 'ACTIVO')",
        id,
        codigo,
        nombre,
        BECA,
        destino,
        USD,
        vigencia);
    return id;
  }

  private UUID bot(String codigo, String implementacion, Integer vigencia) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description,"
            + " source_membership_id, target_membership_id, price, currency_id, validity_days, status)"
            + " VALUES ('TIENDA', ?, ?, ?, 'BOT', ?, 'Un bot', NULL, NULL, 50.00, CAST(? AS uuid), ?,"
            + " 'ACTIVO')",
        implementacion,
        id,
        codigo,
        "Bot " + codigo,
        USD,
        vigencia);
    return id;
  }

  /** Una venta con una línea por producto, con la implementación COPIADA del producto. */
  private UUID venta(UUID sujeto, String estado, UUID... productos) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, user_id, payment_method_id,
                               currency_id, code, status, total_amount, discount_amount,
                               payable_amount, occurred_at, confirmed_at,
                               voided_at, void_reason)
        VALUES (?, CAST(? AS uuid), ?, CAST(? AS uuid), CAST(? AS uuid), ?, ?,
                100.00, 0, 100.00, CAST(? AS timestamptz),
                CASE WHEN ? = 'CONFIRMADA' THEN CAST(? AS timestamptz) ELSE NULL END,
                -- `ck_movements_voided`: una anulada lleva fecha y motivo, y solo ella.
                CASE WHEN ? = 'ANULADA' THEN now() ELSE NULL END,
                CASE WHEN ? = 'ANULADA' THEN 'Sembrada anulada' ELSE NULL END)
        """,
        id,
        VENTA,
        sujeto,
        TARJETA,
        USD,
        "VTA-" + id.toString().substring(0, 8).toUpperCase(),
        estado,
        VENDIDA_EL.toString(),
        estado,
        VENDIDA_EL.toString(),
        estado,
        estado);
    for (UUID producto : productos) {
      jdbc.update(
          """
          INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                        product_description, quantity, unit_price, line_amount,
                                        validity_days, implementation)
          SELECT ?, ?, p.id, ?, p.name, p.description, 1, 100.00, 100.00, p.validity_days,
                 p.implementation
            FROM products p WHERE p.id = ?
          """,
          UUID.randomUUID(),
          id,
          vendedor,
          producto);
    }
    return id;
  }

  // Desde RF-SP-062 (21-09-2026) lo propio exige permiso —autenticarse no autoriza
  // nada—: el actor porta la familia de alcance propio de MV, que es lo que V31 da a
  // todo rol. Hasta entonces bastaba con `user(id)`.
  private static RequestPostProcessor propio(UUID persona) {
    return user(persona.toString())
        .authorities(
            () -> "movements:list-own",
            () -> "movements:read-own",
            () -> "movements:read-own-products",
            () -> "packages:buy");
  }
}
