package com.factech.nexus.modules.system.roles.application;

import java.util.UUID;

/**
 * Parámetros de {@code GET /api/v1/roles} (`RF-SP-002`).
 *
 * <p><b>{@code parentRoleId} no se valida contra el catálogo.</b> Un filtro por un rol padre
 * inexistente devuelve la colección vacía y <b>no es un error</b> (`spec.md` §13): comprobarlo
 * añadiría una consulta por petición para producir un fallo que la especificación no quiere. Lo
 * único que se exige de él es la forma canónica de un UUID, y de eso se encarga el editor
 * transversal.
 *
 * <p>{@code includeDeleted} es {@code Boolean} y no {@code boolean} a propósito: un tipo primitivo
 * en un {@code @ModelAttribute} hace que la petición <b>sin el parámetro</b> falle con {@code 400},
 * porque Spring intenta convertir la ausencia. Es el mismo defecto que el catálogo de monedas tuvo
 * que corregir y que `RF-SP-025` dejó anotado.
 *
 * <p>Los parámetros que este endpoint no declara <b>no existen</b>: Spring ignora en silencio los
 * de consulta que el DTO no tiene, de modo que enumerar aquí exactamente los ocho de `spec.md` §6.1
 * es lo que hace verificable que no se cuele ninguno más.
 */
public record ListRolesRequest(
    Integer page,
    Integer size,
    String sort,
    String status,
    String roleType,
    UUID parentRoleId,
    String search,
    Boolean includeDeleted) {

  public ListRolesRequest {
    // Recortado, y en blanco equivale a ausente (`spec.md` §13): buscar por
    // espacios es no buscar, y añadir el predicado devolvería el catálogo
    // entero igualmente pero pagando el recorrido.
    search = search == null || search.isBlank() ? null : search.trim();
    status = status == null || status.isBlank() ? null : status.trim();
    roleType = roleType == null || roleType.isBlank() ? null : roleType.trim();
    sort = sort == null || sort.isBlank() ? null : sort.trim();
  }

  /** Por omisión {@code false}: los eliminados se piden de forma explícita (`CA-SP-010`). */
  public boolean incluirEliminados() {
    return Boolean.TRUE.equals(includeDeleted);
  }
}
