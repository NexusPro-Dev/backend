package com.factech.nexus.modules.movements.interfaces;

import static com.factech.nexus.modules.movements.LedgerFixtures.USD;
import static com.factech.nexus.modules.movements.PointsFixtures.PSE;
import static com.factech.nexus.modules.movements.PointsFixtures.TARJETA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.FakeCardGateway;
import com.factech.nexus.modules.movements.FakeLocalPaymentGateway;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.modules.movements.PointsFixtures;
import com.factech.nexus.modules.movements.domain.service.LocalChargeSweep;
import com.factech.nexus.modules.movements.domain.service.LocalPaymentGateway.LocalChargeOrder;
import com.factech.nexus.modules.movements.domain.service.LocalPaymentGateway.Outcome;
import com.factech.nexus.testing.CommissionCleanup;
import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * La pasarela local, PayRetailers: cobrar (`RF-MV-048`), los avisos (`RF-MV-049`), el barrido
 * (`RF-MV-050`) y pagar un pendiente (`RF-MV-051`). Con {@link FakeLocalPaymentGateway}: ninguna
 * prueba llama a PayRetailers.
 *
 * <p>La conversión de Colombia se siembra con una moneda de prueba, {@code ZZC}: 1 USD = 4.150,5 al
 * cobrar. 10 USD son 41.505, redondeado hacia arriba a unidad entera (`RN-MV-063`).
 */
@AutoConfigureMockMvc
class LocalChargeIT extends IntegrationTestBase {

  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String AVISO = "/api/v1/movements/gateway-notifications/payretailers";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private FakeLocalPaymentGateway pasarela;
  @Autowired private FakeCardGateway tarjeta;
  @Autowired private LocalChargeSweep barrido;

  private UUID cliente;
  private UUID otro;
  private UUID admin;
  private UUID bot;
  private UUID cop;

