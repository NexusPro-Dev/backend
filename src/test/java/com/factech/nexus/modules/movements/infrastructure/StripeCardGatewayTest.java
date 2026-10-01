package com.factech.nexus.modules.movements.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.factech.nexus.modules.movements.domain.service.CardGateway;
import com.factech.nexus.modules.movements.domain.service.CardGateway.CancelResult;
import com.factech.nexus.modules.movements.domain.service.CardGateway.ChargeOrder;
import com.factech.nexus.modules.movements.domain.service.CardGateway.GatewayEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * El adaptador de Stripe (`RF-MV-040` y `RF-MV-041`, `architecture.md` §15.4), contra un servidor
 * simulado: la forma de las llamadas y la verificación de la firma, con un HMAC calculado aquí
 * igual que lo calcula Stripe.
 */
class StripeCardGatewayTest {

  private static final String SECRETO = "whsec_prueba";
  private static final Instant AHORA = Instant.parse("2026-10-01T12:00:00Z");

  private MockRestServiceServer servidor;
  private StripeCardGateway pasarela;

  @BeforeEach
  void preparar() {
    RestClient.Builder constructor = RestClient.builder().baseUrl("https://api.stripe.test");
    servidor = MockRestServiceServer.bindTo(constructor).build();
    StripeSettings ajustes =
        new StripeSettings(
            "rk_test_prueba",
            SECRETO,
            "https://api.stripe.test",
            "2024-06-20",
            Duration.ofSeconds(5),
            Duration.ofMinutes(5),
            false);
    pasarela =
        new StripeCardGateway(
            ajustes, constructor.build(), new ObjectMapper(), Clock.fixed(AHORA, ZoneOffset.UTC));
  }

  @Test
  @DisplayName(
      "abrir: POST con la clave en Idempotency-Key, la versión fijada, solo tarjeta y la"
          + " metadata del pago")
  void abre() {
    UUID pago = UUID.randomUUID();
    servidor
        .expect(requestTo("https://api.stripe.test/v1/payment_intents"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Idempotency-Key", "clave-1"))
        .andExpect(header("Stripe-Version", "2024-06-20"))
        .andExpect(header("Authorization", "Bearer rk_test_prueba"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("amount=4900"),
                        org.hamcrest.Matchers.containsString("currency=usd"),
                        org.hamcrest.Matchers.containsString("payment_method_types%5B%5D=card"),
                        org.hamcrest.Matchers.containsString("metadata%5Bpayment_id%5D=" + pago))))
        .andRespond(
            withSuccess(
                "{\"id\":\"pi_1\",\"client_secret\":\"pi_1_secret_x\",\"status\":"
                    + "\"requires_payment_method\"}",
                MediaType.APPLICATION_JSON));

    CardGateway.Charge cobro =
        pasarela.open(new ChargeOrder(4900, "USD", "clave-1", pago, UUID.randomUUID(), "VTA-1"));

    assertThat(cobro.reference()).isEqualTo("pi_1");
    assertThat(cobro.clientSecret()).isEqualTo("pi_1_secret_x");
    servidor.verify();
  }

  @Test
  @DisplayName("un error de Stripe al abrir es Unavailable, sin el cuerpo de la respuesta")
  void falla() {
    servidor
        .expect(requestTo("https://api.stripe.test/v1/payment_intents"))
        .andRespond(withServerError().body("{\"error\":{\"message\":\"secreto\"}}"));
    assertThatThrownBy(
            () ->
                pasarela.open(
                    new ChargeOrder(
                        100, "USD", "clave-2", UUID.randomUUID(), UUID.randomUUID(), "VTA-2")))
        .isInstanceOf(CardGateway.Unavailable.class)
        .hasMessageNotContaining("secreto");
  }

  @Test
  @DisplayName("cancelar un cobro ya cobrado es YA_COBRADO: Stripe rechaza y la consulta lo dice")
  void cancelaYaCobrado() {
    servidor
        .expect(requestTo("https://api.stripe.test/v1/payment_intents/pi_9/cancel"))
        .andRespond(
            withBadRequest().body("{\"error\":{\"code\":\"payment_intent_unexpected_state\"}}"));
    servidor
        .expect(requestTo("https://api.stripe.test/v1/payment_intents/pi_9"))
        .andRespond(
            withSuccess("{\"id\":\"pi_9\",\"status\":\"succeeded\"}", MediaType.APPLICATION_JSON));
    assertThat(pasarela.cancel("pi_9")).isEqualTo(CancelResult.YA_COBRADO);
  }

  @Test
  @DisplayName("la firma correcta verifica y el evento se interpreta")
  void firmaCorrecta() {
    String cuerpo =
        "{\"id\":\"evt_1\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":"
            + "{\"id\":\"pi_1\",\"amount_received\":4900,\"currency\":\"usd\","
            + "\"metadata\":{\"payment_id\":\"p1\"}}}}";
    long marca = AHORA.getEpochSecond();

    GatewayEvent evento =
        pasarela.verify(cuerpo.getBytes(StandardCharsets.UTF_8), firma(marca, cuerpo));

    assertThat(evento.externalId()).isEqualTo("evt_1");
    assertThat(evento.chargeReference()).isEqualTo("pi_1");
    assertThat(evento.amountMinor()).isEqualTo(4900L);
    assertThat(evento.paymentIdHint()).isEqualTo("p1");
  }

  @Test
  @DisplayName(
      "un cuerpo alterado, una firma ausente o una marca de más de cinco minutos no verifican")
  void firmasQueNoVerifican() {
    String cuerpo = "{\"id\":\"evt_2\",\"type\":\"x\",\"data\":{\"object\":{}}}";
    long marca = AHORA.getEpochSecond();
    String buena = firma(marca, cuerpo);

    assertThatThrownBy(
            () -> pasarela.verify((cuerpo + " ").getBytes(StandardCharsets.UTF_8), buena))
        .isInstanceOf(CardGateway.InvalidSignature.class);
    assertThatThrownBy(() -> pasarela.verify(cuerpo.getBytes(StandardCharsets.UTF_8), null))
        .isInstanceOf(CardGateway.InvalidSignature.class);
    long vieja = marca - 301;
    assertThatThrownBy(
            () -> pasarela.verify(cuerpo.getBytes(StandardCharsets.UTF_8), firma(vieja, cuerpo)))
        .isInstanceOf(CardGateway.InvalidSignature.class)
        .hasMessageContaining("vieja");
  }

  private static String firma(long marca, String cuerpo) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      String v1 =
          HexFormat.of()
              .formatHex(mac.doFinal((marca + "." + cuerpo).getBytes(StandardCharsets.UTF_8)));
      return "t=" + marca + ",v1=" + v1;
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
