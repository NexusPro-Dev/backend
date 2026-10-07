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

  /**
   * El lote <b>más reciente sin pagar</b> —`ABIERTO` o `PENDIENTE`— de esa persona y moneda,
   * <b>bloqueado</b>; si no hay ninguno, {@link #lockOpenBatch}, que abre uno (`RN-CM-051`). Solo
   * para la cadena de una línea reatribuida: el devengo de una venta nueva va al abierto.
   *
   * <p>Se busca y <b>después</b> se bloquea por identificador, mirando otra vez el estado: un pago
   * que ganó el bloqueo lo deja `PAGADO`, y entonces se busca el siguiente.
   */
  OpenBatch lockLatestUnpaidBatch(UUID userId, UUID currencyId, OffsetDateTime at);

  /** Suma sobre la fila, no sobre lo leído: dos devengos simultáneos suman los dos. */
  void addToTotal(UUID batchId, BigDecimal amount, OffsetDateTime at);

  /**
   * El lote, <b>bloqueado</b> hasta el final de la transacción, para pagarlo (`RF-CM-011`): el
   * segundo de dos pagos simultáneos espera aquí y encuentra {@code PAGADO}.
   */
  java.util.Optional<BatchToPay> lockForPayment(UUID batchId);

  /** {@code PENDIENTE} → {@code PAGADO}, con la fecha y el movimiento del abono (`RN-CM-030`). */
  void markPaid(UUID batchId, OffsetDateTime at, UUID movementId);

  /**
   * La comisión, <b>bloqueada</b> hasta el final de la transacción (`RF-CM-022`, `RF-CM-023`,
   * `RF-CM-024`). <b>El orden de los bloqueos del módulo es uno</b>: primero las comisiones,
   * después los lotes, y los lotes por identificador (`RF-CM-022` `plan.md` §1).
   */
  java.util.Optional<LockedCommission> lockCommission(UUID commissionId);

  /** Esos lotes, <b>bloqueados en orden de identificador</b>; los que no existen no vuelven. */
  java.util.List<LockedBatch> lockBatches(java.util.Collection<UUID> batchIds);

  /** La comisión pasa a otro lote, con el lote de origen anotado o vaciado (`RN-CM-046`). */
  void moveCommission(UUID commissionId, UUID toBatchId, UUID withdrawnFromBatchId);

  /** Si el lote tiene al menos una comisión (`RN-CM-048`). */
  boolean hasLiveCommissions(UUID batchId);

  /** Una comisión, con lo que hace falta para moverla. */
  record LockedCommission(UUID id, UUID batchId, BigDecimal amount, UUID withdrawnFromBatchId) {}

  /** Un lote bloqueado. */
  record LockedBatch(UUID id, String code, UUID userId, UUID currencyId, String status) {}

  /** Lo que hace falta para abonar un lote. */
  record BatchToPay(
      UUID id, String code, UUID userId, UUID currencyId, BigDecimal totalAmount, String status) {}

  /** El lote abierto y el instante en que empezó su periodo. */
  record OpenBatch(UUID id, OffsetDateTime periodStart) {}
}
