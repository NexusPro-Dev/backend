package com.factech.nexus.modules.products.domain.models;

import java.math.BigDecimal;

/**
 * Lo que cobra en su venta propia quien no es el último eslabón de la fuerza comercial
 * (`RN-PM-051`, `RN-CM-045`).
 *
 * <p><b>Un valor sin reglas</b>: lleva el tipo y los dos campos tal como llegaron, incluido el que
 * sobra, porque quien valida tiene que poder decir <b>qué</b> está mal (`VAL-025` en el alta). Las
 * reglas —forma, rangos, tope contra el precio y exención FTD— viven en {@code
 * DirectCommissionRules}, que es quien conoce la moneda y el precio.
 *
 * @param type la forma; nula solo si el cuerpo no la trajo
 * @param percentage presente solo si {@code type} es {@link DirectCommissionType#PORCENTAJE}
 * @param fixedAmount presente solo si {@code type} es {@link DirectCommissionType#FIJO}
 */
public record DirectCommission(
    DirectCommissionType type, BigDecimal percentage, BigDecimal fixedAmount) {

  public static DirectCommission porcentaje(BigDecimal valor) {
    return new DirectCommission(DirectCommissionType.PORCENTAJE, valor, null);
  }

  public static DirectCommission fija(BigDecimal valor) {
    return new DirectCommission(DirectCommissionType.FIJO, null, valor);
  }

  /** El campo que corresponde al tipo. */
  public BigDecimal value() {
    return type == DirectCommissionType.FIJO ? fixedAmount : percentage;
  }

  /**
   * Dos directas iguales por valor y no por escala: {@code 10.00} y {@code 10.0000} son la misma, y
   * compararlas con {@code equals} llenaría la auditoría de cambios que no cambian nada.
   */
  public boolean mismaQue(DirectCommission otra) {
    if (otra == null) {
      return false;
    }
    return type == otra.type
        && mismo(percentage, otra.percentage)
        && mismo(fixedAmount, otra.fixedAmount);
  }

  private static boolean mismo(BigDecimal uno, BigDecimal otro) {
    if (uno == null || otro == null) {
      return uno == otro;
    }
    return uno.compareTo(otro) == 0;
  }

  /** Cómo se escribe en la auditoría: «PORCENTAJE 10.00», «FIJO 5000». */
  public String comoTexto() {
    return type.name() + " " + value().toPlainString();
  }
}
