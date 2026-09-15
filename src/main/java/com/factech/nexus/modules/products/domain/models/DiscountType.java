package com.factech.nexus.modules.products.domain.models;

/**
 * La forma del descuento de un producto dentro de un paquete (`RN-PM-037`).
 *
 * <p>{@link #PORCENTAJE} de cero a cien sobre el precio del producto; {@link #FIJO} en la moneda
 * del paquete, de cero al precio del producto. La cuenta que los aplica vive en {@link
 * PackagePricing}.
 */
public enum DiscountType {
  PORCENTAJE,
  FIJO
}
