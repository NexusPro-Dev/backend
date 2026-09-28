package com.factech.nexus.modules.commissions.domain.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Puerto de escritura de los lotes de comisión (`RF-CM-013`, y después `RF-CM-009` y `RF-CM-011`).
 */
public interface CommissionBatchRepository {

  /**
   * El lote {@code ABIERTO} de esa persona y moneda, <b>bloqueado</b> hasta el final de la
   * transacción; si no lo hay, lo abre (`RN-CM-033`, `plan.md` §4) con {@code period_start = at}
   * <b>o con el fin del último cerrado, si es posterior</b>: un devengo que tomó su instante justo
   * antes de un cierre y lo esperó no puede abrir un lote que empiece dentro del cerrado.
   *
   * <p>El bloqueo es lo que ordena el devengo contra el cierre: si el cierre tomó el lote primero,
   * este lo encuentra {@code PENDIENTE} al despertar y abre uno nuevo.
   */
  OpenBatch lockOpenBatch(UUID userId, UUID currencyId, OffsetDateTime at);

  /** Suma sobre la fila, no sobre lo leído: dos devengos simultáneos suman los dos. */
  void addToTotal(UUID batchId, BigDecimal amount, OffsetDateTime at);

  /**
   * El lote, <b>bloqueado</b> hasta el final de la transacción, para pagarlo (`RF-CM-011`): el
   * segundo de dos pagos simultáneos espera aquí y encuentra {@code PAGADO}.
   */
  java.util.Optional<BatchToPay> lockForPayment(UUID batchId);

  /** {@code PENDIENTE} → {@code PAGADO}, con la fecha y el movimiento del abono (`RN-CM-030`). */
  void markPaid(UUID batchId, OffsetDateTime at, UUID movementId);

  /** Lo que hace falta para abonar un lote. */
  record BatchToPay(
      UUID id, String code, UUID userId, UUID currencyId, BigDecimal totalAmount, String status) {}

  /** El lote abierto y el instante en que empezó su periodo. */
  record OpenBatch(UUID id, OffsetDateTime periodStart) {}
}
