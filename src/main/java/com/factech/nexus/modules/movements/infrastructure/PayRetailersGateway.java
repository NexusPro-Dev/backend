package com.factech.nexus.modules.movements.infrastructure;

import com.factech.nexus.modules.movements.domain.service.LocalPaymentGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * <b>El único sitio que conoce la API de PayRetailers</b> (`architecture.md` §15.5). Por HTTP con
 * {@link RestClient}, sin biblioteca, como Stripe: dos llamadas JSON.
 *
 * <ul>
 *   <li><b>Abrir</b>: {@code POST /paywalls} —su página de pago—, con {@code trackingId} = el
 *       identificador del pago. El {@code uid} que devuelve es el del paywall, no el de la
 *       transacción, que nace cuando el cliente elige método.
 *   <li><b>Consultar</b>: {@code GET /transactions/byTracking/{trackingId}}. {@code 404} es «el
 *       cliente todavía no eligió método»: pendiente.
 * </ul>
 *
 * <p><b>Autenticación</b>: HTTP Basic con {@code shopId} y la clave secreta, y la cabecera {@code
 * Ocp-Apim-Subscription-Key}. <b>Los importes viajan en la unidad mínima</b> de la moneda, como
 * texto en la petición y como número en la respuesta; se confirma con el sandbox.
 */
@Component
public class PayRetailersGateway implements LocalPaymentGateway {

  private static final Logger LOG = LoggerFactory.getLogger(PayRetailersGateway.class);

  /** ISO 3166-1 alfa-3 → alfa-2: el sistema guarda el país en alfa-3 y la pasarela pide alfa-2. */
  private static final Map<String, String> ALFA2 =
      Stream.of(Locale.getISOCountries())
          .collect(
              Collectors.toMap(
                  c -> Locale.of("", c).getISO3Country().toUpperCase(Locale.ROOT),
                  c -> c,
                  (a, b) -> a));

  private final PayRetailersSettings ajustes;
  private final RestClient http;
  private final ObjectMapper json;

  @Autowired
  public PayRetailersGateway(
      PayRetailersSettings ajustes, RestClient.Builder constructor, ObjectMapper json) {
    this(
        ajustes,
        constructor.requestFactory(fabrica(ajustes.timeout())).baseUrl(ajustes.baseUrl()).build(),
        json);
    if (!ajustes.encendida()) {
      LOG.warn(
          "Pasarela local APAGADA: faltan PAYRETAILERS_SHOP_ID, PAYRETAILERS_SECRET_KEY o"
              + " PAYRETAILERS_SUBSCRIPTION_KEY. El pago con PSE nace pendiente sin cobro y lo"
              + " confirma una persona.");
    }
  }

  PayRetailersGateway(PayRetailersSettings ajustes, RestClient http, ObjectMapper json) {
    this.ajustes = ajustes;
    this.http = http;
    this.json = json;
  }

  @Override
  public boolean enabled() {
    return ajustes.encendida();
  }

  @Override
  public LocalCharge open(LocalChargeOrder orden) {
    ObjectNode cuerpo = json.createObjectNode();
    cuerpo.put("amount", Long.toString(orden.amountMinor()));
    cuerpo.put("currency", orden.currency());
    cuerpo.put("description", orden.movementCode());
    cuerpo.put("trackingId", orden.paymentId().toString());
    cuerpo.put("language", "ES");
    cuerpo.put("testMode", ajustes.testMode());
    ponerSiHay(cuerpo, "notificationUrl", ajustes.notificationUrl());
    ponerSiHay(cuerpo, "returnUrl", ajustes.returnUrl());
    ObjectNode cliente = cuerpo.putObject("customer");
    Payer quien = orden.payer();
    cliente.put("firstName", quien.firstName());
    cliente.put("lastName", quien.lastName());
    cliente.put("email", quien.email());
    cliente.put("country", alfa2(quien.countryAlpha3()));
    ponerSiHay(cliente, "personalId", quien.personalId());
    ponerSiHay(cliente, "phone", quien.phone());
    try {
      JsonNode respuesta =
          http.post()
              .uri("/paywalls")
              .headers(this::cabeceras)
              .contentType(MediaType.APPLICATION_JSON)
              .body(cuerpo)
              .retrieve()
              .body(JsonNode.class);
      String uid = texto(respuesta, "uid");
      String pagina = respuesta == null ? null : texto(respuesta.path("form"), "action");
      if (uid == null || pagina == null) {
        throw new Unavailable("La pasarela no devolvió el cobro ni su página.", null);
      }
      return new LocalCharge(uid, pagina);
    } catch (RestClientResponseException fallo) {
      if (fallo.getStatusCode().is4xxClientError()) {
        throw new Rejected(
            "La pasarela rechazó el cobro (HTTP "
                + fallo.getStatusCode().value()
                + "): "
                + recortar(fallo.getResponseBodyAsString()));
      }
      throw new Unavailable("La pasarela no abrió el cobro: HTTP " + fallo.getStatusCode(), fallo);
    } catch (RestClientException fallo) {
      throw new Unavailable(
          "La pasarela no abrió el cobro: " + fallo.getClass().getSimpleName(), fallo);
    }
  }

