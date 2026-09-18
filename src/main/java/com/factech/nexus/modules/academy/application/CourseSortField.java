package com.factech.nexus.modules.academy.application;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Los tres órdenes admitidos del listado de cursos (`RF-AC-009` §6.1): {@code displayOrder} por
 * omisión y ascendente —el curso tiene orden propio (`RN-AC-002`)—, {@code title} sobre la
 * expresión del índice de unicidad, y {@code createdAt} descendente por omisión. Siempre con el
 * identificador de desempate en el mismo sentido, como {@link CourseCategorySortField}.
 */
public enum CourseSortField {
  DISPLAY_ORDER("displayOrder", "c.display_order", false),
  TITLE("title", "f_unaccent(lower(c.title))", false),
  CREATED_AT("createdAt", "c.created_at", true);

  public static final String POR_OMISION = "c.display_order ASC, c.id ASC";

  private final String publico;
  private final String columna;
  private final boolean desciendePorOmision;

  CourseSortField(String publico, String columna, boolean desciendePorOmision) {
    this.publico = publico;
    this.columna = columna;
    this.desciendePorOmision = desciendePorOmision;
  }

  public static Orden resolver(String sort) {
    if (sort == null || sort.isBlank()) {
      return new Orden(POR_OMISION, DISPLAY_ORDER.publico + ",asc");
    }
    String[] partes = sort.split(",", 2);
    String campo = partes[0].trim();
    CourseSortField resuelto =
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
            + (descendente ? ", c.id DESC" : ", c.id ASC"),
        resuelto.publico + "," + sentido);
  }

  public record Orden(String sql, String publico) {}

  private static ValidationException rechazar(String valor) {
    String mensaje =
        "No se puede ordenar por '"
            + valor
            + "'. Campos admitidos: "
            + String.join(", ", Arrays.stream(values()).map(v -> v.publico).toList())
            + ".";
    return new ValidationException(
        "VAL-002", mensaje, List.of(new FieldError("sort", "VAL-002", mensaje)));
  }
}
