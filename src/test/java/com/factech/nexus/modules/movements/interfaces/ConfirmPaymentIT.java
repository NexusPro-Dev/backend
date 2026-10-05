package com.factech.nexus.modules.movements.interfaces;

import static com.factech.nexus.modules.movements.LedgerFixtures.USD;
import static com.factech.nexus.modules.movements.LedgerFixtures.saldo;
import static com.factech.nexus.testing.ConcurrencyHarness.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.modules.movements.PointsFixtures;
import com.factech.nexus.testing.ConcurrencyHarness.Outcome;
import com.jayway.jsonpath.JsonPath;
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

/**
 * `RF-MV-044` — confirmar un pago pendiente, y su movimiento con él (`RN-MV-061`); y las rutas por
 * movimiento que sustituye, que ya no existen (`CA-MV-516`, `CA-MV-518`).
 *
 * <p><b>Las ventas se siembran por SQL</b>, como en {@code ConfirmSaleIT}: lo que se prueba es la
 * entrada por el pago; la entrega la prueba aquella suite, por esta misma ruta. <b>Las compras de
 * puntos se hacen por su ruta</b>, como en {@code PointsPurchaseIT}, con la pasarela apagada: nacen
 * pendientes y sin cobro.
 */
@AutoConfigureMockMvc
class ConfirmPaymentIT extends IntegrationTestBase {

  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String RETIRO = "01a0f6c0-8800-7003-9c4f-5e7ad7000012";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID cliente;
  private UUID administrador;
  private UUID bot;

