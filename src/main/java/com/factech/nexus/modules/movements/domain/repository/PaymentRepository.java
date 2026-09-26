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

  record KeyedPayment(UUID paymentId, UUID movementId, UUID paymentMethodId) {}

  record RetryTarget(UUID movementId, String status, BigDecimal payableAmount) {}
}
