package com.factech.nexus.modules.academy.application;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Los tres órdenes admitidos del listado de categorías (`RF-AC-002` §6.1, `VAL-002`).
 *
 * <p><b>El orden por omisión es {@code displayOrder}</b>, ascendente, y no la fecha de alta como en
 * el producto: la categoría tiene orden propio (`RN-AC-002`) y esta es la vista con la que
 * administración comprueba que quedó como quería. {@code name} ordena por {@code
 * f_unaccent(lower(name))} —la expresión del índice de unicidad— para que el orden alfabético no
 * separe «Álgebra» de «Algoritmos»; {@code createdAt} desciende por omisión, como en `PM`.
 *
 * <p>Siempre con el identificador de desempate en el mismo sentido: es un UUID v7 y su orden es el
 * cronológico, de modo que dos categorías con el mismo número salen la más antigua primero.
 */
public enum CourseCategorySortField {
  DISPLAY_ORDER("displayOrder", "k.display_order", false),
  NAME("name", "f_unaccent(lower(k.name))", false),
  CREATED_AT("createdAt", "k.created_at", true);

  public static final String POR_OMISION = "k.display_order ASC, k.id ASC";

  private final String publico;
  private final String columna;
  private final boolean desciendePorOmision;

  CourseCategorySortField(String publico, String columna, boolean desciendePorOmision) {
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
    CourseCategorySortField resuelto =
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
            + (descendente ? ", k.id DESC" : ", k.id ASC"),
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
