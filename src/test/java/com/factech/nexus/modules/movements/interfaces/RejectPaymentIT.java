package com.factech.nexus.modules.movements.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.PaymentFixtures;
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

/**
 * `RF-MV-004` — rechazar el pago pendiente de una venta; y lo que el pago cambia en confirmar
 * (`RF-MV-003`, `CA-MV-218` y `CA-MV-219`) y en anular (`RF-MV-005`, `CA-MV-220`).
 */
@AutoConfigureMockMvc
class RejectPaymentIT extends IntegrationTestBase {

  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String TARJETA = "01a061ba-3400-7002-9c4f-5e7ad7000021";
  private static final String PSE = "01a061ba-3400-7003-9c4f-5e7ad7000022";
  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";
  private static final String MOTIVO = "El banco devolvió la transferencia.";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID administrador;
  private UUID cliente;
  private UUID bot;

  @BeforeEach
  void sembrar() {
    limpiar();
    administrador = persona("rj-admin");
    cliente = persona("rj-cliente");
    bot = producto("RJ_BOT");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  @Test
  @DisplayName(
      "CA-MV-197 y CA-MV-198 — el pago pasa a RECHAZADO con su motivo, la venta sigue pendiente y"
          + " se puede volver a pagar")
  void rechazaYSePuedeVolverAPagar() throws Exception {
    UUID venta = venta("PENDIENTE");

    mvc.perform(rechazar(venta, MOTIVO).with(conPermiso("movements:reject-payment")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDIENTE"))
        .andExpect(jsonPath("$.payments[0].status").value("RECHAZADO"))
        .andExpect(jsonPath("$.payments[0].rejectionReason").value(MOTIVO))
        .andExpect(jsonPath("$.payments[0].rejectedAt").isNotEmpty());

    mvc.perform(
            post("/api/v1/movements/mine/{id}/payments", venta)
                .header("Idempotency-Key", "tras-rechazo-01")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethodId\":\"" + PSE + "\"}")
                .with(user(cliente.toString()).authorities(() -> "movements:retry-payment")))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("CA-MV-199 — rechazar por segunda vez es 409 y no cambia nada")
  void dosVeces() throws Exception {
    UUID venta = venta("PENDIENTE");
    mvc.perform(rechazar(venta, MOTIVO).with(conPermiso("movements:reject-payment")))
        .andExpect(status().isOk());

    mvc.perform(rechazar(venta, "Otro motivo").with(conPermiso("movements:reject-payment")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"));
    assertThat(
            jdbc.queryForObject(
                "SELECT rejection_reason FROM payments WHERE movement_id = ?", String.class, venta))
        .isEqualTo(MOTIVO);
  }

  @Test
  @DisplayName(
      "CA-MV-200 — una venta confirmada o anulada es 409 con su estado, y su pago no cambia")
  void noPendiente() throws Exception {
    UUID confirmada = venta("CONFIRMADA");
    UUID anulada = venta("ANULADA");

    mvc.perform(rechazar(confirmada, MOTIVO).with(conPermiso("movements:reject-payment")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("CONFIRMADA")));
    mvc.perform(rechazar(anulada, MOTIVO).with(conPermiso("movements:reject-payment")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ANULADA")));
    assertThat(estadoDelPago(confirmada)).isEqualTo("CONFIRMADO");
  }

  @Test
  @DisplayName("CA-MV-201 — una venta que no existe es 404")
  void inexistente() throws Exception {
    mvc.perform(rechazar(UUID.randomUUID(), MOTIVO).with(conPermiso("movements:reject-payment")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("CA-MV-202 — sin motivo, o en blanco, es 400 y el pago sigue pendiente")
  void sinMotivo() throws Exception {
    UUID venta = venta("PENDIENTE");
    mvc.perform(rechazar(venta, "   ").with(conPermiso("movements:reject-payment")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    mvc.perform(rechazar(venta, "x".repeat(501)).with(conPermiso("movements:reject-payment")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-003"));
    assertThat(estadoDelPago(venta)).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName(
      "CA-MV-203 — sin movements:reject-payment es 403, también con movements:confirm; sin token"
          + " 401")
  void permisos() throws Exception {
    UUID venta = venta("PENDIENTE");
    mvc.perform(rechazar(venta, MOTIVO).with(conPermiso("movements:confirm")))
        .andExpect(status().isForbidden());
    mvc.perform(rechazar(venta, MOTIVO)).andExpect(status().isUnauthorized());
    assertThat(estadoDelPago(venta)).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName(
      "CA-MV-204 y CA-MV-205 — queda auditado con el motivo, y el comprador lo ve en su detalle")
  void auditoriaYDetalle() throws Exception {
    UUID venta = venta("PENDIENTE");
    mvc.perform(rechazar(venta, MOTIVO).with(conPermiso("movements:reject-payment")))
        .andExpect(status().isOk());

    String asiento =
        jdbc.queryForObject(
            "SELECT changes::text FROM audit_change_log WHERE entity = 'payments'"
                + " AND entity_id = ? AND action = 'UPDATE'",
            String.class,
            venta);
    assertThat(asiento).contains("RECHAZADO").contains(MOTIVO);

    mvc.perform(
            get("/api/v1/movements/mine/{id}", venta)
                .with(user(cliente.toString()).authorities(() -> "movements:read-own")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payments[0].rejectionReason").value(MOTIVO));
  }

  @Test
  @DisplayName("CA-MV-218 — confirmar deja el pago CONFIRMADO con el mismo instante que la venta")
  void confirmarConfirmaElPago() throws Exception {
    UUID venta = venta("PENDIENTE");
    mvc.perform(
            post("/api/v1/movements/{id}/confirmation", venta)
                .with(conPermiso("movements:confirm")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payments[0].status").value("CONFIRMADO"));

    assertThat(
            jdbc.queryForObject(
                "SELECT p.confirmed_at = m.confirmed_at FROM payments p"
                    + " JOIN movements m ON m.id = p.movement_id WHERE m.id = ?",
                Boolean.class,
                venta))
        .isTrue();
  }

  @Test
  @DisplayName(
      "CA-MV-219 — una venta pendiente cuyo último pago se rechazó no se confirma: 409, y nada"
          + " cambia")
  void sinPagoPendienteNoSeConfirma() throws Exception {
    UUID venta = venta("PENDIENTE");
    mvc.perform(rechazar(venta, MOTIVO).with(conPermiso("movements:reject-payment")))
        .andExpect(status().isOk());

    mvc.perform(
            post("/api/v1/movements/{id}/confirmation", venta)
                .with(conPermiso("movements:confirm")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-006"));
    assertThat(
            jdbc.queryForObject("SELECT status FROM movements WHERE id = ?", String.class, venta))
        .isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName(
      "CA-MV-220 — anular deja el pago pendiente RECHAZADO con el motivo de la anulación; sin"
          + " pago pendiente se anula igual")
  void anularCierraElPago() throws Exception {
    UUID venta = venta("PENDIENTE");
    mvc.perform(
            post("/api/v1/movements/{id}/voiding", venta)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Cliente equivocado\"}")
                .with(conPermiso("movements:void")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payments[0].status").value("RECHAZADO"))
        .andExpect(
            jsonPath("$.payments[0].rejectionReason").value("Venta anulada: Cliente equivocado"));

    UUID sinPendiente = venta("PENDIENTE");
    mvc.perform(rechazar(sinPendiente, MOTIVO).with(conPermiso("movements:reject-payment")))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/movements/{id}/voiding", sinPendiente)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Ya no la quiere\"}")
                .with(conPermiso("movements:void")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ANULADA"))
        .andExpect(jsonPath("$.payments[0].rejectionReason").value(MOTIVO));
  }

  // ---------------------------------------------------------------------------

  private static MockHttpServletRequestBuilder rechazar(UUID venta, String motivo) {
    return post("/api/v1/movements/{id}/rejection", venta)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"" + motivo + "\"}");
  }

  private RequestPostProcessor conPermiso(String permiso) {
    return user(administrador.toString()).authorities(() -> permiso);
  }

  private String estadoDelPago(UUID venta) {
    return jdbc.queryForObject(
        "SELECT status FROM payments WHERE movement_id = ?", String.class, venta);
  }

  private UUID venta(String estado) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id, currency_id,
                               code, status, total_amount, discount_amount, payable_amount,
                               occurred_at, confirmed_at, voided_at, void_reason)
        VALUES (?, CAST(? AS uuid),
                (SELECT s.id FROM movement_type_statuses s
                  WHERE s.movement_type_id = CAST(? AS uuid) AND s.code = 'VALIDADO'),
                ?, CAST(? AS uuid), ?, ?, 100.00, 0, 100.00, CAST(? AS timestamptz),
                CASE WHEN ? = 'CONFIRMADA' THEN now() ELSE NULL END,
                CASE WHEN ? = 'ANULADA' THEN now() ELSE NULL END,
                CASE WHEN ? = 'ANULADA' THEN 'Sembrada anulada' ELSE NULL END)
        """,
        id,
        VENTA,
        VENTA,
        cliente,
        USD,
        "VTA-" + id.toString().substring(0, 8).toUpperCase(),
        estado,
        OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC).toString(),
        estado,
        estado,
        estado);
    PaymentFixtures.pagoDe(jdbc, id, TARJETA);
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
        bot);
    return id;
  }

  private void limpiar() {
    jdbc.update("DELETE FROM movement_details");
    jdbc.update("DELETE FROM movements");
    jdbc.update("DELETE FROM audit_change_log WHERE entity = 'payments'");
    jdbc.update("DELETE FROM products WHERE code LIKE 'RJ\\_%'");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'rj-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'rj-%'");
  }

  private UUID persona(String username) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                           must_change_password, status, country_id)
        VALUES (?, ?, ?, 'Nombre', 'Apellido', 'x', false, 'ACTIVO',
                (SELECT id FROM countries WHERE code = 'COL'))
        """,
        id,
        username,
        username + "@factech.co");
    darElSuelo(jdbc, id);
    return id;
  }

  private UUID producto(String codigo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description,"
            + " price, currency_id, validity_days, status)"
            + " VALUES ('TIENDA', 'MANUAL', ?, ?, 'BOT', ?, 'x', 100.00, CAST(? AS uuid), 30,"
            + " 'ACTIVO')",
        id,
        codigo,
        "Producto " + codigo,
        USD);
    return id;
  }
}
