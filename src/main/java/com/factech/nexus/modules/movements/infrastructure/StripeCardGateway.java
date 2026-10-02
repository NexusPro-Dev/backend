package com.factech.nexus.modules.movements.infrastructure;

import com.factech.nexus.modules.movements.domain.service.CardGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link CardGateway} contra Stripe (`architecture.md` §15.4), <b>por su API HTTP y sin su
 * biblioteca</b>: tres llamadas sobre {@code PaymentIntents} y la verificación de la firma de sus
 * notificaciones, igual que Resend, YouTube y Vimeo se llaman con {@link RestClient}. Es <b>el
 * único sitio</b> que conoce la forma de la API de Stripe.
 *
 * <ul>
 *   <li><b>Abrir</b>: {@code POST /v1/payment_intents}, formulario, con la clave del pago en la
 *       cabecera {@code Idempotency-Key} —la misma petición devuelve el mismo cobro— y {@code
 *       payment_method_types[]=card}: solo la tarjeta.
 *   <li><b>Consultar</b>: {@code GET /v1/payment_intents/{id}}.
 *   <li><b>Cancelar</b>: {@code POST /v1/payment_intents/{id}/cancel}. Si Stripe responde que el
 *       estado no lo admite, se consulta: un cobro {@code succeeded} es {@code YA_COBRADO}.
 *   <li><b>Verificar</b>: la cabecera {@code Stripe-Signature} —{@code t=…,v1=…}— es un HMAC-SHA256
 *       de {@code t + "." + cuerpo} con el secreto del endpoint; se compara en tiempo constante y
 *       se rechaza la marca más vieja que la tolerancia.
 * </ul>
 *
 * <p><b>Nada de lo que viaja va al log</b>: ni el secreto de cliente, ni el cuerpo de una
 * notificación, que lleva datos de quien pagó.
 */
@Component
public class StripeCardGateway implements CardGateway {

  private static final Logger LOG = LoggerFactory.getLogger(StripeCardGateway.class);
  private static final String NOMBRE = "STRIPE";

  private final StripeSettings ajustes;
  private final RestClient http;
  private final ObjectMapper json;
  private final Clock reloj;

  @Autowired
  public StripeCardGateway(
      StripeSettings ajustes, RestClient.Builder constructor, ObjectMapper json) {
    this(
        ajustes,
        constructor
            .requestFactory(fabrica(ajustes.timeout()))
            .baseUrl(ajustes.apiBaseUrl())
            .build(),
        json,
        Clock.systemUTC());
    if (!ajustes.encendida()) {
      LOG.warn(
          "Pasarela de la tarjeta APAGADA: faltan STRIPE_SECRET_KEY o STRIPE_WEBHOOK_SECRET. El"
              + " pago con CREDIT_CARD nace pendiente sin cobro y lo confirma una persona.");
    }
  }

  /** Para las pruebas: el cliente ya construido, con el servidor simulado detrás, y su reloj. */
  StripeCardGateway(StripeSettings ajustes, RestClient http, ObjectMapper json, Clock reloj) {
    this.ajustes = ajustes;
    this.http = http;
    this.json = json;
    this.reloj = reloj;
  }

  @Override
  public boolean enabled() {
    return ajustes.encendida();
  }

  @Override
  public String name() {
    return NOMBRE;
  }

  @Override
  public Charge open(ChargeOrder orden) {
    MultiValueMap<String, String> forma = new LinkedMultiValueMap<>();
    forma.add("amount", Long.toString(orden.amountMinor()));
    forma.add("currency", orden.currency().toLowerCase(java.util.Locale.ROOT));
    forma.add("payment_method_types[]", "card");
    forma.add("metadata[payment_id]", orden.paymentId().toString());
    forma.add("metadata[movement_id]", orden.movementId().toString());
    forma.add("metadata[movement_code]", orden.movementCode());
    try {
      JsonNode cuerpo =
          http.post()
              .uri("/v1/payment_intents")
              .headers(h -> cabeceras(h, orden.idempotencyKey()))
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(forma)
              .retrieve()
              .body(JsonNode.class);
      return cobro(cuerpo);
    } catch (RestClientException fallo) {
      throw new Unavailable("La pasarela no abrió el cobro: " + motivo(fallo), fallo);
    }
  }

  @Override
  public Charge retrieve(String reference) {
    try {
      return cobro(
          http.get()
              .uri("/v1/payment_intents/{id}", reference)
              .headers(h -> cabeceras(h, null))
              .retrieve()
              .body(JsonNode.class));
    } catch (RestClientException fallo) {
      throw new Unavailable("La pasarela no respondió: " + motivo(fallo), fallo);
    }
  }

