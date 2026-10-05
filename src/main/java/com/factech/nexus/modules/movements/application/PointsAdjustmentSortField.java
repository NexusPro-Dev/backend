package com.factech.nexus.modules.movements.application;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Los tres órdenes del listado de ajustes (`RF-MV-053`, `VAL-002`), con el identificador de
 * desempate en el mismo sentido, como {@code CourseCategorySortField}. <b>El orden por omisión es
 * el más reciente primero</b>, que es el del índice {@code ix_movements_ajustes}.
 */
public enum PointsAdjustmentSortField {
  OCCURRED_AT("occurredAt", "m.occurred_at", true),
  POINTS("points", "m.points_amount", true),
  CODE("code", "m.code", false);

  public static final String POR_OMISION = "m.occurred_at DESC, m.id DESC";

  private final String publico;
  private final String columna;
  private final boolean desciendePorOmision;

  PointsAdjustmentSortField(String publico, String columna, boolean desciendePorOmision) {
    this.publico = publico;
    this.columna = columna;
    this.desciendePorOmision = desciendePorOmision;
  }

  /** El SQL del orden, o {@code VAL-002} si el campo o el sentido no son de la lista blanca. */
  public static String resolver(String sort) {
    if (sort == null || sort.isBlank()) {
      return POR_OMISION;
    }
    String[] partes = sort.split(",", 2);
    String campo = partes[0].trim();
    PointsAdjustmentSortField resuelto =
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
    String direccion = "desc".equals(sentido) ? " DESC" : " ASC";
    return resuelto.columna + direccion + ", m.id" + direccion;
  }

  private static ValidationException rechazar(String valor) {
    String mensaje =
        "No se puede ordenar por '"
            + valor
            + "'. Campos admitidos: "
            + String.join(", ", Arrays.stream(values()).map(v -> v.publico).toList())
            + ", con ,asc o ,desc.";
    return new ValidationException(
        "VAL-002", mensaje, List.of(new FieldError("sort", "VAL-002", mensaje)));
  }
}
