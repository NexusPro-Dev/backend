package com.factech.nexus.modules.system.users.application;

import java.util.UUID;

/**
 * Parámetros de {@code GET /api/v1/users} (`RF-SP-025`).
 *
 * <p><b>{@code countryId} tampoco se valida, y además NO se acota a países activos</b>
 * (`RN-SP-034`, 07-09-2026). Lo primero es el criterio de los otros dos filtros. Lo segundo es una
 * decisión aparte y va contra la intuición: sería fácil filtrar solo por países activos, y <b>eso
 * convertiría desactivar un país en una forma de esconder a su gente</b>. La condición de país
 * activo es del momento de <b>asignarlo</b> y no de leerlo, y este listado es justamente la
 * herramienta con la que se busca a quien quedó en un país retirado para moverlo con `RF-SP-027`.
 *
 * <p><b>Ni {@code roleId} ni {@code membershipId} se validan contra su catálogo.</b> Un filtro por
 * un rol inexistente devuelve la colección vacía y <b>no es un error</b>: validarlo añadiría una
 * consulta por petición para producir un fallo que la especificación no quiere. Lo único que se
 * exige de ellos es la forma canónica, y de eso se encarga el editor transversal.
 *
 * <p>{@code includeDeleted} es {@code Boolean} y no {@code boolean} a propósito: un tipo primitivo
 * en un {@code @ModelAttribute} hace que la petición <b>sin el parámetro</b> falle con {@code 400},
 * porque Spring intenta convertir la ausencia. Es el mismo defecto que el catálogo de monedas tuvo
 * que corregir.
 */
public record ListUsersRequest(
    Integer page,
    Integer size,
    String sort,
    String status,
    UUID roleId,
    UUID membershipId,
    UUID countryId,
    String search,
    Boolean includeDeleted) {

  public ListUsersRequest {
    // Recortado, y en blanco equivale a ausente: buscar por espacios es no
    // buscar, y añadir el predicado devolvería la lista entera igualmente pero
    // pagando el recorrido.
    search = search == null || search.isBlank() ? null : search.trim();
    status = status == null || status.isBlank() ? null : status.trim();
    sort = sort == null || sort.isBlank() ? null : sort.trim();
  }

  public boolean incluirEliminados() {
    return Boolean.TRUE.equals(includeDeleted);
  }
}