  @Override
  public CancelResult cancel(String reference) {
    try {
      http.post()
          .uri("/v1/payment_intents/{id}/cancel", reference)
          .headers(h -> cabeceras(h, null))
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(new LinkedMultiValueMap<String, String>())
          .retrieve()
          .toBodilessEntity();
      return CancelResult.CANCELADO;
    } catch (RestClientResponseException fallo) {
      if (fallo.getStatusCode().is4xxClientError()) {
        // El estado no admite cancelar: o ya se cobró, o ya estaba cancelado.
        Charge actual = retrieve(reference);
        if ("succeeded".equals(actual.status())) {
          return CancelResult.YA_COBRADO;
        }
        if (actual.canceled()) {
          return CancelResult.CANCELADO;
        }
      }
      throw new Unavailable("La pasarela no canceló el cobro: " + motivo(fallo), fallo);
    } catch (RestClientException fallo) {
      throw new Unavailable("La pasarela no respondió al cancelar: " + motivo(fallo), fallo);
    }
  }

  @Override
  public GatewayEvent verify(byte[] body, String signature) {
    if (!ajustes.encendida()) {
      throw new InvalidSignature("La pasarela no está configurada.");
    }
    if (body == null || signature == null || signature.isBlank()) {
      throw new InvalidSignature("Falta la firma.");
    }
    Long marca = null;
    List<String> firmas = new ArrayList<>();
    for (String parte : signature.split(",")) {
      String[] kv = parte.trim().split("=", 2);
      if (kv.length != 2) {
        continue;
      }
      if ("t".equals(kv[0])) {
        try {
          marca = Long.parseLong(kv[1]);
        } catch (NumberFormatException e) {
          throw new InvalidSignature("La marca de tiempo no es un número.");
        }
      } else if ("v1".equals(kv[0])) {
        firmas.add(kv[1]);
      }
    }
    if (marca == null || firmas.isEmpty()) {
      throw new InvalidSignature("La firma no tiene marca de tiempo o no tiene v1.");
    }
    long ahora = reloj.instant().getEpochSecond();
    if (Math.abs(ahora - marca) > ajustes.signatureTolerance().toSeconds()) {
      throw new InvalidSignature("La firma es demasiado vieja.");
    }
    byte[] esperada = hmac(marca + "." + new String(body, StandardCharsets.UTF_8));
    boolean vale = false;
    for (String firma : firmas) {
      try {
        vale |= MessageDigest.isEqual(esperada, HexFormat.of().parseHex(firma));
      } catch (IllegalArgumentException e) {
        // Una firma que no es hexadecimal no verifica; se mira la siguiente.
      }
    }
    if (!vale) {
      throw new InvalidSignature("La firma no verifica.");
    }
    return interpretar(body);
  }

  @Override
  public GatewayEvent parse(String payload) {
    return interpretar(payload.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public boolean processInline() {
    return ajustes.processInline();
  }

  // ---------------------------------------------------------------------------

  private GatewayEvent interpretar(byte[] body) {
    return StripeEvents.interpretar(json, new String(body, StandardCharsets.UTF_8));
  }

  private static String texto(JsonNode nodo, String campo) {
    JsonNode valor = nodo.path(campo);
    return valor.isMissingNode() || valor.isNull() ? null : valor.asText();
  }

  private Charge cobro(JsonNode cuerpo) {
    if (cuerpo == null || !cuerpo.hasNonNull("id")) {
      throw new Unavailable("La pasarela respondió sin cobro.", null);
    }
    return new Charge(
        cuerpo.get("id").asText(), texto(cuerpo, "client_secret"), texto(cuerpo, "status"));
  }

  private void cabeceras(HttpHeaders h, String clave) {
    h.setBearerAuth(ajustes.secretKey() == null ? "" : ajustes.secretKey());
    h.set("Stripe-Version", ajustes.apiVersion());
    if (clave != null) {
      h.set("Idempotency-Key", clave);
    }
  }

  private byte[] hmac(String mensaje) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              ajustes.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8));
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 no está disponible.", e);
    }
  }

  /** El motivo, sin cuerpo de respuesta: lo que Stripe contesta puede traer datos del cobro. */
  private static String motivo(RestClientException fallo) {
    return fallo instanceof RestClientResponseException r
        ? "HTTP " + r.getStatusCode().value()
        : fallo.getClass().getSimpleName();
  }

  private static SimpleClientHttpRequestFactory fabrica(Duration plazo) {
    SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
    fabrica.setConnectTimeout(plazo);
    fabrica.setReadTimeout(plazo);
    return fabrica;
  }
}
