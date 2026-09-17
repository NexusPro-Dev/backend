package com.factech.nexus.modules.products.application;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Los tres órdenes admitidos del listado de paquetes (`RF-PM-018` §6.1, `VAL-005`).
 *
 * <p><b>El precio no está en ninguna columna</b>, de modo que ordenar por él ordena por la
 * expresión de {@link #PRECIO_CALCULADO}: una subconsulta que suma el precio dentro del paquete de
 * cada producto <b>sin redondear</b>, solo para ordenar. El importe que viaja lo calcula {@code
 * PackagePricing} después, redondeado por producto; la diferencia entre las dos cuentas cabe en un
 * céntimo por producto y puede alterar el orden entre paquetes casi iguales (`spec.md` §14.2). La
 * expresión del descuento es la traducción literal de {@code DiscountValue.precioDentroDe}, y
 * `CA-PM-273` es el hilo que las mantiene iguales.
 */
public enum PackageSortField {
  NAME("name", "k.name"),
  PRICE("price", PackageSortField.PRECIO_CALCULADO),
  CREATED_AT("createdAt", "k.created_at");

  /** La suma sin redondear de {@code máx(0, precio − fijo)} y {@code precio × (1 − p ÷ 100)}. */
  public static final String PRECIO_CALCULADO =
      """
      (SELECT COALESCE(SUM(CASE WHEN i.discount_type = 'FIJO'
                                THEN GREATEST(0, p.price - i.discount_value)
                                ELSE p.price - p.price * i.discount_value / 100 END), 0)
         FROM product_package_items i JOIN products p ON p.id = i.product_id
        WHERE i.package_id = k.id)""";

  public static final String POR_OMISION = "k.created_at DESC, k.id DESC";

  private final String publico;
  private final String columna;

  PackageSortField(String publico, String columna) {
    this.publico = publico;
    this.columna = columna;
  }

  public static Orden resolver(String sort) {
    if (sort == null || sort.isBlank()) {
      return new Orden(POR_OMISION, CREATED_AT.publico + ",desc");
    }
    String[] partes = sort.split(",", 2);
    String campo = partes[0].trim();
    String sentido = partes.length > 1 ? partes[1].trim().toLowerCase(Locale.ROOT) : "asc";
    PackageSortField resuelto =
        Arrays.stream(values())
            .filter(valor -> valor.publico.equalsIgnoreCase(campo))
            .findFirst()
            .orElseThrow(() -> rechazar(campo));
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
        "VAL-005", mensaje, List.of(new FieldError("sort", "VAL-005", mensaje)));
  }
}