  @Override
  public Optional<LocalTransaction> findByTracking(UUID paymentId) {
    try {
      JsonNode t =
          http.get()
              .uri("/transactions/byTracking/{tracking}", paymentId.toString())
              .headers(this::cabeceras)
              .retrieve()
              .body(JsonNode.class);
      if (t == null || t.isNull()) {
        return Optional.empty();
      }
      String estado = texto(t, "status");
      return Optional.of(
          new LocalTransaction(
              texto(t, "uid"),
              desenlace(estado),
              estado,
              t.hasNonNull("amount") ? t.get("amount").asLong() : null,
              texto(t, "currency")));
    } catch (RestClientResponseException fallo) {
      if (fallo.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
        return Optional.empty();
      }
      throw new Unavailable(
          "La pasarela no respondió la consulta: HTTP " + fallo.getStatusCode(), fallo);
    } catch (RestClientException fallo) {
      throw new Unavailable(
          "La pasarela no respondió la consulta: " + fallo.getClass().getSimpleName(), fallo);
    }
  }

  @Override
  public Optional<Notice> parse(String payload) {
    try {
      JsonNode aviso = json.readTree(payload);
      String tracking = texto(aviso, "trackingId");
      String uid = texto(aviso, "uid");
      if (tracking == null || uid == null) {
        return Optional.empty();
      }
      UUID pago;
      try {
        pago = UUID.fromString(tracking);
      } catch (IllegalArgumentException noEsNuestro) {
        pago = null;
      }
      String estado = texto(aviso, "status");
      return Optional.of(new Notice(pago, uid + ":" + (estado == null ? "?" : estado), estado));
    } catch (Exception ilegible) {
      return Optional.empty();
    }
  }

  // ---------------------------------------------------------------------------

  static Outcome desenlace(String estado) {
    if (estado == null) {
      return Outcome.PENDIENTE;
    }
    return switch (estado.toUpperCase(Locale.ROOT)) {
      case "APPROVED" -> Outcome.APROBADO;
      case "FAILED", "REJECTED", "CANCELLED", "CANCELED", "EXPIRED" -> Outcome.FINAL_NO_APROBADO;
      default -> Outcome.PENDIENTE;
    };
  }

  static String alfa2(String alfa3) {
    if (alfa3 == null) {
      return null;
    }
    String codigo = alfa3.trim().toUpperCase(Locale.ROOT);
    return ALFA2.getOrDefault(codigo, codigo);
  }

  private void cabeceras(HttpHeaders h) {
    String basico = ajustes.shopId() + ":" + ajustes.secretKey();
    h.set(
        HttpHeaders.AUTHORIZATION,
        "Basic " + Base64.getEncoder().encodeToString(basico.getBytes(StandardCharsets.UTF_8)));
    h.set("Ocp-Apim-Subscription-Key", ajustes.subscriptionKey());
    h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
  }

  private static void ponerSiHay(ObjectNode nodo, String campo, String valor) {
    if (valor != null && !valor.isBlank()) {
      nodo.put(campo, valor);
    }
  }

  private static String texto(JsonNode nodo, String campo) {
    return nodo != null && nodo.hasNonNull(campo) ? nodo.get(campo).asText() : null;
  }

  private static String recortar(String texto) {
    if (texto == null) {
      return "";
    }
    return texto.length() > 300 ? texto.substring(0, 300) : texto;
  }

  private static SimpleClientHttpRequestFactory fabrica(Duration plazo) {
    SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
    fabrica.setConnectTimeout(plazo);
    fabrica.setReadTimeout(plazo);
    return fabrica;
  }
}
