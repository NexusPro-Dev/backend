package com.factech.nexus.modules.system.currencies.application;

/**
 * Parámetros de la consulta del catálogo (`RF-SP-019`).
 *
 * <p><b>Un solo campo.</b> No hay paginación ni ordenamiento: `spec.md` §6.1 lo decide, y no
 * aceptarlos siquiera es lo que lo hace verificable, porque Spring ignora en silencio los
 * parámetros de consulta que un DTO no declara. Tampoco hay búsqueda: sobre un catálogo que hoy
 * tiene una fila sería ceremonia.
 *
 * <p><b>{@code includeInactive} añade, no sustituye.</b> Con {@code true} se devuelven activas e
 * inactivas, no solo las inactivas: un filtro que ocultara las activas respondería una pregunta que
 * nadie hace.
 *
 * <p><b>Es {@code Boolean} y no {@code boolean}</b>, y no es indiferente: Spring construye el
 * registro por su constructor canónico, y con un primitivo la <b>ausencia</b> del parámetro no
 * tiene valor que pasar — la petición sin filtros fallaba con {@code 400}. El envoltorio admite el
 * nulo, y {@link #incluirInactivas()} lo traduce al valor por omisión.
 *
 * <p>Sin Bean Validation, porque `spec.md` §11 no declara ninguna validación. Un valor no booleano
 * produce {@code 400} por conversión, antes de llegar al caso de uso.
 */
public record ListCurrenciesRequest(Boolean includeInactive) {

  /** Por omisión, {@code false}: las inactivas se piden explícitamente. */
  public boolean incluirInactivas() {
    return Boolean.TRUE.equals(includeInactive);
  }
}
