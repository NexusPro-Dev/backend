package com.factech.nexus.modules.system.roles.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Lista blanca de campos por los que se puede ordenar el listado de roles (`RF-SP-002`).
 *
 * <p><b>Es una lista blanca y no una negra</b>, y esa es toda la defensa: el nombre que llega del
 * cliente se resuelve contra este enumerado <b>antes</b> de construir la consulta, de modo que su
 * cadena nunca llega a la sentencia. Con una lista negra, cada columna nueva nacería ordenable y
 * habría que acordarse de prohibirla. Es la misma mecánica de {@code UserSortField} (`RF-SP-025`) y
 * por el mismo motivo: sin ella, el parámetro de ordenamiento es una vía de inyección, porque es
 * texto del cliente que acaba en el {@code ORDER BY}.
 *
 * <p>Cada exclusión tiene su motivo:
 *
 * <ul>
 *   <li>{@code description} — ordenar por un texto libre de hasta quinientos caracteres no responde
 *       ninguna pregunta, y no está indexado.
 *   <li>{@code parentRoleId} — ordenar por un UUID opaco tampoco: el orden resultante no significa
 *       nada para quien lo mira.
 *   <li>{@code deletedAt} — agrupa a los eliminados, que es lo que ya hace el filtro.
 *   <li>{@code isSystem} y todo lo que no pertenezca a la tabla —los permisos, entre ellos— porque
 *       no son escalares del propio rol.
 * </ul>
 */
public enum RoleSortField {
  CODE("code", "r.code"),
  NAME("name", "r.name"),
  ROLE_TYPE("roleType", "r.role_type"),
  STATUS("status", "r.status"),
  CREATED_AT("createdAt", "r.created_at"),
  UPDATED_AT("updatedAt", "r.updated_at");

  private final String publico;
  private final String columna;

  RoleSortField(String publico, String columna) {
    this.publico = publico;
    this.columna = columna;
  }

  public String columna() {
    return columna;
  }

  /**
   * Resuelve {@code campo,sentido}. Ausente equivale a {@code code,asc}.
   *
   * <p><b>El orden por omisión es el código y no el nombre</b>, al revés que en el listado de
   * personas: el código es el identificador con el que se habla de un rol en la documentación, en
   * la auditoría y entre quienes lo administran.
   *
   * @return la cláusula de ordenamiento, ya desempatada por identificador
   */
  public static String resolver(String sort) {
    if (sort == null || sort.isBlank()) {
      return CODE.columna + " ASC, r.id ASC";
    }

    String[] partes = sort.split(",", 2);
    String campo = partes[0].trim();
    String sentido = partes.length > 1 ? partes[1].trim().toLowerCase(Locale.ROOT) : "asc";

    RoleSortField resuelto =
        Arrays.stream(values())
            .filter(valor -> valor.publico.equalsIgnoreCase(campo))
            .findFirst()
            .orElseThrow(() -> rechazar(campo));

    if (!"asc".equals(sentido) && !"desc".equals(sentido)) {
      throw rechazar(sort);
    }
    // El desempate por identificador no es cosmético: ordenando por `status`,
    // donde casi todas las filas comparten valor, el orden de las empatadas
    // queda a criterio del plan de ejecución y puede cambiar entre la página 1 y
    // la 2 — filas repetidas en una y ausentes en la otra.
    return resuelto.columna + ("desc".equals(sentido) ? " DESC" : " ASC") + ", r.id ASC";
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
    return Arrays.stream(values()).map(valor -> valor.publico).toList();
  }
}
