package com.factech.nexus.modules.movements.domain.service;

import java.util.UUID;

/**
 * <b>La pasarela que cobra la tarjeta</b> (`architecture.md` §15.4; `RF-MV-040` a `RF-MV-042`).
 *
 * <p>Un puerto de `MV`: lo implementa un adaptador de Stripe, que es el único sitio que conoce su
 * API. <b>Ningún tipo del proveedor cruza esta frontera</b>: cambiar de pasarela es cambiar el
 * adaptador, no los casos de uso.
 *
 * <p><b>Apagable</b>: sin sus credenciales, {@link #enabled()} es falso y la tarjeta vuelve a ser
 * un pago pendiente que confirma una persona.
 */
public interface CardGateway {

  /** Si hay pasarela en este entorno. */
  boolean enabled();

  /** El nombre con que se marca el método que esta pasarela cobra: {@code STRIPE}. */
  String name();

  /**
   * Abre un cobro. <b>Idempotente por la clave</b>: la misma clave devuelve el mismo cobro.
   *
   * @throws Unavailable si la pasarela no responde o se niega
   */
  Charge open(ChargeOrder orden);

  /** El cobro, con su secreto y su estado. @throws Unavailable */
  Charge retrieve(String reference);

  /**
   * Cancela un cobro que nadie ha pagado.
   *
   * @return {@link CancelResult#YA_COBRADO} si la pasarela ya lo cobró
   * @throws Unavailable si no responde
   */
  CancelResult cancel(String reference);

  /**
   * Verifica la firma de una notificación y la interpreta.
   *
   * @param body los bytes exactos recibidos: la firma es sobre ellos
   * @throws InvalidSignature si falta, no verifica o es vieja
   */
  GatewayEvent verify(byte[] body, String signature);

  /**
   * Interpreta una notificación ya guardada, sin verificar la firma: ya se verificó al recibirla.
   */
  GatewayEvent parse(String payload);

  /**
   * Si la notificación se procesa en el hilo que la recibió, después del {@code COMMIT}, en lugar
   * de en segundo plano. <b>Solo las pruebas</b>, para que lo procesado sea determinista.
   */
  boolean processInline();

  /** Lo que se cobra: en la unidad mínima de la moneda —centavos—, con su clave y su origen. */
  record ChargeOrder(
      long amountMinor,
      String currency,
      String idempotencyKey,
      UUID paymentId,
      UUID movementId,
      String movementCode) {}

  /** Un cobro: su referencia, el secreto con que la app lo completa, y su estado. */
  record Charge(String reference, String clientSecret, String status) {

    public boolean canceled() {
      return "canceled".equals(status);
    }
  }

  enum CancelResult {
    CANCELADO,
    YA_COBRADO
  }

  /**
   * Una notificación verificada, con lo que el sistema necesita de ella y el cuerpo entero.
   *
   * @param chargeReference el cobro al que se refiere —el {@code PaymentIntent}—, también en los
   *     eventos del cargo y de la disputa
   * @param paymentIdHint el pago anotado en el cobro al abrirlo; nulo en los eventos del cargo
   * @param amountMinor el importe cobrado, en un cobro que entró
   * @param refundedMinor el importe devuelto acumulado, en un reembolso
   * @param disputeStatus el desenlace de una disputa cerrada —{@code won}, {@code lost}—
   */
  record GatewayEvent(
      String externalId,
      String type,
      String payload,
      String chargeReference,
      String paymentIdHint,
      Long amountMinor,
      String currency,
      Long refundedMinor,
      String disputeStatus,
      String failureMessage) {}

  /** La pasarela no respondió, o se negó: nada se escribió y se puede reintentar. */
  class Unavailable extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public Unavailable(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** La firma de una notificación falta, no verifica o es vieja. */
  class InvalidSignature extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidSignature(String message) {
      super(message);
    }
  }
}
