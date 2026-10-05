package com.factech.nexus.shared.persistence;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * La conversión entre un importe y las <b>centésimas</b> en que se guarda (ADR-006).
 *
 * <p>Todo importe en dinero vive en la base como {@code bigint}: {@code 12.50} se guarda {@code
 * 1250}. El dominio y la API siguen hablando en decimales, y este es el único sitio que conoce el
 * factor. Lo usa {@link MinorUnitsConverter} por dentro de las entidades y el SQL nativo por fuera:
 * <b>lo que se lee se convierte al mapearlo, lo que se compara se convierte antes de vincularlo, y
 * nunca se divide en SQL.</b>
 *
 * <p><b>El redondeo es una red, no la regla.</b> Un importe con más de dos decimales se redondea
 * con {@code HALF_UP}, el mismo que deshace el empate en PostgreSQL al migrar; la regla es el
 * rechazo de la API y el redondeo explícito del caso de uso. Un importe que llegue aquí sin
 * redondear es un defecto de quien lo calculó, aunque esta clase lo tape.
 */
public final class MinorUnits {

  /** Las dos cifras decimales que caben en una centésima. */
  public static final int ESCALA = 2;

  private MinorUnits() {}

  /**
   * El importe en centésimas. Nulo pasa a nulo.
   *
   * @throws ArithmeticException si no cabe en un {@code long}: truncarlo cambiaría el importe
   */
  public static Long toMinor(BigDecimal importe) {
    if (importe == null) {
      return null;
    }
    return importe.setScale(ESCALA, RoundingMode.HALF_UP).movePointRight(ESCALA).longValueExact();
  }

  /**
   * El importe que guardan unas centésimas, con escala dos. Nulo pasa a nulo.
   *
   * <p>Admite cualquier {@link Number} porque el SQL nativo no siempre devuelve un {@code Long}: un
   * {@code SUM} sobre {@code bigint} llega como {@code numeric}, y un {@code count} o una columna
   * {@code integer} como {@code Integer}. <b>Lo que no admite es un número con decimales</b>: unas
   * centésimas son enteras, y una fracción aquí delata que alguien ya dividió por su cuenta.
   */
  public static BigDecimal fromMinor(Object centesimas) {
    if (centesimas == null) {
      return null;
    }
    if (centesimas instanceof Long
        || centesimas instanceof Integer
        || centesimas instanceof Short) {
      return BigDecimal.valueOf(((Number) centesimas).longValue(), ESCALA);
    }
    if (centesimas instanceof BigInteger entero) {
      return new BigDecimal(entero, ESCALA);
    }
    if (centesimas instanceof BigDecimal decimal) {
      return new BigDecimal(decimal.toBigIntegerExact(), ESCALA);
    }
    throw new IllegalArgumentException(
        "Unas centésimas son un entero, y llegó " + centesimas.getClass().getName());
  }
}
