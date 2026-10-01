package com.factech.nexus.modules.movements.infrastructure;

import com.factech.nexus.modules.movements.domain.service.CardGateway.GatewayEvent;
import com.factech.nexus.modules.movements.domain.service.CardGateway.InvalidSignature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * De una notificación de Stripe a {@link GatewayEvent}: lo único que el sistema lee de ella, y el
 * cuerpo entero para guardarlo (`RN-MV-059`). Aparte del adaptador para que la use también el doble
 * de las pruebas: la forma de la notificación se interpreta en un solo sitio.
 */
public final class StripeEvents {

  private StripeEvents() {}

  public static GatewayEvent interpretar(ObjectMapper json, String texto) {
    JsonNode evento;
    try {
      evento = json.readTree(texto);
    } catch (java.io.IOException e) {
      throw new InvalidSignature("El cuerpo no es JSON.");
    }
    String tipo = texto(evento, "type");
    JsonNode objeto = evento.path("data").path("object");
    String referencia = null;
    String pago = null;
    Long importe = null;
    Long devuelto = null;
    String disputa = null;
    String fallo = null;
    if (tipo != null && tipo.startsWith("payment_intent.")) {
      referencia = texto(objeto, "id");
      pago = texto(objeto.path("metadata"), "payment_id");
      importe =
          objeto.hasNonNull("amount_received") ? objeto.get("amount_received").asLong() : null;
      fallo = texto(objeto.path("last_payment_error"), "message");
    } else if ("charge.refunded".equals(tipo)) {
      referencia = texto(objeto, "payment_intent");
      devuelto =
          objeto.hasNonNull("amount_refunded") ? objeto.get("amount_refunded").asLong() : null;
    } else if (tipo != null && tipo.startsWith("charge.dispute.")) {
      referencia = texto(objeto, "payment_intent");
      disputa = texto(objeto, "status");
    }
    return new GatewayEvent(
        texto(evento, "id"),
        tipo,
        texto,
        referencia,
        pago,
        importe,
        texto(objeto, "currency"),
        devuelto,
        disputa,
        fallo);
  }

  private static String texto(JsonNode nodo, String campo) {
    JsonNode valor = nodo.path(campo);
    return valor.isMissingNode() || valor.isNull() ? null : valor.asText();
  }
}