  @BeforeEach
  void sembrar() {
    limpiar();
    pasarela.reiniciar();
    pasarela.encender(true);
    tarjeta.reiniciar();
    cliente = persona("lc-cliente");
    otro = persona("lc-otro");
    admin = persona("lc-admin");
    bot = producto("LC_BOT");
    PointsFixtures.tasa(jdbc, USD, "100", admin);
    cop = PointsFixtures.moneda(jdbc, "ZZC", true);
    conversion("4150.5", "3950");
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    pasarela.reiniciar();
    tarjeta.reiniciar();
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-048` — cobrar
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-600, CA-MV-601 y CA-MV-610 — volver a pagar con PSE abre el cobro en moneda local,"
          + " hacia arriba, guarda cómo se convirtió y deja la venta en USD")
  void volverAPagarConPse() throws Exception {
    UUID venta = ventaConPagoRechazado(cliente, "10.00");

    String cuerpo =
        mvc.perform(volverAPagar(venta, PSE, "lc-clave-0001"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDIENTE"))
            .andExpect(jsonPath("$.localCharge.gateway").value("PAYRETAILERS"))
            .andExpect(jsonPath("$.localCharge.checkoutUrl").value("https://pago.prueba/1"))
            .andExpect(jsonPath("$.localCharge.currency.code").value("ZZC"))
            // CA-MV-601: 10 × 4.150,5 = 41.505, hacia arriba a unidad entera.
            .andExpect(jsonPath("$.localCharge.amount").value(41505))
            .andExpect(jsonPath("$.cardCharge").doesNotExist())
            // CA-MV-610: la venta sigue en USD.
            .andExpect(jsonPath("$.currency.code").value("USD"))
            .andExpect(jsonPath("$.payableAmount").value(10.0))
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID pago = UUID.fromString(JsonPath.read(cuerpo, "$.localCharge.paymentId"));
    LocalChargeOrder orden = pasarela.abiertos().get(0);
    assertThat(orden.paymentId()).isEqualTo(pago);
    assertThat(orden.amountMinor()).isEqualTo(4150500L);
    assertThat(orden.currency()).isEqualTo("ZZC");
    assertThat(orden.payer().email()).isEqualTo("lc-cliente@factech.co");
    assertThat(orden.payer().countryAlpha3()).isEqualTo("COL");

    Map<String, Object> fila =
        jdbc.queryForMap(
            "SELECT provider_reference, charge_currency_id, charge_amount, conversion_rate_id,"
                + " checkout_url, status FROM payments WHERE id = ?",
            pago);
    assertThat(fila.get("provider_reference")).isEqualTo("pw_1");
    assertThat(fila.get("charge_currency_id")).isEqualTo(cop);
    assertThat(((Number) fila.get("charge_amount")).longValue()).isEqualTo(4150500L);
    assertThat(fila.get("conversion_rate_id")).isNotNull();
    assertThat(fila.get("checkout_url")).isEqualTo("https://pago.prueba/1");
    assertThat(fila.get("status")).isEqualTo("PENDIENTE");
    assertThat(estado(venta)).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName(
      "CA-MV-602 y CA-MV-605 — comprar puntos con PSE abre el cobro; repetir la petición devuelve"
          + " el mismo sin abrir otro")
  void comprarPuntosConPse() throws Exception {
    mvc.perform(comprarPuntos(cliente, "20.00", PSE, "lc-puntos-0001"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.localCharge.amount").value(83010));
    mvc.perform(comprarPuntos(cliente, "20.00", PSE, "lc-puntos-0001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.localCharge.checkoutUrl").value("https://pago.prueba/1"));
    assertThat(pasarela.abiertos()).hasSize(1);
  }

  @Test
  @DisplayName(
      "CA-MV-603 y CA-MV-604 — sin conversión es 409; la pasarela caída 503 y un rechazo suyo 422:"
          + " en los tres casos no queda ni compra ni pago")
  void sinCobroNoHayCompra() throws Exception {
    jdbc.update("DELETE FROM country_conversion_rates");
    mvc.perform(comprarPuntos(cliente, "20.00", PSE, "lc-puntos-0002"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-MV-063"));
    assertThat(compras()).isZero();

    conversion("4150.5", "3950");
    pasarela.caer(true);
    mvc.perform(comprarPuntos(cliente, "20.00", PSE, "lc-puntos-0003"))
        .andExpect(status().isServiceUnavailable());
    pasarela.caer(false);
    pasarela.rechazar("El documento no es válido.");
    mvc.perform(comprarPuntos(cliente, "20.00", PSE, "lc-puntos-0004"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail").value("El documento no es válido."));
    assertThat(compras()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isZero();
  }

  @Test
  @DisplayName(
      "CA-MV-606 y CA-MV-608 — apagada, PSE nace sin cobro y se confirma a mano; con cobro"
          + " abierto, confirmar a mano es conflicto")
  void apagadaYConfirmarAMano() throws Exception {
    pasarela.encender(false);
    String cuerpo =
        mvc.perform(comprarPuntos(cliente, "10.00", PSE, "lc-puntos-0005"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.localCharge").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID sinCobro = UUID.fromString(JsonPath.read(cuerpo, "$.id"));
    mvc.perform(confirmar(sinCobro)).andExpect(status().isOk());

    pasarela.encender(true);
    cuerpo =
        mvc.perform(comprarPuntos(cliente, "10.00", PSE, "lc-puntos-0006"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID conCobro = UUID.fromString(JsonPath.read(cuerpo, "$.id"));
    mvc.perform(confirmar(conCobro)).andExpect(status().isConflict());
    assertThat(estado(conCobro)).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName(
      "CA-MV-607 y CA-MV-609 — la venta registrada sin cobro con PSE no abre nada; la tarjeta"
          + " sigue yendo a su pasarela y PSE nunca va a la de la tarjeta")
  void tarjetaYPseNoSeCruzan() throws Exception {
    UUID registrada = venta(cliente, "PENDIENTE", "10.00", PSE);
    assertThat(referenciaDelPendiente(registrada)).isNull();

    tarjeta.encender(true);
    UUID venta = ventaConPagoRechazado(cliente, "10.00");
    mvc.perform(volverAPagar(venta, TARJETA, "lc-clave-0002"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.cardCharge.gateway").value("STRIPE"))
        .andExpect(jsonPath("$.localCharge").doesNotExist());
    assertThat(pasarela.abiertos()).isEmpty();

    UUID otra = ventaConPagoRechazado(cliente, "10.00");
    mvc.perform(volverAPagar(otra, PSE, "lc-clave-0003"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.localCharge.gateway").value("PAYRETAILERS"))
        .andExpect(jsonPath("$.cardCharge").doesNotExist());
    assertThat(tarjeta.abiertos()).hasSize(1);
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-049` — los avisos
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-612 y CA-MV-619 — un aviso, sin token, de un cobro que la pasarela da por aprobado"
          + " confirma el pago y la venta")
  void avisoAprobado() throws Exception {
    Cobro c = cobroAbierto("10.00");
    pasarela.responder(c.pago(), Outcome.APROBADO, "APPROVED", 4150500L, "ZZC");

    mvc.perform(avisar(c.pago(), "APPROVED")).andExpect(status().isOk());

    assertThat(estado(c.venta())).isEqualTo("CONFIRMADA");
    assertThat(estadoDelPago(c.pago())).isEqualTo("CONFIRMADO");
    assertThat(desenlace(c.pago())).isEqualTo("PROCESADO");
  }

