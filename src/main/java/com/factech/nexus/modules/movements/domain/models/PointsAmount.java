package com.factech.nexus.modules.movements.domain.models;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cuántos puntos corresponden a un importe (`requirements/mv.md` §4.4).
 *
 * <p><b>Dos redondeos, y opuestos a propósito: nunca se regala una fracción.</b> Al comprar, los
 * puntos se redondean <b>hacia abajo</b> (`RN-MV-051`); al pagar con ellos, <b>hacia arriba</b>
 * (`RN-MV-052`). Siempre a dos decimales, que son los de la columna {@code accounts.balance}.
 */
public final class PointsAmount {

  /** La escala de un saldo de puntos. */
  public static final int ESCALA = 2;

  private PointsAmount() {}

  /** Los puntos que da una compra de {@code importe} a la {@code tasa}: hacia abajo. */
  public static BigDecimal comprados(BigDecimal importe, BigDecimal tasa) {
    return importe.multiply(tasa).setScale(ESCALA, RoundingMode.FLOOR);
  }

  /** Los puntos que cuesta una venta de {@code importe} a la {@code tasa}: hacia arriba. */
  public static BigDecimal costo(BigDecimal importe, BigDecimal tasa) {
    return importe.multiply(tasa).setScale(ESCALA, RoundingMode.CEILING);
  }
}
