package com.factech.nexus.modules.products.domain.models;

/**
 * La forma de la comisión por venta directa de un producto (`RN-PM-051`).
 *
 * <p><b>Es un enumerado propio de `PM`</b>, aunque diga lo mismo que el de las tasas de `CM`: `CM`
 * consume `PM` y no al revés, de modo que importarlo cerraría un ciclo. Los valores coinciden a
 * propósito —son los de {@code ck_products_direct_commission_forma}— para que `CM` los lea sin
 * traducir.
 */
public enum DirectCommissionType {

  /** Un porcentaje de la base bruta de la línea, de cero a cien. */
  PORCENTAJE,

  /** Un importe por unidad, en la moneda del producto. */
  FIJO
}
