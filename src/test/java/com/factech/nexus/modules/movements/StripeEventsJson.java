package com.factech.nexus.modules.movements;

import java.util.UUID;

/** Cuerpos de notificación con la forma de Stripe, para las pruebas de `RF-MV-041`. */
public final class StripeEventsJson {

  private StripeEventsJson() {}

  public static String cobrado(String referencia, UUID pago, long importe, String moneda) {
    return intencion("payment_intent.succeeded", referencia, pago, importe, moneda, null);
  }

  public static String rechazado(String referencia, UUID pago, String motivo) {
    return intencion("payment_intent.payment_failed", referencia, pago, 0, "usd", motivo);
  }

  public static String cancelado(String referencia, UUID pago) {
    return intencion("payment_intent.canceled", referencia, pago, 0, "usd", null);
  }

  public static String reembolsado(String referencia, long devuelto) {
    return evento(
        "charge.refunded",
        "{\"id\":\"ch_"
            + UUID.randomUUID()
            + "\",\"payment_intent\":\""
            + referencia
            + "\",\"amount_refunded\":"
            + devuelto
            + ",\"currency\":\"usd\"}");
  }

  public static String disputa(String tipo, String referencia, String estado) {
    return evento(
        tipo,
        "{\"id\":\"dp_"
            + UUID.randomUUID()
            + "\",\"payment_intent\":\""
            + referencia
            + "\",\"status\":\""
            + estado
            + "\"}");
  }

  public static String otro(String tipo) {
    return evento(tipo, "{\"id\":\"x_" + UUID.randomUUID() + "\"}");
  }

  private static String intencion(
      String tipo, String referencia, UUID pago, long importe, String moneda, String motivo) {
    String error =
        motivo == null ? "null" : "{\"message\":\"" + motivo + "\",\"code\":\"card_declined\"}";
    return evento(
        tipo,
        "{\"id\":\""
            + referencia
            + "\",\"object\":\"payment_intent\",\"amount_received\":"
            + importe
            + ",\"currency\":\""
            + moneda
            + "\",\"metadata\":{\"payment_id\":\""
            + (pago == null ? "" : pago)
            + "\"},\"last_payment_error\":"
            + error
            + "}");
  }

  private static String evento(String tipo, String objeto) {
    return "{\"id\":\"evt_"
        + UUID.randomUUID()
        + "\",\"type\":\""
        + tipo
        + "\",\"data\":{\"object\":"
        + objeto
        + "}}";
  }
}
