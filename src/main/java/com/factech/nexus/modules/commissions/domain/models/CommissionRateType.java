package com.factech.nexus.modules.commissions.domain.models;

/**
 * En qué forma paga una comisión (`RN-CM-016`, `cm.md` §1.1.1).
 *
 * <p><b>Una tasa declara una forma y solo una.</b> No se suman: no existe «5 % más 10.000». El tipo
 * manda y el campo de la otra forma va vacío.
 *
 * <p><b>Existe aunque parezca deducible de qué campo esté lleno</b>, y esa es la decisión de fondo.
 * Sin él, «una forma y solo una» sería una propiedad emergente de dos nulos, y ni el {@code CHECK}
 * ni la validación podrían decir <b>cuál</b> de las dos formas quiso declarar quien envió una
 * petición con las dos vacías — o con las dos llenas. El mensaje de rechazo sería el mismo para dos
 * equivocaciones distintas.
 *
 * <p><b>El orden de declaración no significa nada aquí</b>, al revés que en {@link RateSource},
 * donde es la precedencia. Reordenarlo no rompe ninguna regla.
 */
public enum CommissionRateType {

  /** Una proporción de la venta. Acotada de cero a cien (`RN-CM-007`). */
  PORCENTAJE,

  /**
   * Una cantidad de dinero por venta.
   *
   * <p><b>Sin moneda</b> (`RN-CM-017`): toma la del producto que se venda, de modo que la misma
   * tasa paga cosas distintas en productos de monedas distintas. <b>Y sin tope por arriba</b>
   * (`RN-CM-018`): puede superar el precio de la venta con un solo nivel de la cadena.
   */
  FIJO
}
