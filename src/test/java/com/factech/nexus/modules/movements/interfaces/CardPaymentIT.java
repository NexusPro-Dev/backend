package com.factech.nexus.modules.movements.interfaces;

import static com.factech.nexus.modules.movements.LedgerFixtures.USD;
import static com.factech.nexus.modules.movements.LedgerFixtures.saldo;
import static com.factech.nexus.modules.movements.PointsFixtures.PSE;
import static com.factech.nexus.modules.movements.PointsFixtures.TARJETA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factech.nexus.IntegrationTestBase;
import com.factech.nexus.modules.movements.FakeCardGateway;
import com.factech.nexus.modules.movements.PaymentFixtures;
import com.factech.nexus.modules.movements.PointsFixtures;
import com.factech.nexus.modules.movements.StripeEventsJson;
import com.factech.nexus.modules.movements.domain.service.CardGateway.ChargeOrder;
import com.factech.nexus.modules.movements.domain.service.GatewayEventProcessor;
import com.factech.nexus.testing.CommissionCleanup;
import com.jayway.jsonpath.JsonPath;
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
 * La tarjeta por la pasarela: cobrar (`RF-MV-040`), las notificaciones (`RF-MV-041`), pagar un
 * pendiente (`RF-MV-042`) y las enmiendas de `RF-MV-003` a `RF-MV-006`, `RF-MV-018` y `RF-MV-027` a
 * `RF-MV-029`. Con {@link FakeCardGateway}: ninguna prueba llama a Stripe.
 */
@AutoConfigureMockMvc
class CardPaymentIT extends IntegrationTestBase {

  private static final String VENTA = "01a061ba-3400-7001-9c4f-5e7ad7000011";
  private static final String WEBHOOK = "/api/v1/movements/gateway-notifications/stripe";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private FakeCardGateway pasarela;
  @Autowired private GatewayEventProcessor procesador;

  private UUID cliente;
  private UUID otro;
  private UUID admin;
  private UUID bot;

  @BeforeEach
  void sembrar() {
    limpiar();
    pasarela.reiniciar();
    pasarela.encender(true);
    cliente = persona("cp-cliente");
    otro = persona("cp-otro");
    admin = persona("cp-admin");
    bot = producto("CP_BOT");
    PointsFixtures.tasa(jdbc, USD, "100", admin);
  }

