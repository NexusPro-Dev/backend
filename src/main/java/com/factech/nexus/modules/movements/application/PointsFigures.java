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
 * <b>Las cifras de los puntos</b>, publicadas para `IN` (`RF-IN-005` · `T-02`; D-25).
 *
 * <p>Con la forma de {@link SalesFigures}: quien pregunta da los titulares ya resueltos, el
 * intervalo y la moneda, y recibe sumas. <b>Se cuenta sobre los asientos de las cuentas {@code
 * PUNTOS} de personas</b>, porque el evento del asiento ya dice qué clase de hecho fue
 * (`RN-IN-009`): {@code ABONO} es una compra cobrada, {@code PAGO} una venta pagada con puntos,
 * {@code AJUSTE} un ajuste a mano. Las cuentas de la empresa no cuentan.
 *
 * <p>Los puntos van con dos decimales, como se guardan, y <b>siempre en positivo</b>: el sentido lo
 * da {@link Kind}.
 */
public interface PointsFigures {

  /**
   * Lo que entró y salió de los puntos en el intervalo, por moneda y clase.
   *
   * @param holders los titulares; nulo es todo
   * @param currencyId si no es nulo, solo esa moneda
   */
  List<Flow> flows(Set<UUID> holders, Interval interval, UUID currencyId);

  /**
   * {@link #flows} partido en tramos de calendario de {@code zone} sobre cuándo se movieron los
   * puntos (`RN-IN-010`): solo los tramos con movimientos, con su inicio.
   */
  List<BucketFlow> flowsByBucket(
      Set<UUID> holders, Interval interval, UUID currencyId, Granularity granularity, ZoneId zone);

  /** El saldo de hoy, por moneda, de esos titulares; nulo es todo. */
  List<Balance> balances(Set<UUID> holders, UUID currencyId);

  /** Lo de una clase y moneda en un tramo. */
  record BucketFlow(LocalDate start, Flow flow) {}

  /** La clase de un movimiento de puntos. */
  enum Kind {
    /** Una compra de puntos cobrada. */
    PURCHASED,
    /** Un pago de una venta con puntos. */
    REDEEMED,
    /** Un ajuste a mano que sumó. */
    ADDED,
    /** Un ajuste a mano que restó. */
    REMOVED
  }

  /**
   * Los puntos de una clase en una moneda.
   *
   * @param points en positivo
   * @param count cuántos movimientos los produjeron
   */
  record Flow(UUID currencyId, String currencyCode, Kind kind, BigDecimal points, long count) {}

  /** El saldo de una moneda. */
  record Balance(UUID currencyId, String currencyCode, BigDecimal points) {}
}
