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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** `RF-MV-005` — anular una venta pendiente que no debía existir. */
@AutoConfigureMockMvc
class VoidSaleIT extends IntegrationTestBase {
  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";
  private static final String VIP = "01a04ad0-e800-7002-9c4f-5e7ad7000002";
  private static final String BECA = "01a04ad0-e800-7001-9c4f-5e7ad7000001";

  private static final String MOTIVO = "Se registró al cliente equivocado.";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID administrador;
  private UUID cliente;
  private UUID bot;
  private UUID upgrade;

  @BeforeEach
  void sembrar() {
    limpiar();
    jdbc.update("DELETE FROM user_memberships");
    jdbc.update("DELETE FROM memberships");
    jdbc.update(
        """
        INSERT INTO memberships (id, code, name, description, parent_membership_id, level, color)
        VALUES (CAST(? AS uuid), 'VIP', 'VIP', 'Primer nivel de pago.', NULL, 3, '7E57C2'),
               (CAST(? AS uuid), 'BECA', 'Beca', 'Nivel de entrada.', CAST(? AS uuid), 4, '9E9E9E')
        """,
        VIP,
        BECA,
        VIP);
    administrador = persona("vs-admin");
    cliente = persona("vs-cliente");
    bot = producto("VS_BOT", "BOT", null, null);
    upgrade = producto("VS_VIP", "UPGRADE_MEMBRESIA", BECA, VIP);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName("CA-MV-110 — una venta pendiente pasa a ANULADA con su instante y su motivo")
  void anula() throws Exception {
    UUID venta = venta("PENDIENTE", bot);

    mvc.perform(anular(venta, MOTIVO).with(conPermiso(administrador, "movements:void")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ANULADA"))
        .andExpect(jsonPath("$.voidedAt").isNotEmpty())
        .andExpect(jsonPath("$.voidReason").value(MOTIVO))
        .andExpect(jsonPath("$.confirmedAt").value(org.hamcrest.Matchers.nullValue()));

    assertThat(estadoDe(venta)).isEqualTo("ANULADA");
    assertThat(
            jdbc.queryForObject(
                "SELECT void_reason FROM movements WHERE id = ?", String.class, venta))
        .isEqualTo(MOTIVO);
  }

  @Test
  @DisplayName("CA-MV-111 — la segunda anulación es 409 y no cambia nada")
  void dosVeces() throws Exception {
    UUID venta = venta("PENDIENTE", bot);
    mvc.perform(anular(venta, MOTIVO).with(conPermiso(administrador, "movements:void")))
        .andExpect(status().isOk());

    mvc.perform(anular(venta, "Otro motivo").with(conPermiso(administrador, "movements:void")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ANULADA")));

    assertThat(
            jdbc.queryForObject(
                "SELECT void_reason FROM movements WHERE id = ?", String.class, venta))
        .isEqualTo(MOTIVO);
  }

  @Test
  @DisplayName("CA-MV-112 — una confirmada no se anula, y la membresía concedida sigue")
  void confirmadaNoSeAnula() throws Exception {
    UUID venta = venta("PENDIENTE", upgrade);
    mvc.perform(
            post("/api/v1/movements/{id}/confirmation", venta)
                .with(conPermiso(administrador, "movements:confirm")))
        .andExpect(status().isOk());

    mvc.perform(anular(venta, MOTIVO).with(conPermiso(administrador, "movements:void")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("CONFIRMADA")));

    assertThat(estadoDe(venta)).isEqualTo("CONFIRMADA");
    assertThat(
            jdbc.queryForObject(
                "SELECT m.code FROM user_memberships um JOIN memberships m ON m.id = um.membership_id"
                    + " WHERE um.user_id = ? AND um.closed_at IS NULL",
                String.class,
                cliente))
        .isEqualTo("VIP");
  }

  @Test
  @DisplayName("CA-MV-113 — una venta que no existe es 404")
  void inexistente() throws Exception {
    mvc.perform(anular(UUID.randomUUID(), MOTIVO).with(conPermiso(administrador, "movements:void")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("CA-MV-114 — sin motivo, o en blanco, es 400 y la venta sigue pendiente")
  void sinMotivo() throws Exception {
    UUID venta = venta("PENDIENTE", bot);

    mvc.perform(anular(venta, "   ").with(conPermiso(administrador, "movements:void")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    mvc.perform(
            post("/api/v1/movements/{id}/voiding", venta)
                .with(conPermiso(administrador, "movements:void")))
        .andExpect(status().isBadRequest());
    mvc.perform(anular(venta, "x".repeat(501)).with(conPermiso(administrador, "movements:void")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));

    assertThat(estadoDe(venta)).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName(
      "CA-MV-115 — sin movements:void es 403, también con movements:confirm; sin token 401")
  void permisos() throws Exception {
    UUID venta = venta("PENDIENTE", bot);

    // QUIEN CONCILIA NO PUEDE HACER DESAPARECER VENTAS DEL EMBUDO: son dos permisos.
    mvc.perform(anular(venta, MOTIVO).with(conPermiso(administrador, "movements:confirm")))
        .andExpect(status().isForbidden());
    mvc.perform(anular(venta, MOTIVO).with(propio(cliente))).andExpect(status().isForbidden());
    mvc.perform(anular(venta, MOTIVO)).andExpect(status().isUnauthorized());
    assertThat(estadoDe(venta)).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName("CA-MV-116 — las líneas siguen pendientes y el registro de lo comprado dice ANULADO")
  void lineasYRegistro() throws Exception {
    UUID venta = venta("PENDIENTE", bot);
    mvc.perform(anular(venta, MOTIVO).with(conPermiso(administrador, "movements:void")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines[0].deliveryStatus").value("PENDIENTE"));

    mvc.perform(get("/api/v1/movements/mine/products").with(propio(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].state").value("ANULADO"));
  }

  @Test
  @DisplayName("CA-MV-117 — el cambio queda auditado con el motivo, y no como eliminación")
  void auditoria() throws Exception {
    UUID venta = venta("PENDIENTE", bot);
    mvc.perform(anular(venta, MOTIVO).with(conPermiso(administrador, "movements:void")))
        .andExpect(status().isOk());

    String asiento =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE entity = 'movements'"
                + " AND entity_id = ? AND action = 'UPDATE'",
            String.class,
            venta);
    assertThat(asiento).contains("ANULADA").contains(MOTIVO);
    Integer eliminaciones =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_deletion_log WHERE entity_id = ?", Integer.class, venta);
    assertThat(eliminaciones).isZero();
  }

  @Test
  @DisplayName("CA-MV-118 — el detalle propio muestra cuándo y por qué se anuló")
  void detalle() throws Exception {
    UUID venta = venta("PENDIENTE", bot);
    mvc.perform(anular(venta, MOTIVO).with(conPermiso(administrador, "movements:void")))
        .andExpect(status().isOk());

    mvc.perform(get("/api/v1/movements/mine/{id}", venta).with(propio(cliente)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ANULADA"))
        .andExpect(jsonPath("$.voidedAt").isNotEmpty())
        .andExpect(jsonPath("$.voidReason").value(MOTIVO));
  }

  // ---------------------------------------------------------------------------
  // Auxiliares
  // ---------------------------------------------------------------------------

  private static MockHttpServletRequestBuilder anular(UUID venta, String motivo) {
    return post("/api/v1/movements/{id}/voiding", venta)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":" + (motivo == null ? "null" : "\"" + motivo + "\"") + "}");
  }

  private static RequestPostProcessor conPermiso(UUID persona, String permiso) {
    return user(persona.toString()).authorities(() -> permiso);
  }

  private String estadoDe(UUID venta) {
    return jdbc.queryForObject("SELECT status FROM movements WHERE id = ?", String.class, venta);
  }

  private void limpiar() {
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM products WHERE code LIKE 'VS\\_%'");
    jdbc.update(
        "DELETE FROM user_memberships WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'vs-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'vs-%'");
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

  private UUID producto(String codigo, String tipo, String origen, String destino) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description,"
            + " source_membership_id, target_membership_id, price, currency_id, validity_days, status)"
            + " VALUES ('TIENDA', 'AUTOMATICA', ?, ?, ?, ?, 'x', CAST(? AS uuid), CAST(? AS uuid),"
            + " 100.00, CAST(? AS uuid), 30, 'ACTIVO')",
        id,
        codigo,
        tipo,
        "Producto " + codigo,
        origen,
        destino,
        USD);
    return id;
  }

  private UUID venta(String estado, UUID producto) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id, payment_method_id, currency_id,
                               code, status, total_amount, discount_amount, payable_amount,
                               occurred_at)
        VALUES (?, CAST(? AS uuid), (SELECT s.id FROM movement_type_statuses s WHERE s.movement_type_id = CAST(? AS uuid) AND s.code = 'VALIDADO'), ?, CAST(? AS uuid), CAST(? AS uuid), ?, ?, 100.00, 0, 100.00,
                CAST(? AS timestamptz))
        """,
        id,
        VENTA,
        VENTA,
        cliente,
        TARJETA,
        USD,
        "VTA-" + id.toString().substring(0, 8).toUpperCase(),
        estado,
        OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC).toString());
    jdbc.update(
        """
        INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                      product_description, quantity, unit_price, line_amount,
                                      validity_days, implementation)
        SELECT ?, ?, p.id, ?, p.name, p.description, 1, 100.00, 100.00, p.validity_days,
               p.implementation FROM products p WHERE p.id = ?
        """,
        UUID.randomUUID(),
        id,
        cliente,
        producto);
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