  @AfterEach
  void devolverElEstadoASuSitio() {
    pasarela.reiniciar();
    limpiar();
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-040` — cobrar con tarjeta
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-428, CA-MV-430, CA-MV-431 y CA-MV-473 — volver a pagar con tarjeta abre el cobro con"
          + " la clave del pago y su origen, devuelve el secreto y no confirma nada")
  void volverAPagarConTarjeta() throws Exception {
    UUID venta = ventaConPagoRechazado(cliente, "100.00");

    String cuerpo =
        mvc.perform(volverAPagar(venta, TARJETA, "cp-clave-0001").with(comprador(cliente)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDIENTE"))
            .andExpect(jsonPath("$.cardCharge.gateway").value("STRIPE"))
            .andExpect(jsonPath("$.cardCharge.clientSecret").value("pi_prueba_1_secret_prueba"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    ChargeOrder orden = pasarela.abiertos().get(0);
    assertThat(orden.amountMinor()).isEqualTo(10000L);
    assertThat(orden.currency()).isEqualTo("USD");
    assertThat(orden.idempotencyKey()).isEqualTo("cp-clave-0001");
    assertThat(orden.movementId()).isEqualTo(venta);
    UUID pago = UUID.fromString(JsonPath.read(cuerpo, "$.cardCharge.paymentId"));
    assertThat(orden.paymentId()).isEqualTo(pago);
    assertThat(referenciaDe(pago)).isEqualTo("pi_prueba_1");
    // CA-MV-431: nada confirmado.
    assertThat(estado(venta)).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName(
      "CA-MV-429 y CA-MV-477 — comprar puntos con tarjeta abre el cobro por el importe de la"
          + " compra; repetir la petición devuelve el mismo cobro")
  void comprarPuntosConTarjeta() throws Exception {
    mvc.perform(comprarPuntos(cliente, "25.00", TARJETA, "cp-puntos-0001"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDIENTE"))
        .andExpect(jsonPath("$.cardCharge.clientSecret").value("pi_prueba_1_secret_prueba"));
    assertThat(pasarela.abiertos()).hasSize(1);
    assertThat(pasarela.abiertos().get(0).amountMinor()).isEqualTo(2500L);

    mvc.perform(comprarPuntos(cliente, "25.00", TARJETA, "cp-puntos-0001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cardCharge.clientSecret").value("pi_prueba_1_secret_prueba"));
    assertThat(pasarela.abiertos()).hasSize(1);
  }

  @Test
  @DisplayName(
      "CA-MV-432 y CA-MV-433 — la pasarela caída es 503 y por debajo del mínimo es 422: en los dos"
          + " casos no queda ni movimiento ni pago")
  void sinCobroNoHayCompra() throws Exception {
    pasarela.caer(true);
    mvc.perform(comprarPuntos(cliente, "25.00", TARJETA, "cp-puntos-0002"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errors[0].code").value("RN-MV-057"));
    assertThat(compras()).isZero();

    pasarela.caer(false);
    mvc.perform(comprarPuntos(cliente, "0.40", TARJETA, "cp-puntos-0003"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("0.50")));
    assertThat(compras()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isZero();
  }

  @Test
  @DisplayName(
      "CA-MV-435 y CA-MV-436 — con la pasarela apagada o con otro método no hay cobro, y la"
          + " compra con tarjeta se confirma a mano")
  void apagadaUOtroMetodo() throws Exception {
    mvc.perform(comprarPuntos(cliente, "10.00", PSE, "cp-puntos-0004"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.cardCharge").doesNotExist());

    pasarela.encender(false);
    String cuerpo =
        mvc.perform(comprarPuntos(cliente, "10.00", TARJETA, "cp-puntos-0005"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cardCharge").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(pasarela.abiertos()).isEmpty();
    UUID compra = UUID.fromString(JsonPath.read(cuerpo, "$.id"));
    mvc.perform(confirmarPuntos(compra)).andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "CA-MV-437 — ninguna ruta acepta datos de tarjeta: un cuerpo con número de tarjeta es 400 y"
          + " el contrato no declara campos de tarjeta")
  void sinDatosDeTarjeta() throws Exception {
    mvc.perform(
            post("/api/v1/movements/mine/points-purchases")
                .header("Idempotency-Key", "cp-puntos-0006")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currencyId\":\""
                        + USD
                        + "\",\"amount\":10.00,\"paymentMethodId\":\""
                        + TARJETA
                        + "\",\"cardNumber\":\"4242424242424242\"}")
                .with(user(cliente.toString()).authorities(() -> "movements:buy-points")))
        .andExpect(status().isBadRequest());
    assertThat(compras()).isZero();
    String contrato =
        java.nio.file.Files.readString(java.nio.file.Path.of("docs/api/openapi.json"));
    assertThat(contrato).doesNotContain("\"cardNumber\"").doesNotContain("\"cvc\"");
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-041` — notificaciones
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-438, CA-MV-440, CA-MV-451 y CA-MV-452 — el cobro que entró confirma la venta y su"
          + " pago una sola vez aunque llegue dos veces, sin sesión, y queda auditado")
  void cobroQueEntro() throws Exception {
    Cobro c = cobroAbierto("100.00");
    String evento = StripeEventsJson.cobrado(c.referencia(), c.pago(), 10000, "usd");

    mvc.perform(notificar(evento)).andExpect(status().isOk());
    mvc.perform(notificar(evento)).andExpect(status().isOk());

    assertThat(estado(c.venta())).isEqualTo("CONFIRMADA");
    assertThat(estadoDelPago(c.pago())).isEqualTo("CONFIRMADO");
    assertThat(eventos()).isEqualTo(1);
    assertThat(desenlace(c.pago())).isEqualTo("PROCESADO");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM movement_details WHERE movement_id = ?"
                    + " AND delivery_status = 'ENTREGADA'",
                Integer.class,
                c.venta()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_change_log WHERE entity = 'movements'"
                    + " AND entity_id = ? AND CAST(changes AS text) LIKE '%CONFIRMADA%'",
                Integer.class, c.venta()))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("CA-MV-439 — el cobro que entró en una compra de puntos la confirma y los abona")
  void cobroDePuntos() throws Exception {
    String cuerpo =
        mvc.perform(comprarPuntos(cliente, "10.00", TARJETA, "cp-puntos-0010"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID pago = UUID.fromString(JsonPath.read(cuerpo, "$.cardCharge.paymentId"));
    UUID compra = UUID.fromString(JsonPath.read(cuerpo, "$.id"));

    mvc.perform(notificar(StripeEventsJson.cobrado("pi_prueba_1", pago, 1000, "usd")))
        .andExpect(status().isOk());

    assertThat(estado(compra)).isEqualTo("CONFIRMADA");
    assertThat(saldo(jdbc, cliente, "PUNTOS")).isEqualByComparingTo("1000.00");
  }

  @Test
  @DisplayName(
      "CA-MV-441 y CA-MV-450 — una firma inválida o ausente es 400 y no guarda nada; sin pasarela"
          + " configurada, 503")
  void firmaYConfiguracion() throws Exception {
    String evento = StripeEventsJson.otro("customer.created");
    mvc.perform(
            post(WEBHOOK)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", "t=1,v1=00")
                .content(evento))
        .andExpect(status().isBadRequest());
    mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(evento))
        .andExpect(status().isBadRequest());
    assertThat(eventos()).isZero();

    pasarela.encender(false);
    mvc.perform(notificar(evento)).andExpect(status().isServiceUnavailable());
    assertThat(eventos()).isZero();
  }

  @Test
  @DisplayName(
      "CA-MV-442 — se responde en cuanto se guarda; si el proceso falla, queda pendiente y un"
          + " reintento la aplica")
  void reintento() throws Exception {
    Cobro c = cobroAbierto("100.00");
    // El pago todavía no está confirmado: el reembolso no se puede aplicar aún.
    mvc.perform(notificar(StripeEventsJson.reembolsado(c.referencia(), 5000)))
        .andExpect(status().isOk());
    UUID evento =
        jdbc.queryForObject(
            "SELECT id FROM gateway_events WHERE type = 'charge.refunded'", UUID.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT processed_at IS NULL AND attempts = 1 FROM gateway_events WHERE id = ?",
                Boolean.class,
                evento))
        .isTrue();

    mvc.perform(notificar(StripeEventsJson.cobrado(c.referencia(), c.pago(), 10000, "usd")))
        .andExpect(status().isOk());
    procesador.procesar(evento);

    assertThat(
            jdbc.queryForObject(
                "SELECT outcome FROM gateway_events WHERE id = ?", String.class, evento))
        .isEqualTo("PROCESADO");
    assertThat(incidencia(c.pago())).isEqualTo("REEMBOLSADO|50.00");
  }

  @Test
  @DisplayName(
      "CA-MV-443 — una tarjeta rechazada queda registrada y el pago sigue pendiente; después, el"
          + " cobro que entra sobre el mismo cobro lo confirma")
  void tarjetaRechazada() throws Exception {
    Cobro c = cobroAbierto("100.00");
    mvc.perform(
            notificar(StripeEventsJson.rechazado(c.referencia(), c.pago(), "Fondos insuficientes")))
        .andExpect(status().isOk());
    assertThat(estadoDelPago(c.pago())).isEqualTo("PENDIENTE");
    assertThat(
            jdbc.queryForObject(
                "SELECT error FROM gateway_events WHERE type = 'payment_intent.payment_failed'",
                String.class))
        .isEqualTo("Fondos insuficientes");

    mvc.perform(notificar(StripeEventsJson.cobrado(c.referencia(), c.pago(), 10000, "usd")))
        .andExpect(status().isOk());
    assertThat(estadoDelPago(c.pago())).isEqualTo("CONFIRMADO");
  }

  @Test
  @DisplayName(
      "CA-MV-444 — un cobro cancelado rechaza el pago pendiente, y la venta sigue pendiente")
  void cobroCancelado() throws Exception {
    Cobro c = cobroAbierto("100.00");
    mvc.perform(notificar(StripeEventsJson.cancelado(c.referencia(), c.pago())))
        .andExpect(status().isOk());
    assertThat(estadoDelPago(c.pago())).isEqualTo("RECHAZADO");
    assertThat(estado(c.venta())).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName(
      "CA-MV-445 y CA-MV-446 — reembolso parcial y acumulado, y disputa abierta y cerrada, se"
          + " marcan en el pago sin cambiar la venta")
  void reembolsoYDisputa() throws Exception {
    Cobro c = cobroAbierto("100.00");
    mvc.perform(notificar(StripeEventsJson.cobrado(c.referencia(), c.pago(), 10000, "usd")))
        .andExpect(status().isOk());

    mvc.perform(notificar(StripeEventsJson.reembolsado(c.referencia(), 3000)))
        .andExpect(status().isOk());
    assertThat(incidencia(c.pago())).isEqualTo("REEMBOLSADO|30.00");
    mvc.perform(notificar(StripeEventsJson.reembolsado(c.referencia(), 4500)))
        .andExpect(status().isOk());
    assertThat(incidencia(c.pago())).isEqualTo("REEMBOLSADO|45.00");
    assertThat(estado(c.venta())).isEqualTo("CONFIRMADA");
    assertThat(estadoDelPago(c.pago())).isEqualTo("CONFIRMADO");

    Cobro d = cobroAbierto("100.00");
    mvc.perform(notificar(StripeEventsJson.cobrado(d.referencia(), d.pago(), 10000, "usd")))
        .andExpect(status().isOk());
    mvc.perform(
            notificar(
                StripeEventsJson.disputa(
                    "charge.dispute.created", d.referencia(), "needs_response")))
        .andExpect(status().isOk());
    assertThat(incidencia(d.pago())).isEqualTo("EN_DISPUTA|");
    mvc.perform(
            notificar(StripeEventsJson.disputa("charge.dispute.closed", d.referencia(), "lost")))
        .andExpect(status().isOk());
    assertThat(incidencia(d.pago())).isEqualTo("DISPUTA_PERDIDA|");
    assertThat(estado(d.venta())).isEqualTo("CONFIRMADA");
  }

  @Test
  @DisplayName(
      "CA-MV-447 y CA-MV-448 — otro importe, otra moneda, un pago ya rechazado o ninguno: no se"
          + " confirma nada y la notificación queda con su error")
  void loQueNoCuadra() throws Exception {
    Cobro c = cobroAbierto("100.00");
    mvc.perform(notificar(StripeEventsJson.cobrado(c.referencia(), c.pago(), 9000, "usd")))
        .andExpect(status().isOk());
    mvc.perform(notificar(StripeEventsJson.cobrado(c.referencia(), c.pago(), 10000, "eur")))
        .andExpect(status().isOk());
    assertThat(estadoDelPago(c.pago())).isEqualTo("PENDIENTE");

    mvc.perform(notificar(StripeEventsJson.cobrado("pi_inexistente", null, 10000, "usd")))
        .andExpect(status().isOk());

    jdbc.update(
        "UPDATE payments SET status = 'RECHAZADO', rejected_at = now(),"
            + " rejection_reason = 'x' WHERE id = ?",
        c.pago());
    mvc.perform(notificar(StripeEventsJson.cobrado(c.referencia(), c.pago(), 10000, "usd")))
        .andExpect(status().isOk());

    assertThat(estado(c.venta())).isEqualTo("PENDIENTE");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM gateway_events WHERE outcome = 'ERROR'", Integer.class))
        .isEqualTo(4);
  }

  @Test
  @DisplayName("CA-MV-449 — una notificación de una clase que no se espera se guarda e ignora")
  void ignorada() throws Exception {
    mvc.perform(notificar(StripeEventsJson.otro("customer.created"))).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT outcome FROM gateway_events WHERE type = 'customer.created'", String.class))
        .isEqualTo("IGNORADO");
  }

  // ---------------------------------------------------------------------------
  // `RF-MV-042` — pagar con tarjeta un pendiente propio
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-453, CA-MV-454 y CA-MV-459 — sin cobro, lo abre; con cobro, devuelve el mismo; y no"
          + " confirma nada")
  void pagarUnPendiente() throws Exception {
    UUID venta = venta(cliente, "PENDIENTE", "100.00", TARJETA);

    mvc.perform(pagarConTarjeta(venta, cliente))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientSecret").value("pi_prueba_1_secret_prueba"));
    mvc.perform(pagarConTarjeta(venta, cliente))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientSecret").value("pi_prueba_1_secret_prueba"));

    assertThat(pasarela.abiertos()).hasSize(1);
    assertThat(estado(venta)).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName("CA-MV-455 — también sobre una compra de puntos pendiente con tarjeta")
  void pagarCompraDePuntos() throws Exception {
    pasarela.encender(false);
    String cuerpo =
        mvc.perform(comprarPuntos(cliente, "10.00", TARJETA, "cp-puntos-0020"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID compra = UUID.fromString(JsonPath.read(cuerpo, "$.id"));
    pasarela.encender(true);

    mvc.perform(pagarConTarjeta(compra, cliente))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gateway").value("STRIPE"));
  }

  @Test
  @DisplayName(
      "CA-MV-456 a CA-MV-458 — lo ajeno o inexistente es 404; sin pago pendiente con tarjeta,"
          + " 409; con la pasarela apagada o caída, 503")
  void pagarUnPendienteRechazado() throws Exception {
    UUID venta = venta(cliente, "PENDIENTE", "100.00", TARJETA);
    mvc.perform(pagarConTarjeta(venta, otro)).andExpect(status().isNotFound());
    mvc.perform(pagarConTarjeta(UUID.randomUUID(), cliente)).andExpect(status().isNotFound());

    UUID confirmada = venta(cliente, "CONFIRMADA", "100.00", TARJETA);
    mvc.perform(pagarConTarjeta(confirmada, cliente)).andExpect(status().isConflict());
    UUID conPse = venta(cliente, "PENDIENTE", "100.00", PSE);
    mvc.perform(pagarConTarjeta(conPse, cliente)).andExpect(status().isConflict());

    pasarela.caer(true);
    mvc.perform(pagarConTarjeta(venta, cliente)).andExpect(status().isServiceUnavailable());
    pasarela.caer(false);
    pasarela.encender(false);
    mvc.perform(pagarConTarjeta(venta, cliente)).andExpect(status().isServiceUnavailable());
    assertThat(pasarela.abiertos()).isEmpty();
  }

  @Test
  @DisplayName(
      "CA-MV-460 y CA-MV-461 — sin el permiso 403 y sin token 401; un CLIENTE lo porta por su"
          + " tipo de rol")
  void permisoDePagarConTarjeta() throws Exception {
    UUID venta = venta(cliente, "PENDIENTE", "100.00", TARJETA);
    mvc.perform(
            post("/api/v1/movements/mine/{id}/card-charge", venta)
                .with(user(cliente.toString()).authorities(() -> "movements:retry-payment")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/movements/mine/{id}/card-charge", venta))
        .andExpect(status().isUnauthorized());
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM role_permissions rp
                  JOIN roles r ON r.id = rp.role_id
                  JOIN permissions p ON p.id = rp.permission_id
                 WHERE r.code = 'CLIENTE' AND p.code = 'movements:pay-pending-by-card'
                """,
                Integer.class))
        .isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // Enmiendas
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "CA-MV-462 a CA-MV-465 — confirmar y rechazar a mano no alcanzan a un pago con cobro"
          + " abierto; sin cobro, sí")
  void manualesConCobroAbierto() throws Exception {
    Cobro c = cobroAbierto("100.00");
    mvc.perform(
            post(
                    "/api/v1/movements/payments/{id}/confirmation",
                    PaymentFixtures.pagoAConciliar(jdbc, c.venta()))
                .with(user(admin.toString()).authorities(() -> "movements:confirm-payment")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
    mvc.perform(rechazar(c.venta()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
    assertThat(estadoDelPago(c.pago())).isEqualTo("PENDIENTE");
    assertThat(estado(c.venta())).isEqualTo("PENDIENTE");

    UUID sinCobro = venta(cliente, "PENDIENTE", "100.00", TARJETA);
    mvc.perform(rechazar(sinCobro)).andExpect(status().isOk());
    UUID otraSinCobro = venta(cliente, "PENDIENTE", "100.00", TARJETA);
    mvc.perform(
            post(
                    "/api/v1/movements/payments/{id}/confirmation",
                    PaymentFixtures.pagoAConciliar(jdbc, otraSinCobro))
                .with(user(admin.toString()).authorities(() -> "movements:confirm-payment")))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "CA-MV-466 a CA-MV-468 — anular cancela el cobro y rechaza el pago; si ya se cobró, 409; si"
          + " la pasarela no responde, 503; y en esos dos casos nada cambia")
  void anularConCobro() throws Exception {
    Cobro c = cobroAbierto("100.00");
    mvc.perform(anular(c.venta())).andExpect(status().isOk());
    assertThat(pasarela.cancelados()).containsExactly(c.referencia());
    assertThat(estado(c.venta())).isEqualTo("ANULADA");
    assertThat(estadoDelPago(c.pago())).isEqualTo("RECHAZADO");

    Cobro d = cobroAbierto("100.00");
    pasarela.cobrar(d.referencia());
    mvc.perform(anular(d.venta()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-005"));
    assertThat(estado(d.venta())).isEqualTo("PENDIENTE");

    Cobro e = cobroAbierto("100.00");
    pasarela.caer(true);
    mvc.perform(anular(e.venta())).andExpect(status().isServiceUnavailable());
    assertThat(estado(e.venta())).isEqualTo("PENDIENTE");
    assertThat(estadoDelPago(e.pago())).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName(
      "CA-MV-469 y CA-MV-470 — el libro filtra por incidencia —una o cualquiera— y la publica en"
          + " cada fila; una desconocida es 400")
  void libroPorIncidencia() throws Exception {
    Cobro c = cobroAbierto("100.00");
    mvc.perform(notificar(StripeEventsJson.cobrado(c.referencia(), c.pago(), 10000, "usd")))
        .andExpect(status().isOk());
    mvc.perform(notificar(StripeEventsJson.reembolsado(c.referencia(), 10000)))
        .andExpect(status().isOk());
    UUID limpia = venta(cliente, "PENDIENTE", "100.00", TARJETA);

    mvc.perform(libro("REEMBOLSADO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].id").value(c.venta().toString()))
        .andExpect(jsonPath("$.content[0].paymentIncident").value("REEMBOLSADO"));
    mvc.perform(libro("CUALQUIERA")).andExpect(jsonPath("$.content.length()").value(1));
    mvc.perform(libro("EN_DISPUTA")).andExpect(jsonPath("$.content.length()").value(0));
    mvc.perform(libro(null))
        .andExpect(
            jsonPath("$.content[?(@.id == '%s')].paymentIncident".formatted(limpia))
                .value(org.hamcrest.Matchers.contains((Object) null)));
    mvc.perform(libro("PERDIDO")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "CA-MV-474 a CA-MV-476 — volver a pagar con otro método cancela el cobro abierto; si ya"
          + " cobró, nada cambia; con tarjeta otra vez, 409")
  void volverAPagarConCobroAbierto() throws Exception {
    Cobro c = cobroAbierto("100.00");
    mvc.perform(volverAPagar(c.venta(), TARJETA, "cp-clave-0101").with(comprador(cliente)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-010"));

    mvc.perform(volverAPagar(c.venta(), PSE, "cp-clave-0102").with(comprador(cliente)))
        .andExpect(status().isCreated());
    assertThat(pasarela.cancelados()).containsExactly(c.referencia());
    assertThat(estadoDelPago(c.pago())).isEqualTo("RECHAZADO");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE movement_id = ? AND status = 'PENDIENTE'",
                Integer.class,
                c.venta()))
        .isEqualTo(1);

    Cobro d = cobroAbierto("100.00");
    pasarela.cobrar(d.referencia());
    mvc.perform(volverAPagar(d.venta(), PSE, "cp-clave-0103").with(comprador(cliente)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-009"));
    assertThat(estadoDelPago(d.pago())).isEqualTo("PENDIENTE");
  }

  @Test
  @DisplayName(
      "CA-MV-478 y CA-MV-479 — confirmar y rechazar a mano una compra de puntos con cobro abierto"
          + " es 409 y no abona")
  void puntosManualesConCobro() throws Exception {
    String cuerpo =
        mvc.perform(comprarPuntos(cliente, "10.00", TARJETA, "cp-puntos-0030"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID compra = UUID.fromString(JsonPath.read(cuerpo, "$.id"));

    mvc.perform(confirmarPuntos(compra))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
    mvc.perform(
            post(
                    "/api/v1/movements/payments/{id}/rejection",
                    PaymentFixtures.pagoAConciliar(jdbc, compra))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"No\"}")
                .with(user(admin.toString()).authorities(() -> "movements:reject-payment")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("EX-004"));
    assertThat(estado(compra)).isEqualTo("PENDIENTE");
    assertThat(saldo(jdbc, cliente, "PUNTOS")).isEqualByComparingTo("0");
  }

  // ---------------------------------------------------------------------------

  private record Cobro(UUID venta, UUID pago, String referencia) {}

  /** Una venta pendiente con su pago con tarjeta y el cobro abierto por `RF-MV-042`. */
  private Cobro cobroAbierto(String importe) throws Exception {
    UUID venta = venta(cliente, "PENDIENTE", importe, TARJETA);
    String cuerpo =
        mvc.perform(pagarConTarjeta(venta, cliente))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID pago = UUID.fromString(JsonPath.read(cuerpo, "$.paymentId"));
    return new Cobro(venta, pago, referenciaDe(pago));
  }

  private MockHttpServletRequestBuilder notificar(String evento) {
    return post(WEBHOOK)
        .contentType(MediaType.APPLICATION_JSON)
        .header("Stripe-Signature", FakeCardGateway.FIRMA)
        .content(evento);
  }

  private MockHttpServletRequestBuilder pagarConTarjeta(UUID movimiento, UUID quien) {
    return post("/api/v1/movements/mine/{id}/card-charge", movimiento)
        .with(user(quien.toString()).authorities(() -> "movements:pay-pending-by-card"));
  }

  private static MockHttpServletRequestBuilder volverAPagar(
      UUID venta, String metodo, String clave) {
    return post("/api/v1/movements/mine/{id}/payments", venta)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"paymentMethodId\":\"" + metodo + "\"}")
        .header("Idempotency-Key", clave);
  }

  private static RequestPostProcessor comprador(UUID persona) {
    return user(persona.toString()).authorities(() -> "movements:retry-payment");
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

  private MockHttpServletRequestBuilder confirmarPuntos(UUID compra) {
    return post(
            "/api/v1/movements/payments/{id}/confirmation",
            PaymentFixtures.pagoAConciliar(jdbc, compra))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}")
        .with(user(admin.toString()).authorities(() -> "movements:confirm-payment"));
  }

  private MockHttpServletRequestBuilder rechazar(UUID venta) {
    return post(
            "/api/v1/movements/payments/{id}/rejection",
            PaymentFixtures.pagoAConciliar(jdbc, venta))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"Fondos insuficientes\"}")
        .with(user(admin.toString()).authorities(() -> "movements:reject-payment"));
  }

  private MockHttpServletRequestBuilder anular(UUID venta) {
    return post("/api/v1/movements/{id}/voiding", venta)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"reason\":\"Cliente equivocado\"}")
        .with(user(admin.toString()).authorities(() -> "movements:void"));
  }

  private MockHttpServletRequestBuilder libro(String incidencia) {
    MockHttpServletRequestBuilder peticion =
        get("/api/v1/movements")
            .param("userId", cliente.toString())
            .with(user(admin.toString()).authorities(() -> "movements:read"));
    return incidencia == null ? peticion : peticion.param("paymentIncident", incidencia);
  }

  private String referenciaDe(UUID pago) {
    return jdbc.queryForObject(
        "SELECT provider_reference FROM payments WHERE id = ?", String.class, pago);
  }

  private String estado(UUID movimiento) {
    return jdbc.queryForObject(
        "SELECT status FROM movements WHERE id = ?", String.class, movimiento);
  }

  private String estadoDelPago(UUID pago) {
    return jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, pago);
  }

  private String incidencia(UUID pago) {
    return jdbc.queryForObject(
        "SELECT incident || '|' || coalesce(refunded_amount::text, '') FROM payments WHERE id = ?",
        String.class,
        pago);
  }

  private String desenlace(UUID pago) {
    return jdbc.queryForObject(
        "SELECT outcome FROM gateway_events WHERE payment_id = ? LIMIT 1", String.class, pago);
  }

  private int eventos() {
    return jdbc.queryForObject("SELECT count(*) FROM gateway_events", Integer.class);
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
                ?, CAST(? AS uuid), ?, ?, CAST(? AS numeric), 0, CAST(? AS numeric),
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
        SELECT ?, ?, p.id, ?, p.name, p.description, 1, CAST(? AS numeric), CAST(? AS numeric),
               p.validity_days, p.implementation FROM products p WHERE p.id = ?
        """,
        UUID.randomUUID(),
        id,
        sujeto,
        importe,
        importe,
        bot);
    return id;
  }

  private void limpiar() {
    CommissionCleanup.limpiar(jdbc);
    PointsFixtures.limpiar(jdbc);
    jdbc.update("DELETE FROM gateway_events");
    jdbc.update("DELETE FROM audit_change_log WHERE module = 'MV'");
    jdbc.update("DELETE FROM products WHERE code LIKE 'CP\\_%'");
    jdbc.update(
        "DELETE FROM user_products WHERE user_id IN"
            + " (SELECT id FROM users WHERE username LIKE 'cp-%')");
    jdbc.update("DELETE FROM users WHERE username LIKE 'cp-%'");
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
            + " VALUES ('TIENDA', 'AUTOMATICA', ?, ?, 'BOT', ?, 'x', 100.00, CAST(? AS uuid), 30,"
            + " 'ACTIVO')",
        id,
        codigo,
        "Producto " + codigo,
        USD);
    return id;
  }
}