  @Test
  @DisplayName(
      "CA-MV-613 — si la pasarela lo da por caducado, el pago se rechaza con el motivo y la venta"
          + " sigue pendiente")
  void avisoCaducado() throws Exception {
    Cobro c = cobroAbierto("10.00");
    pasarela.responder(c.pago(), Outcome.FINAL_NO_APROBADO, "EXPIRED", null, null);

    mvc.perform(avisar(c.pago(), "EXPIRED")).andExpect(status().isOk());

    assertThat(estadoDelPago(c.pago())).isEqualTo("RECHAZADO");
    assertThat(
            jdbc.queryForObject(
                "SELECT rejection_reason FROM payments WHERE id = ?", String.class, c.pago()))
        .contains("EXPIRED");
    assertThat(estado(c.venta())).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName(
      "CA-MV-614 — EL AVISO NO SE CREE: dice aprobado, la pasarela dice pendiente, y nada cambia")
  void elAvisoNoSeCree() throws Exception {
    Cobro c = cobroAbierto("10.00");
    pasarela.responder(c.pago(), Outcome.PENDIENTE, "PENDING", null, null);

    mvc.perform(avisar(c.pago(), "APPROVED")).andExpect(status().isOk());

    assertThat(estadoDelPago(c.pago())).isEqualTo("PENDIENTE");
    assertThat(estado(c.venta())).isEqualTo("PENDIENTE");
    assertThat(pasarela.consultas()).containsExactly(c.pago());
  }

  @Test
  @DisplayName(
      "CA-MV-615 — un aviso de un cobro que no es de ningún pago se guarda ignorado y no consulta"
          + " nada")
  void avisoAjeno() throws Exception {
    mvc.perform(avisar(UUID.randomUUID(), "APPROVED")).andExpect(status().isOk());

    assertThat(pasarela.consultas()).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT outcome FROM gateway_events WHERE gateway = 'PAYRETAILERS'", String.class))
        .isEqualTo("IGNORADO");
  }

  @Test
  @DisplayName("CA-MV-616 — el mismo aviso dos veces no confirma dos veces")
  void avisoRepetido() throws Exception {
    Cobro c = cobroAbierto("10.00");
    pasarela.responder(c.pago(), Outcome.APROBADO, "APPROVED", 4150500L, "ZZC");

    mvc.perform(avisar(c.pago(), "APPROVED")).andExpect(status().isOk());
    mvc.perform(avisar(c.pago(), "APPROVED")).andExpect(status().isOk());

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM gateway_events WHERE gateway = 'PAYRETAILERS'",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE movement_id = ? AND status = 'CONFIRMADO'",
                Integer.class,
                c.venta()))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "CA-MV-617 — anular con un cobro local abierto rechaza el pago en el acto; si después se"
          + " aprueba, queda COBRO_TARDIO y la venta sigue anulada")
  void cobroTardio() throws Exception {
    Cobro c = cobroAbierto("10.00");

    mvc.perform(anular(c.venta())).andExpect(status().isOk());
    assertThat(estadoDelPago(c.pago())).isEqualTo("RECHAZADO");

    pasarela.responder(c.pago(), Outcome.APROBADO, "APPROVED", 4150500L, "ZZC");
    mvc.perform(avisar(c.pago(), "APPROVED")).andExpect(status().isOk());

    assertThat(
            jdbc.queryForObject(
                "SELECT incident FROM payments WHERE id = ?", String.class, c.pago()))
        .isEqualTo("COBRO_TARDIO");
    assertThat(estado(c.venta())).isEqualTo("ANULADA");
  }

  @Test
  @DisplayName(
      "CA-MV-617 — volver a pagar con otro método con un cobro local abierto lo deja sin efecto y"
          + " abre el nuevo")
  void cambiarDeMetodo() throws Exception {
    Cobro c = cobroAbierto("10.00");

    mvc.perform(volverAPagar(c.venta(), TARJETA, "lc-clave-0009")).andExpect(status().isCreated());

    assertThat(estadoDelPago(c.pago())).isEqualTo("RECHAZADO");
    assertThat(
            jdbc.queryForObject(
                "SELECT rejection_reason FROM payments WHERE id = ?", String.class, c.pago()))
        .contains("pasarela local");
  }

  @Test
  @DisplayName(
      "CA-MV-618 — apagada, el aviso responde 503 sin guardar nada; un aviso ilegible es 400 sin"
          + " guardar nada")
  void avisoApagadoOIlegible() throws Exception {
    mvc.perform(post(AVISO).contentType(MediaType.APPLICATION_JSON).content("{\"x\":1}"))
        .andExpect(status().isBadRequest());
    pasarela.encender(false);
    mvc.perform(avisar(UUID.randomUUID(), "APPROVED")).andExpect(status().isServiceUnavailable());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM gateway_events", Integer.class)).isZero();
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-050` — el barrido
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-620 y CA-MV-621 — el barrido confirma un cobro aprobado sin aviso con más de diez"
          + " minutos, y no pregunta por uno reciente")
  void barridoConfirma() throws Exception {
    Cobro c = cobroAbierto("10.00");
    pasarela.responder(c.pago(), Outcome.APROBADO, "APPROVED", 4150500L, "ZZC");

    assertThat(barrido.barrer(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5))).isZero();
    assertThat(pasarela.consultas()).isEmpty();

    assertThat(barrido.barrer(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(11))).isEqualTo(1);
    assertThat(estado(c.venta())).isEqualTo("CONFIRMADA");
  }

  @Test
  @DisplayName(
      "CA-MV-622 y CA-MV-624 — si la pasarela no responde por un cobro, el otro se concilia; y el"
          + " barrido tras un aviso no confirma dos veces")
  void barridoParcialYSinDoble() throws Exception {
    Cobro uno = cobroAbierto("10.00");
    Cobro dos = cobroAbierto("10.00");
    pasarela.sinRespuestaPara(uno.pago());
    pasarela.responder(dos.pago(), Outcome.APROBADO, "APPROVED", 4150500L, "ZZC");

    barrido.barrer(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(11));

    assertThat(estadoDelPago(uno.pago())).isEqualTo("PENDIENTE");
    assertThat(estadoDelPago(dos.pago())).isEqualTo("CONFIRMADO");

    // CA-MV-624: un aviso del ya confirmado no lo confirma otra vez.
    mvc.perform(avisar(dos.pago(), "APPROVED")).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE movement_id = ? AND status = 'CONFIRMADO'",
                Integer.class,
                dos.venta()))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("CA-MV-623 — apagada, el barrido no hace nada")
  void barridoApagado() throws Exception {
    cobroAbierto("10.00");
    pasarela.encender(false);
    assertThat(barrido.barrer(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(11))).isZero();
    assertThat(pasarela.consultas()).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-051` — pagar un pendiente propio
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-625 y CA-MV-626 — una venta propia con PSE sin cobro lo abre; pedirlo otra vez"
          + " devuelve el mismo")
  void pagarPendiente() throws Exception {
    UUID venta = venta(cliente, "PENDIENTE", "10.00", PSE);

    mvc.perform(pagarPendiente(venta, cliente))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.checkoutUrl").value("https://pago.prueba/1"))
        .andExpect(jsonPath("$.amount").value(41505));
    mvc.perform(pagarPendiente(venta, cliente))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.checkoutUrl").value("https://pago.prueba/1"));
    assertThat(pasarela.abiertos()).hasSize(1);
  }

  @Test
  @DisplayName(
      "CA-MV-627 a CA-MV-629 — de otro es 404; con otro método 409; sin conversión 409; apagada"
          + " 503; sin permiso 403 y sin token 401")
  void pagarPendienteRechazos() throws Exception {
    UUID venta = venta(cliente, "PENDIENTE", "10.00", PSE);
    mvc.perform(pagarPendiente(venta, otro)).andExpect(status().isNotFound());

    UUID conTarjeta = venta(cliente, "PENDIENTE", "10.00", TARJETA);
    mvc.perform(pagarPendiente(conTarjeta, cliente)).andExpect(status().isConflict());

    jdbc.update("DELETE FROM country_conversion_rates");
    mvc.perform(pagarPendiente(venta, cliente))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RN-MV-063"));

    pasarela.encender(false);
    mvc.perform(pagarPendiente(venta, cliente)).andExpect(status().isServiceUnavailable());

    mvc.perform(
            post("/api/v1/movements/mine/{id}/local-charge", venta)
                .with(user(cliente.toString()).authorities(() -> "movements:read")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/movements/mine/{id}/local-charge", venta))
        .andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------

  private record Cobro(UUID venta, UUID pago) {}

  /** Una venta propia con su cobro local abierto, por volver a pagar con PSE. */
  private Cobro cobroAbierto(String importe) throws Exception {
    UUID venta = ventaConPagoRechazado(cliente, importe);
    String cuerpo =
        mvc.perform(volverAPagar(venta, PSE, "lc-" + UUID.randomUUID()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new Cobro(venta, UUID.fromString(JsonPath.read(cuerpo, "$.localCharge.paymentId")));
  }

  private MockHttpServletRequestBuilder avisar(UUID pago, String estado) {
    return post(AVISO)
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"uid\":\"tx-%s\",\"type\":\"payment\",\"status\":\"%s\",\"trackingId\":\"%s\"}"
                .formatted(pago, estado, pago));
  }

  private MockHttpServletRequestBuilder volverAPagar(UUID venta, String metodo, String clave) {
    return post("/api/v1/movements/mine/{id}/payments", venta)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"paymentMethodId\":\"" + metodo + "\"}")
        .header("Idempotency-Key", clave)
        .with(user(cliente.toString()).authorities(() -> "movements:retry-payment"));
  }

  private MockHttpServletRequestBuilder pagarPendiente(UUID movimiento, UUID quien) {
    return post("/api/v1/movements/mine/{id}/local-charge", movimiento)
        .with(user(quien.toString()).authorities(() -> "movements:pay-pending-locally"));
  }

  private MockHttpServletRequestBuilder comprarPuntos(
      UUID quien, String importe, String metodo, String clave) {
    return post("/api/v1/movements/mine/points-purchases")
        .header("Idempotency-Key", clave)
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"currencyId\":\"%s\",\"amount\":%s,\"paymentMethodId\":\"%s\"}"
                .formatted(USD, importe, metodo))
        .with(user(quien.toString()).authorities(() -> "movements:buy-points"));
  }

  private MockHttpServletRequestBuilder confirmar(UUID movimiento) {
    return post(
            "/api/v1/movements/payments/{id}/confirmation",
            PaymentFixtures.pagoAConciliar(jdbc, movimiento))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}")
        .with(user(admin.toString()).authorities(() -> "movements:confirm-payment"));
  }

  private MockHttpServletRequestBuilder anular(UUID venta) {
    return post("/api/v1/movements/{id}/voiding", venta)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"Cliente equivocado\"}")
        .with(user(admin.toString()).authorities(() -> "movements:void"));
  }

  private void conversion(String cobro, String retiro) {
    jdbc.update(
        "INSERT INTO country_conversion_rates (id, country_id, currency_id, base_currency_id,"
            + " pay_in_price, payout_price, valid_from, created_by)"
            + " VALUES (gen_random_uuid(), (SELECT id FROM countries WHERE code = 'COL'), ?,"
            + " CAST(? AS uuid), CAST(? AS numeric), CAST(? AS numeric), now() - interval '1 day',"
            + " ?)",
        cop,
        USD,
        cobro,
        retiro,
        admin);
  }

  private String referenciaDelPendiente(UUID movimiento) {
    return jdbc.queryForObject(
        "SELECT provider_reference FROM payments WHERE movement_id = ? AND status = 'PENDIENTE'",
        String.class,
        movimiento);
  }

  private String estado(UUID movimiento) {
    return jdbc.queryForObject(
        "SELECT status FROM movements WHERE id = ?", String.class, movimiento);
  }

  private String estadoDelPago(UUID pago) {
    return jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, pago);
  }

  private String desenlace(UUID pago) {
    return jdbc.queryForObject(
        "SELECT outcome FROM gateway_events WHERE payment_id = ? LIMIT 1", String.class, pago);
  }

  private int compras() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM movements m JOIN movement_types t ON t.id = m.movement_type_id"
            + " WHERE t.code = 'COMPRA_PUNTOS'",
        Integer.class);
  }

  private UUID ventaConPagoRechazado(UUID sujeto, String importe) {
    UUID venta = venta(sujeto, "PENDIENTE", importe, TARJETA);
    jdbc.update(
        "UPDATE payments SET status = 'RECHAZADO', rejected_at = now(),"
            + " rejection_reason = 'Fondos insuficientes' WHERE movement_id = ?",
        venta);
    return venta;
  }

  private UUID venta(UUID sujeto, String estado, String importe, String metodo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO movements (id, movement_type_id, type_status_id, user_id, currency_id,
                               code, status, total_amount, discount_amount, payable_amount,
                               occurred_at, confirmed_at)
        VALUES (?, CAST(? AS uuid),
                (SELECT s.id FROM movement_type_statuses s
                  WHERE s.movement_type_id = CAST(? AS uuid) AND s.code = 'VALIDADO'),
                ?, CAST(? AS uuid), ?, ?, CAST(? AS numeric) * 100, 0, CAST(? AS numeric) * 100,
                CAST(? AS timestamptz),
                CASE WHEN ? = 'CONFIRMADA' THEN now() ELSE NULL END)
        """,
        id,
        VENTA,
        VENTA,
        sujeto,
        USD,
        "VTA-" + id.toString().substring(0, 8).toUpperCase(),
        estado,
        importe,
        importe,
        OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC).toString(),
        estado);
    PaymentFixtures.pagoDe(jdbc, id, metodo);
    jdbc.update(
        """
        INSERT INTO movement_details (id, movement_id, product_id, seller_id, product_name,
                                      product_description, quantity, unit_price, line_amount,
                                      validity_days, implementation)
        SELECT ?, ?, p.id, ?, p.name, p.description, 1, CAST(? AS numeric) * 100,
               CAST(? AS numeric) * 100, p.validity_days, p.implementation
          FROM products p WHERE p.id = ?
        """,
        UUID.randomUUID(),
        id,
        sujeto,
        importe,
        importe,
        bot);
    return id;
  }

  /**
   * Los pagos con cobro local referencian la conversión: se sueltan antes de borrarla, y la
   * conversión antes que la moneda de prueba que borra {@code PointsFixtures}.
   */
  private void limpiar() {
    jdbc.update(
        "UPDATE payments SET charge_currency_id = NULL, charge_amount = NULL,"
            + " conversion_rate_id = NULL, checkout_url = NULL WHERE charge_currency_id IS NOT NULL");
    jdbc.update("DELETE FROM country_conversion_rates");
    CommissionCleanup.limpiar(jdbc);
    PointsFixtures.limpiar(jdbc);
    jdbc.update("DELETE FROM gateway_events");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'MV'");
    jdbc.update("DELETE FROM products WHERE code LIKE 'LC\\_%'");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'lc-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'lc-%'");
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
            + " VALUES ('TIENDA', 'AUTOMATICA', ?, ?, 'BOT', ?, 'x', 10000, CAST(? AS uuid), 30,"
            + " 'ACTIVO')",
        id,
        codigo,
        "Producto " + codigo,
        USD);
    return id;
  }
}
