package com.factech.nexus.modules.movements.domain.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de los intentos de pago (`RN-MV-039`, `RN-MV-040`; `RF-MV-018` · `plan.md` §3).
 *
 * <p>El primer pago de una venta lo escribe {@link MovementRepository#save}, en el mismo acto que
 * la cabecera; aquí vive lo que ocurre <b>después</b>: volver a pagar, y reconocer una clave
 * repetida.
 */
public interface PaymentRepository {

  /** El pago que ya lleva esta clave, para decidir si una petición repetida es la misma. */
  Optional<KeyedPayment> findByKey(String idempotencyKey);

  /**
   * La venta que el actor quiere volver a pagar, <b>solo si es suya y es una venta</b>: vacío en
   * cualquier otro caso, que es lo que hace indistinguibles lo ajeno y lo inexistente (`EX-001`).
   */
  Optional<RetryTarget> findOwnSale(UUID movementId, UUID actorId);

  /**
   * Abre un pago {@code PENDIENTE}.
   *
   * <p><b>{@code ON CONFLICT DO NOTHING} sin columna</b>, y es deliberado: cualquiera de las dos
   * restricciones que pueden chocar —la clave y el pendiente único— deja la transacción viva y
   * devuelve cero filas, de modo que quien llama puede <b>releer</b> para explicar el porqué sin
   * abrir otra transacción (`plan.md` §7).
   *
   * @return {@code true} si el pago entró
   */
  boolean open(
      UUID paymentId,
      UUID movementId,
      UUID paymentMethodId,
      BigDecimal amount,
      String idempotencyKey,
      OffsetDateTime at);

  /** ¿Tiene el movimiento un pago pendiente? Distingue `EX-004` de una clave repetida. */
  boolean hasPending(UUID movementId);

  /**
   * `RF-MV-004`: el pago pendiente de una venta pendiente pasa a {@code RECHAZADO}, con el instante
   * y el motivo, <b>bloqueando antes la fila de la venta</b> —la misma que bloquea confirmar—, de
   * modo que rechazar y confirmar no se cruzan.
   *
   * @return {@code true} si esta llamada rechazó
   */
  boolean rejectPendingOfSale(UUID movementId, OffsetDateTime at, String reason);

  /**
   * `RF-MV-020`: el pago que liquida un retiro, <b>ya confirmado</b> —quien aprueba declara que el
   * dinero salió— con el método y la referencia de la transferencia.
   */
  void insertConfirmed(
      UUID paymentId,
      UUID movementId,
      UUID paymentMethodId,
      BigDecimal amount,
      String idempotencyKey,
      String providerReference,
      OffsetDateTime at);

  // ---------------------------------------------------------------------------
  // La pasarela de la tarjeta (`RF-MV-040` a `RF-MV-042`, 01-10-2026)
  // ---------------------------------------------------------------------------

  /** Anota en el pago la referencia del cobro que la pasarela abrió (`RN-MV-040`). */
  void setProviderReference(UUID paymentId, String reference);

  /**
   * El pago pendiente de un movimiento —venta o compra de puntos—, con la fila del
   * <b>movimiento</b> bloqueada: la misma que bloquean confirmar, rechazar y anular, de modo que
   * nada se cruza. Vacío si el movimiento no existe, no es de esos tipos o no tiene pago pendiente.
   */
  Optional<PendingPayment> lockPendingOf(UUID movementId);

  /** Lo mismo, solo si el movimiento es del actor (`RF-MV-042`). */
  Optional<PendingPayment> lockPendingOwn(UUID movementId, UUID actorId);

  /** ¿Es una venta o una compra de puntos del actor, sea cual sea su estado? (`RF-MV-042`) */
  boolean isOwnChargeable(UUID movementId, UUID actorId);

  /** El pago cuyo cobro es esa referencia, con lo que la notificación necesita comprobar. */
  Optional<ReferencedPayment> findByReference(String reference);

  /**
   * Marca la incidencia de un pago <b>confirmado</b> (`RN-MV-060`).
   *
   * @return {@code false} si el pago no está confirmado
   */
  boolean setIncident(UUID paymentId, String incident, BigDecimal refunded, OffsetDateTime at);

  // ---------------------------------------------------------------------------
  // Conciliar por el pago (`RF-MV-044`, `RF-MV-045`, 01-10-2026)
  // ---------------------------------------------------------------------------

  /**
   * Bloquea la fila del <b>movimiento</b> del pago —la que serializa confirmar, rechazar, anular y
   * volver a pagar— y devuelve su identificador. Vacío si el pago no existe.
   *
   * <p>El {@code movement_id} de un pago no cambia nunca, de modo que leerlo sin bloqueo es seguro
   * (`RF-MV-044` · `plan.md` §1).
   */
  Optional<UUID> lockMovementOf(UUID paymentId);

  /**
   * El pago, leído <b>después</b> de {@link #lockMovementOf}: una sentencia nueva ve el estado que
   * dejó quien tenía el bloqueo. Vacío si no existe.
   */
  Optional<PaymentTarget> findTarget(UUID paymentId);

  /**
   * Lo que conciliar un pago necesita saber de él.
   *
   * @param gateway la pasarela del método, o nula
   * @param providerReference la referencia del pago, o nula
   */
  record PaymentTarget(
      UUID paymentId,
      UUID movementId,
      String movementType,
      String status,
      String gateway,
      String providerReference) {

    /** La misma lectura que {@link PendingPayment#tieneCobroAbierto}. */
    public boolean tieneCobroAbierto() {
      return gateway != null && providerReference != null;
    }
  }

  /**
   * Un pago pendiente, con todo lo que hace falta para cobrarlo o cancelar su cobro.
   *
   * @param gateway la pasarela del método, o nula
   * @param providerReference el cobro abierto, o nulo
   */
  record PendingPayment(
      UUID paymentId,
      UUID movementId,
      String movementCode,
      String movementType,
      String movementStatus,
      String methodCode,
      String gateway,
      BigDecimal amount,
      String currencyCode,
      int currencyDecimals,
      String idempotencyKey,
      String providerReference) {

    public boolean tieneCobroAbierto() {
      return gateway != null && providerReference != null;
    }
  }

  record ReferencedPayment(
      UUID paymentId,
      UUID movementId,
      String movementType,
      String status,
      BigDecimal amount,
      String currencyCode,
      int currencyDecimals) {}

  record KeyedPayment(UUID paymentId, UUID movementId, UUID paymentMethodId) {}

  record RetryTarget(UUID movementId, String status, BigDecimal payableAmount) {}
}
