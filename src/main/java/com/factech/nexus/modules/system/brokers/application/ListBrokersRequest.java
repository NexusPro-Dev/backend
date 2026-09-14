package com.factech.nexus.modules.system.brokers.application;

/**
 * Lo único que este catálogo admite que se le pida (`RF-SP-052`).
 *
 * <p><b>Un solo parámetro, y a propósito</b>: sin orden, sin paginación y sin búsqueda. Cada uno
 * sería superficie que alguien tendría que validar, para pintar un desplegable de unos pocos
 * elementos.
 */
public record ListBrokersRequest(Boolean includeInactive) {

  /** Ausente y {@code false} significan lo mismo: solo los activos. */
  public boolean incluirInactivos() {
    return Boolean.TRUE.equals(includeInactive);
  }
}
