package com.factech.nexus.modules.system.permissions.application;

import java.util.Optional;

/**
 * Criterios de consulta del catálogo de permisos, ya normalizados (`RF-SP-010` · `T-05`).
 *
 * <p>No lleva ningún tipo de HTTP: {@code ListPermissionsRequest} traduce los parámetros del borde
 * a esta consulta y el dominio no sabe de dónde vinieron.
 *
 * <p><b>Los tres criterios son opcionales</b> y no hay validación que aplicar: `spec.md` §11 lo
 * declara así, y un valor que no corresponda a ningún permiso produce una colección vacía, que no
 * es un error. Lo único que ocurre aquí es la normalización de §4 del plan: el término se recorta,
 * y si queda vacío no se añade predicado.
 *
 * <p>Los tres campos son {@link Optional} a propósito. Un {@code null} obligaría a cada consumidor
 * a recordar comprobarlo, y el adaptador construye el predicado preguntando exactamente «¿está
 * presente este filtro?».
 */
public record ListPermissionsQuery(
    Optional<String> resource, Optional<String> action, Optional<String> searchTerm) {

  /**
   * Construye la consulta normalizando los tres criterios.
   *
   * <p>El recorte del término de búsqueda lo exige `plan.md` §4: un término en blanco equivale a
   * ausente y no debe añadir predicado. Sin ese recorte, buscar con la barra vacía —que envía una
   * cadena de espacios— filtraría por espacios y devolvería nada.
   *
   * <p><b>La misma regla se aplica a {@code resource} y {@code action}</b>, que el plan no
   * normaliza de forma explícita. Un {@code ?resource=} vacío significa que el cliente no está
   * filtrando, no que quiera los permisos del recurso llamado «cadena vacía»; tratarlo como filtro
   * devolvería siempre la colección vacía y ningún cliente lo pretende. Se aplica aquí y se deja
   * dicho, no se decide en el adaptador.
   */
  public static ListPermissionsQuery of(String resource, String action, String searchTerm) {
    return new ListPermissionsQuery(normalize(resource), normalize(action), normalize(searchTerm));
  }

  /** Consulta sin filtros: el catálogo completo. */
  public static ListPermissionsQuery all() {
    return new ListPermissionsQuery(Optional.empty(), Optional.empty(), Optional.empty());
  }

  private static Optional<String> normalize(String value) {
    if (value == null) {
      return Optional.empty();
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
  }
}