  @BeforeEach
  void sembrar() {
    limpiar();
    cliente = persona("cp-cliente");
    administrador = persona("cp-admin");
    PointsFixtures.tasa(jdbc, USD, "100", administrador);
    bot = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description,"
            + " price, currency_id, validity_days, status)"
            + " VALUES ('TIENDA', 'AUTOMATICA', ?, 'CP_BOT', 'BOT', 'Bot CP', 'Un bot', 10000,"
            + " CAST(? AS uuid), 15, 'ACTIVO')",
        bot,
        USD);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // El efecto de cada tipo
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-495 — el pago de una venta: pago CONFIRMADO y venta CONFIRMADA con el mismo"
          + " instante, la línea entregada, y la respuesta es el detalle")
  void confirmaElPagoDeUnaVenta() throws Exception {
    UUID venta = venta(cliente, PointsFixtures.TARJETA);
    UUID pago = PaymentFixtures.pagoAConciliar(jdbc, venta);

    mvc.perform(confirmar(pago, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(venta.toString()))
        .andExpect(jsonPath("$.type").value("VENTA"))
        .andExpect(jsonPath("$.status").value("CONFIRMADA"))
        .andExpect(jsonPath("$.lines[0].deliveryStatus").value("ENTREGADA"))
        .andExpect(jsonPath("$.payments[0].id").value(pago.toString()))
        .andExpect(jsonPath("$.payments[0].status").value("CONFIRMADO"));

    assertThat(
            jdbc.queryForObject(
                "SELECT m.confirmed_at = p.confirmed_at FROM movements m"
                    + " JOIN payments p ON p.movement_id = m.id WHERE p.id = ?",
                Boolean.class,
                pago))
        .isTrue();
  }

  @Test
  @DisplayName(
      "CA-MV-496 — el pago de una compra de puntos: pago CONFIRMADO, compra CONFIRMADA y los"
          + " puntos congelados abonados")
  void confirmaElPagoDeUnaCompraDePuntos() throws Exception {
    UUID compra = comprarPuntos("10.00");
    // La tasa cambia después de comprar: se abonan los congelados (`RN-MV-051`).
    PointsFixtures.tasa(jdbc, USD, "500", administrador);

    mvc.perform(confirmar(PaymentFixtures.pagoAConciliar(jdbc, compra), null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(compra.toString()))
        .andExpect(jsonPath("$.type").value("COMPRA_PUNTOS"))
        .andExpect(jsonPath("$.status").value("CONFIRMADA"))
        .andExpect(jsonPath("$.payments[0].status").value("CONFIRMADO"));

    assertThat(saldo(jdbc, cliente, "PUNTOS")).isEqualByComparingTo("1000.00");
  }

  @Test
  @DisplayName(
      "CA-MV-497 — cualquier método sin cobro abierto: una transferencia (PSE) y una tarjeta"
          + " sin cobro se confirman igual")
  void cualquierMetodo() throws Exception {
    UUID conPse = venta(cliente, PointsFixtures.PSE);
    UUID conTarjeta = venta(cliente, PointsFixtures.TARJETA);

    mvc.perform(confirmar(PaymentFixtures.pagoAConciliar(jdbc, conPse), null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMADA"));
    mvc.perform(confirmar(PaymentFixtures.pagoAConciliar(jdbc, conTarjeta), null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMADA"));
  }

  @Test
  @DisplayName(
      "CA-MV-498 — la referencia queda en el pago, también en una venta; sin ella, sin"
          + " referencia; de más de 120 caracteres, 400 y nada cambia")
  void laReferencia() throws Exception {
    UUID conReferencia = venta(cliente, PointsFixtures.PSE);
    UUID sinReferencia = venta(cliente, PointsFixtures.PSE);
    UUID larga = venta(cliente, PointsFixtures.PSE);

    mvc.perform(
            confirmar(
                PaymentFixtures.pagoAConciliar(jdbc, conReferencia),
                "{\"providerReference\":\"  TRF-0042  \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payments[0].providerReference").value("TRF-0042"));
    mvc.perform(
            confirmar(
                PaymentFixtures.pagoAConciliar(jdbc, sinReferencia),
                "{\"providerReference\":\"   \"}"))
        .andExpect(status().isOk());
    assertThat(referenciaDe(sinReferencia)).isNull();

    UUID pagoLargo = PaymentFixtures.pagoAConciliar(jdbc, larga);
    mvc.perform(confirmar(pagoLargo, "{\"providerReference\":\"" + "x".repeat(121) + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("VAL-002"));
    assertThat(estadoDelPago(pagoLargo)).isEqualTo("PENDIENTE");
    assertThat(estadoDe(larga)).isEqualTo("PENDIENTE");
  }

  // ---------------------------------------------------------------------------
  // Lo que no se confirma
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("CA-MV-499 — un pago que no existe es 404; un identificador malformado, 400")
  void noExiste() throws Exception {
    mvc.perform(confirmar(UUID.randomUUID(), null)).andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/movements/payments/{id}/confirmation", "no-es-un-uuid")
                .with(conPermiso("movements:confirm-payment")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "CA-MV-500 — un pago ya confirmado o rechazado es 409 con su estado, y la entrega no se"
          + " repite")
  void yaResuelto() throws Exception {
    UUID venta = venta(cliente, PointsFixtures.PSE);
    UUID pago = PaymentFixtures.pagoAConciliar(jdbc, venta);
    mvc.perform(confirmar(pago, null)).andExpect(status().isOk());
    int posesiones = posesionesDe(cliente);

    mvc.perform(confirmar(pago, null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"))
        .andExpect(jsonPath("$.detail").value(containsString("CONFIRMADO")));
    assertThat(posesionesDe(cliente)).isEqualTo(posesiones);

    UUID otra = venta(cliente, PointsFixtures.PSE);
    UUID rechazado = PaymentFixtures.pagoAConciliar(jdbc, otra);
    jdbc.update(
        "UPDATE payments SET status = 'RECHAZADO', rejected_at = now(),"
            + " rejection_reason = 'No entró' WHERE id = ?",
        rechazado);
    mvc.perform(confirmar(rechazado, null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-003"))
        .andExpect(jsonPath("$.detail").value(containsString("RECHAZADO")));
    assertThat(estadoDe(otra)).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName("CA-MV-501 — el pago de un retiro es 409, y nada cambia")
  void unRetiro() throws Exception {
    UUID retiro = movimiento(RETIRO, cliente);
    PaymentFixtures.pagoDe(jdbc, retiro, PointsFixtures.PSE);
    UUID pago = PaymentFixtures.pagoAConciliar(jdbc, retiro);

    mvc.perform(confirmar(pago, null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-002"));
    assertThat(estadoDelPago(pago)).isEqualTo("PENDIENTE");
    assertThat(estadoDe(retiro)).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName("CA-MV-502 — un pago con cobro abierto en la pasarela es 409, y nada cambia")
  void conCobroAbierto() throws Exception {
    UUID venta = venta(cliente, PointsFixtures.TARJETA);
    UUID pago = PaymentFixtures.pagoAConciliar(jdbc, venta);
    jdbc.update("UPDATE payments SET provider_reference = 'pi_abierto' WHERE id = ?", pago);

    mvc.perform(confirmar(pago, null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
    assertThat(estadoDelPago(pago)).isEqualTo("PENDIENTE");
    assertThat(estadoDe(venta)).isEqualTo("PENDIENTE");
    assertThat(posesionesDe(cliente)).isZero();
  }

  // ---------------------------------------------------------------------------
  // A la vez
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-503 — dos confirmaciones simultáneas del mismo pago: una 200, otra 409, una"
          + " entrega y un abono")
  void dosALaVez() throws Exception {
    UUID venta = venta(cliente, PointsFixtures.PSE);
    UUID pagoDeVenta = PaymentFixtures.pagoAConciliar(jdbc, venta);
    List<Outcome<Integer>> ventas = runTogether(2, i -> estado(confirmar(pagoDeVenta, null)));
    assertThat(ventas).allMatch(Outcome::succeeded);
    assertThat(ventas).extracting(Outcome::value).containsExactlyInAnyOrder(200, 409);
    assertThat(posesionesDe(cliente)).isEqualTo(1);

    UUID compra = comprarPuntos("5.00");
    UUID pagoDeCompra = PaymentFixtures.pagoAConciliar(jdbc, compra);
    List<Outcome<Integer>> compras = runTogether(2, i -> estado(confirmar(pagoDeCompra, null)));
    assertThat(compras).allMatch(Outcome::succeeded);
    assertThat(compras).extracting(Outcome::value).containsExactlyInAnyOrder(200, 409);
    assertThat(saldo(jdbc, cliente, "PUNTOS")).isEqualByComparingTo("500.00");
  }

  @Test
  @DisplayName(
      "CA-MV-504 — confirmar y rechazar el mismo pago a la vez: solo uno se aplica, y el"
          + " movimiento queda coherente con él")
  void confirmarYRechazarALaVez() throws Exception {
    UUID compra = comprarPuntos("5.00");
    UUID pago = PaymentFixtures.pagoAConciliar(jdbc, compra);

    List<Callable<Integer>> tareas =
        List.of(
            () -> estado(confirmar(pago, null)),
            () ->
                estado(
                    post("/api/v1/movements/payments/{id}/rejection", pago)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"No entró\"}")
                        .with(conPermiso("movements:reject-payment"))));
    List<Outcome<Integer>> resultados = runTogether(tareas);

    assertThat(resultados).allMatch(Outcome::succeeded);
    assertThat(resultados).extracting(Outcome::value).containsExactlyInAnyOrder(200, 409);
    String pagoQueda = estadoDelPago(pago);
    String compraQueda = estadoDe(compra);
    if ("CONFIRMADO".equals(pagoQueda)) {
      assertThat(compraQueda).isEqualTo("CONFIRMADA");
      assertThat(saldo(jdbc, cliente, "PUNTOS")).isEqualByComparingTo("500.00");
    } else {
      assertThat(pagoQueda).isEqualTo("RECHAZADO");
      assertThat(compraQueda).isEqualTo("RECHAZADA");
      assertThat(saldo(jdbc, cliente, "PUNTOS")).isEqualByComparingTo("0");
    }
  }

  // ---------------------------------------------------------------------------
  // Permiso, auditoría y las rutas retiradas
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-505 — sin movements:confirm-payment es 403, también con reject-payment; sin token"
          + " 401; lo portan SUPERADMIN y ADMIN")
  void permiso() throws Exception {
    UUID pago = PaymentFixtures.pagoAConciliar(jdbc, venta(cliente, PointsFixtures.PSE));

    mvc.perform(
            post("/api/v1/movements/payments/{id}/confirmation", pago)
                .with(conPermiso("movements:reject-payment")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/movements/payments/{id}/confirmation", pago))
        .andExpect(status().isUnauthorized());
    assertThat(estadoDelPago(pago)).isEqualTo("PENDIENTE");

    assertThat(
            jdbc.queryForList(
                "SELECT r.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id"
                    + " JOIN permissions p ON p.id = rp.permission_id"
                    + " WHERE p.code = 'movements:confirm-payment' ORDER BY r.code",
                String.class))
        .containsExactly("ADMIN", "SUPERADMIN");
  }

  @Test
  @DisplayName(
      "CA-MV-506 — queda auditado como en RF-MV-003 y RF-MV-028, con quien confirmó y la"
          + " referencia")
  void auditado() throws Exception {
    UUID venta = venta(cliente, PointsFixtures.PSE);
    mvc.perform(
            confirmar(
                PaymentFixtures.pagoAConciliar(jdbc, venta), "{\"providerReference\":\"TRF-9\"}"))
        .andExpect(status().isOk());
    UUID compra = comprarPuntos("1.00");
    mvc.perform(confirmar(PaymentFixtures.pagoAConciliar(jdbc, compra), null))
        .andExpect(status().isOk());

    for (UUID movimiento : List.of(venta, compra)) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM audit_change_log WHERE entity = 'movements'"
                      + " AND entity_id = ? AND action = 'UPDATE' AND actor_id = ?"
                      + " AND changes -> 'after' ->> 'status' = 'CONFIRMADA'",
                  Integer.class,
                  movimiento,
                  administrador))
          .isEqualTo(1);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT changes -> 'after' ->> 'provider_reference' FROM audit_change_log"
                    + " WHERE entity = 'movements' AND entity_id = ? AND action = 'UPDATE'",
                String.class,
                venta))
        .isEqualTo("TRF-9");
  }

  @Test
  @DisplayName(
      "CA-MV-516 y CA-MV-518 — las rutas de confirmar por movimiento ya no existen: 404, y"
          + " nada cambia")
  void lasRutasRetiradas() throws Exception {
    UUID venta = venta(cliente, PointsFixtures.PSE);
    UUID compra = comprarPuntos("1.00");

    mvc.perform(
            post("/api/v1/movements/{id}/confirmation", venta)
                .with(conPermiso("movements:confirm-payment")))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/movements/{id}/points-purchase-confirmation", compra)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(conPermiso("movements:confirm-payment")))
        .andExpect(status().isNotFound());
    assertThat(estadoDe(venta)).isEqualTo("PENDIENTE");
    assertThat(estadoDe(compra)).isEqualTo("PENDIENTE");
  }

  // ---------------------------------------------------------------------------

  private MockHttpServletRequestBuilder confirmar(UUID pago, String cuerpo) {
    MockHttpServletRequestBuilder peticion =
        post("/api/v1/movements/payments/{id}/confirmation", pago)
            .with(conPermiso("movements:confirm-payment"));
    return cuerpo == null
        ? peticion
        : peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor conPermiso(
      String permiso) {
    return user(administrador.toString()).authorities(() -> permiso);
  }

  private int estado(MockHttpServletRequestBuilder peticion) throws Exception {
    return mvc.perform(peticion).andReturn().getResponse().getStatus();
  }

  private UUID comprarPuntos(String importe) throws Exception {
    String cuerpo =
        mvc.perform(
                post("/api/v1/movements/mine/points-purchases")
                    .header("Idempotency-Key", "cp-" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"currencyId\":\"%s\",\"amount\":%s,\"paymentMethodId\":\"%s\"}"
                            .formatted(USD, importe, PointsFixtures.TARJETA))
                    .with(user(cliente.toString()).authorities(() -> "movements:buy-points")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(cuerpo, "$.id"));
  }

  /** Una venta pendiente con una línea del bot y un pago pendiente con el método dado. */
  private UUID venta(UUID sujeto, String metodo) {
    UUID id = movimiento(VENTA, sujeto);
    PaymentFixtures.pagoDe(jdbc, id, metodo);
    jdbc.update(
        """
        INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                      product_description, quantity, unit_price, line_amount,
                                      validity_days, implementation)
        SELECT gen_random_uuid(), ?, p.id, ?, p.name, p.description, 1, 10000, 10000,
               p.validity_days, p.implementation
          FROM products p WHERE p.id = ?
        """,
        id,
        sujeto,
        bot);
    return id;
  }

  /** Una cabecera pendiente del tipo dado, sin pagos. */
  private UUID movimiento(String tipo, UUID sujeto) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id, currency_id, code,
                               status, total_amount, discount_amount, payable_amount, occurred_at)
        VALUES (?, CAST(? AS uuid),
                (SELECT s.id FROM movement_type_statuses s
                  WHERE s.movement_type_id = CAST(? AS uuid)
                    AND s.code IN ('VALIDADO', 'REGISTRADO')),
                ?, CAST(? AS uuid), ?, 'PENDIENTE', 10000, 0, 10000, now())
        """,
        id,
        tipo,
        tipo,
        sujeto,
        USD,
        "CP-" + id.toString().substring(0, 8).toUpperCase());
    return id;
  }

  private String estadoDe(UUID movimiento) {
    return jdbc.queryForObject(
        "SELECT status FROM movements WHERE id = ?", String.class, movimiento);
  }

  private String estadoDelPago(UUID pago) {
    return jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, pago);
  }

  private String referenciaDe(UUID movimiento) {
    return jdbc.queryForObject(
        "SELECT provider_reference FROM payments WHERE movement_id = ?", String.class, movimiento);
  }

  /** Lo que la persona TIENE por una compra: la entrega escribe una fila con su línea. */
  private int posesionesDe(UUID persona) {
    Integer total =
        jdbc.queryForObject(
            "SELECT count(*) FROM user_products WHERE user_id = ?"
                + " AND movement_detail_id IS NOT NULL",
            Integer.class,
            persona);
    return total == null ? 0 : total;
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

  private void limpiar() {
    PointsFixtures.limpiar(jdbc);
    jdbc.update("DELETE FROM products WHERE code LIKE 'CP\\_%'");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'cp-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'cp-%'");
  }
}
