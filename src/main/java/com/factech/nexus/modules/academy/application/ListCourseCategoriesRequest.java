package com.factech.nexus.modules.academy.application;

/**
 * Filtros, orden y paginación del listado de categorías (`RF-AC-002` §6.1).
 *
 * <p>Búsqueda por nombre e {@code includeDeleted}, y nada más: la categoría no tiene estado que
 * filtrar, y «vacía» no es filtro por lo mismo que {@code offerable} no lo es en `PM` — se publica
 * por fila. Los filtros en blanco equivalen a ausentes.
 */
public record ListCourseCategoriesRequest(
    Integer page, Integer size, String sort, String q, Boolean includeDeleted) {

  public ListCourseCategoriesRequest {
    q = q == null || q.isBlank() ? null : q.trim();
    sort = sort == null || sort.isBlank() ? null : sort.trim();
  }

  public boolean incluirEliminadas() {
    return Boolean.TRUE.equals(includeDeleted);
  }
}
