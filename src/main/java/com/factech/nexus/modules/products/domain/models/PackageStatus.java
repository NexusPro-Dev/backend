package com.factech.nexus.modules.products.domain.models;

/**
 * Estado de publicación de un paquete (`RN-PM-041`).
 *
 * <p>Los mismos dos valores que {@link ProductStatus} y un enumerado aparte, a propósito: los dos
 * agregados se publican por separado, y el día que uno gane un estado el otro no tiene por qué
 * heredarlo.
 */
public enum PackageStatus {
  ACTIVO,
  INACTIVO
}
