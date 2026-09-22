package com.factech.nexus.modules.movements.domain.models;

/**
 * La forma de una rebaja de línea (`RN-MV-027`): {@link #PORCENTAJE} de cero a cien sobre el precio
 * unitario, o {@link #FIJO} en la moneda de la venta.
 *
 * <p><b>Son los dos valores de {@code DiscountType} de `PM`, y se declaran otra vez a
 * propósito.</b> `MV` no depende de {@code products..domain..} (regla de ArchUnit, `RF-MV-001` ·
 * `T-19`), y un enumerado compartido sería la primera grieta de esa frontera. Es un duplicado de
 * dos literales, no de un modelo: la cuenta de cómo se rebaja un producto dentro de un paquete
 * sigue viviendo en `PM` (`RN-PM-036`); aquí solo se congela su resultado.
 */
public enum MovementDiscountType {
  PORCENTAJE,
  FIJO
}
