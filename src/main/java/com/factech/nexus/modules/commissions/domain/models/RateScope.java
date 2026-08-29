package com.factech.nexus.modules.commissions.domain.models;

/**
 * El grado en que una tarifa fue declarada (`requirements/cm.md` §1.1).
 *
 * <p><b>Se calcula y no se guarda.</b> Una columna que dijera el grado podría contradecir a las
 * tres que lo determinan —«del rol» con una persona declarada— y esa contradicción no la detecta
 * nada. Es el mismo criterio con el que la tabla no tiene una casilla «para todos».
 *
 * <p><b>El orden de declaración es el orden de precedencia</b> de `RN-CM-004`, del más específico
 * al más general. No es documentación: {@link #esMasEspecificoQue} lo usa, de modo que reordenar
 * las constantes cambia la regla — y por eso se dice aquí.
 */
public enum RateScope {

  /** Esa persona, ese producto. El caso más específico. */
  PERSONA_Y_PRODUCTO,

  /** Esa persona, cualquier producto. */
  PERSONA,

  /** Cualquiera con el rol, ese producto. */
  PRODUCTO,

  /** Cualquiera con el rol, cualquier producto. La tarifa por omisión. */
  ROL;

  /**
   * El grado que corresponde a una tarifa, deducido de qué se declaró.
   *
   * <p><b>La ausencia es la que da el alcance</b>: sin persona rige para todos los del rol, sin
   * producto para todo el catálogo.
   */
  public static RateScope de(boolean tienePersona, boolean tieneProducto) {
    if (tienePersona) {
      return tieneProducto ? PERSONA_Y_PRODUCTO : PERSONA;
    }
    return tieneProducto ? PRODUCTO : ROL;
  }

  /**
   * ¿Este grado gana al otro? (`RN-CM-004`)
   *
   * <p>La persona pesa más que el producto: una excepción de persona sin producto gana a una tarifa
   * de producto sin persona. Está en el orden de las constantes y aquí solo se lee.
   */
  public boolean esMasEspecificoQue(RateScope otro) {
    return ordinal() < otro.ordinal();
  }
}
