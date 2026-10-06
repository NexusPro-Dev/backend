package com.factech.nexus.modules.movements.application;

import com.factech.nexus.modules.movements.application.SalesFigures.Granularity;
import com.factech.nexus.modules.movements.application.SalesFigures.Interval;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * <b>Las cifras de los puntos</b>, publicadas para `IN` (`RF-IN-005`; D-25).
 *
 * <p>Con la forma de {@link SalesFigures}: quien pregunta da los titulares ya resueltos, el
 * intervalo y los filtros, y recibe sumas. <b>Se suman las filas de la lista de los movimientos de
 * puntos</b> (`RF-MV-056`, `RN-IN-009` enmendada el 06-10-2026), con su misma definición —la tabla
 * derivada de {@code JpaPointsMovementQuery}— y su misma fecha: cuándo ocurrió la compra o el
 * ajuste, y cuándo se descontaron los puntos de un gasto.
 *
 * <p>Los puntos van con dos decimales, como se guardan, y <b>siempre en positivo</b>: el sentido lo
 * da {@link Kind}.
 */
public interface PointsFigures {

  /**
   * Los movimientos de puntos del intervalo, por moneda, clase y estado.
   *
   * @param holders los titulares; nulo es todo
   * @param currencyId si no es nulo, solo esa moneda
   * @param type si no es nulo, solo ese tipo de la lista: {@code COMPRA_PUNTOS}, {@code
   *     AJUSTE_PUNTOS} o {@code GASTO_PUNTOS}
   * @param status si no es nulo, solo ese estado: {@code PENDIENTE}, {@code CONFIRMADA} o {@code
   *     RECHAZADA}
   */
  List<Flow> flows(
      Set<UUID> holders, Interval interval, UUID currencyId, String type, String status);

  /** {@link #flows} partido en tramos de calendario de {@code zone} (`RN-IN-010`). */
  List<BucketFlow> flowsByBucket(
      Set<UUID> holders,
      Interval interval,
      UUID currencyId,
      String type,
      String status,
      Granularity granularity,
      ZoneId zone);

  /** El saldo de hoy, por moneda, de esos titulares; nulo es todo. */
  List<Balance> balances(Set<UUID> holders, UUID currencyId);

  /** Lo de una clase, estado y moneda en un tramo. */
  record BucketFlow(LocalDate start, Flow flow) {}

  /** La clase de un movimiento de puntos: el tipo de la fila y, en el ajuste, su signo. */
  enum Kind {
    /** Una compra de puntos ({@code COMPRA_PUNTOS}), en cualquiera de sus estados. */
    PURCHASE,
    /** Una venta pagada con puntos ({@code GASTO_PUNTOS}). */
    SPENT,
    /** Un ajuste a mano que sumó. */
    ADDED,
    /** Un ajuste a mano que restó. */
    REMOVED
  }

  /**
   * Los movimientos de una clase y estado en una moneda.
   *
   * @param status el de la fila; el gasto y el ajuste son siempre {@code CONFIRMADA}
   * @param count cuántas filas
   * @param points en positivo; en una compra pendiente o rechazada, los que daría o habría dado
   * @param amount lo pagado, solo en una compra; cero en lo demás
   */
  record Flow(
      UUID currencyId,
      String currencyCode,
      Kind kind,
      String status,
      long count,
      BigDecimal points,
      BigDecimal amount) {}

  /** El saldo de una moneda. */
  record Balance(UUID currencyId, String currencyCode, BigDecimal points) {}
}
