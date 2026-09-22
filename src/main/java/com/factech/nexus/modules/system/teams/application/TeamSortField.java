package com.factech.nexus.modules.system.teams.application;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Los dos órdenes admitidos del listado de equipos (`RF-SP-064` §6.2, `VAL-003`).
 *
 * <p><b>El orden por omisión es el nombre</b>, ascendente, y no la fecha de alta como en el
 * producto ni el orden propio como en la categoría: un equipo no tiene número con el que ordenarse
 * y se busca por su nombre, que es lo único que lo identifica (`RN-SP-050`). La lista es corta y se
 * recorre con la vista.
 *
 * <p>{@code name} ordena por {@code f_unaccent(lower(name))} —<b>la expresión de {@code
 * uq_teams_name} y de {@code ix_teams_busqueda}</b>— para que el alfabético no separe «Región
 * Norte» de «Region Norte»; {@code createdAt} desciende por omisión, como en `PM` y en `AC`, porque
 * quien ordena por fecha quiere ver lo último creado.
 *
 * <p>Siempre con el identificador de desempate en el mismo sentido: es un UUID v7 y su orden es el
 * cronológico, de modo que dos equipos que empaten salen con el más antiguo primero.
 */
public enum TeamSortField {
  NAME("name", "f_unaccent(lower(t.name))", false),
  CREATED_AT("createdAt", "t.created_at", true);

  public static final String POR_OMISION = "f_unaccent(lower(t.name)) ASC, t.id ASC";

  private final String publico;
  private final String columna;
  private final boolean desciendePorOmision;

  TeamSortField(String publico, String columna, boolean desciendePorOmision) {
    this.publico = publico;
    this.columna = columna;
    this.desciendePorOmision = desciendePorOmision;
  }

  public static Orden resolver(String sort) {
    if (sort == null || sort.isBlank()) {
      return new Orden(POR_OMISION, NAME.publico + ",asc");
    }
    String[] partes = sort.split(",", 2);
    String campo = partes[0].trim();
    TeamSortField resuelto =
        Arrays.stream(values())
            .filter(valor -> valor.publico.equalsIgnoreCase(campo))
            .findFirst()
            .orElseThrow(() -> rechazar(campo));
    String sentido =
        partes.length > 1
            ? partes[1].trim().toLowerCase(Locale.ROOT)
            : (resuelto.desciendePorOmision ? "desc" : "asc");
    if (!"asc".equals(sentido) && !"desc".equals(sentido)) {
      throw rechazar(sort);
    }
    boolean descendente = "desc".equals(sentido);
    return new Orden(
        resuelto.columna
            + (descendente ? " DESC" : " ASC")
            + (descendente ? ", t.id DESC" : ", t.id ASC"),
        resuelto.publico + "," + sentido);
  }

  public record Orden(String sql, String publico) {}

  private static ValidationException rechazar(String valor) {
    String mensaje =
        "El campo de ordenamiento no es admitido: '"
            + valor
            + "'. Campos admitidos: "
            + String.join(", ", Arrays.stream(values()).map(v -> v.publico).toList())
            + ".";
    return new ValidationException(
        "VAL-003", mensaje, List.of(new FieldError("sort", "VAL-003", mensaje)));
  }
}
