package com.factech.nexus.modules.system.users.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.List;
import java.util.Locale;

/**
 * Lista blanca de campos por los que se puede ordenar el listado de personas (`RF-SP-025`).
 *
 * <p><b>Es una lista blanca y no una negra</b>, y esa es toda la defensa: el nombre que llega del
 * cliente se resuelve contra este enumerado <b>antes</b> de construir la consulta, de modo que su
 * cadena nunca llega a la sentencia. Con una lista negra, cada columna nueva nacería ordenable y
 * habría que acordarse de prohibirla.
 *
 * <p>Cada exclusión tiene su motivo, y dos no son de rendimiento:
 *
 * <ul>
 *   <li>{@code passwordHash} y {@code mustChangePassword} — `spec.md` §10 es explícita: <b>no se
 *       admite ordenar por ningún campo de la credencial</b>. Ordenar por el resumen no responde
 *       nada; ordenar por la marca produce <b>la lista de quien no ha cambiado su contraseña
 *       inicial</b>, que es exactamente una lista que nadie debería poder pedir.
 *   <li>{@code lastLoginAt}, {@code lockedUntil} y {@code failedAttempts} — no se devuelven, y
 *       ordenar por un campo invisible produce un orden que el cliente no puede explicar.
 *   <li>{@code roles} y {@code membership} — no son escalares: ordenar por una colección obliga a
 *       decidir por cuál de sus elementos.
 *   <li>{@code deletedAt} — agrupa a los eliminados, que es lo que ya hace el filtro.
 * </ul>
 */
public enum UserSortField {
  USERNAME("username", "u.username"),
  EMAIL("email", "u.email"),
  FIRST_NAME("firstName", "u.first_name"),
  LAST_NAME("lastName", "u.last_name"),
  STATUS("status", "u.status"),
  CREATED_AT("createdAt", "u.created_at"),
  UPDATED_AT("updatedAt", "u.updated_at");

  private final String publico;
  private final String columna;

  UserSortField(String publico, String columna) {
    this.publico = publico;
    this.columna = columna;
  }

  public String columna() {
    return columna;
  }

  /**
   * Resuelve {@code campo,sentido}. Ausente equivale a {@code lastName,asc}.
   *
   * <p><b>El orden por omisión es el apellido y no el nombre de usuario</b>, al revés que en el
   * catálogo de roles: esta es la lista desde la que se administra el acceso de personas, y quien
   * la mira busca a alguien por su apellido.
   *
   * @return la cláusula de ordenamiento, ya desempatada por identificador
   */
  public static String resolver(String sort) {
    if (sort == null || sort.isBlank()) {
      return LAST_NAME.columna + " ASC, u.id ASC";
    }

    String[] partes = sort.split(",", 2);
    String campo = partes[0].trim();
    String sentido = partes.length > 1 ? partes[1].trim().toLowerCase(Locale.ROOT) : "asc";

    UserSortField resuelto =
        java.util.Arrays.stream(values())
            .filter(valor -> valor.publico.equalsIgnoreCase(campo))
            .findFirst()
            .orElseThrow(() -> rechazar(campo));

    if (!"asc".equals(sentido) && !"desc".equals(sentido)) {
      throw rechazar(sort);
    }
    // El desempate por identificador no es cosmético: sin él, dos páginas
    // consecutivas pueden repetir a una persona y omitir a otra.
    return resuelto.columna + ("desc".equals(sentido) ? " DESC" : " ASC") + ", u.id ASC";
  }

  private static ValidationException rechazar(String valor) {
    String mensaje =
        "No se puede ordenar por '"
            + valor
            + "'. Campos admitidos: "
            + String.join(", ", admitidos())
            + ".";
    return new ValidationException(
        "VAL-003", mensaje, List.of(new FieldError("sort", "VAL-003", mensaje)));
  }

  private static List<String> admitidos() {
    return java.util.Arrays.stream(values()).map(valor -> valor.publico).toList();
  }
}
