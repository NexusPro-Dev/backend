package com.factech.nexus.modules.academy.application;

import java.util.UUID;

/**
 * Filtros, orden y paginación del listado de cursos (`RF-AC-009` §6.1).
 *
 * <p>Búsqueda por título, categoría, instructor, dificultad, estado e {@code includeDeleted}. Los
 * filtros en blanco equivalen a ausentes; los de dominio cerrado —dificultad y estado— los resuelve
 * el caso de uso y devuelve su {@code 400} <b>junto</b> con los de paginación y orden. {@code
 * offerable} no es filtro por lo mismo que en `PM`: se publica por fila.
 */
public record ListCoursesRequest(
    Integer page,
    Integer size,
    String sort,
    String q,
    UUID categoryId,
    UUID instructorId,
    String difficulty,
    String status,
    Boolean includeDeleted) {

  public ListCoursesRequest {
    q = q == null || q.isBlank() ? null : q.trim();
    sort = sort == null || sort.isBlank() ? null : sort.trim();
    difficulty = difficulty == null || difficulty.isBlank() ? null : difficulty.trim();
    status = status == null || status.isBlank() ? null : status.trim();
  }

  public boolean incluirEliminados() {
    return Boolean.TRUE.equals(includeDeleted);
  }
}
