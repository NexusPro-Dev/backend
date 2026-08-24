package com.factech.nexus.modules.system.countries.application;

/**
 * Parámetros de la consulta del catálogo de países (`RF-SP-021`).
 *
 * <p><b>Dos campos, y ninguno más.</b> No hay paginación ni ordenamiento: `spec.md` §6.1 lo decide,
 * y no aceptarlos siquiera es lo que lo hace verificable, porque Spring ignora en silencio los
 * parámetros de consulta que un DTO no declara.
 *
 * <p><b>{@code includeInactive} es {@code Boolean} y no {@code boolean}</b>: con un primitivo, la
 * ausencia del parámetro no tiene valor que pasar al constructor canónico y la consulta sin filtros
 * falla con {@code 400}.
 *
 * @param search sobre código y nombre; recortado, y en blanco equivale a ausente
 */
public record ListCountriesRequest(String search, Boolean includeInactive) {

  /** Por omisión, {@code false}: los inactivos se piden explícitamente. */
  public boolean incluirInactivos() {
    return Boolean.TRUE.equals(includeInactive);
  }
}
