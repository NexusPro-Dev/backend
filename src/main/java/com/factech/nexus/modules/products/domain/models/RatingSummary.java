package com.factech.nexus.modules.products.domain.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * El promedio y la cantidad de reseñas VIVAS de un producto (`RN-PM-031`).
 *
 * <h2>Es una cuenta, no una columna</h2>
 *
 * <p>No se guarda en {@code products}: se calcula sobre {@code product_comments} en la misma
 * sentencia que trae el producto. Una columna desnormalizada obligaría a mantenerla en el alta, la
 * corrección y el retiro de cada reseña, y <b>la que se quedara atrás no fallaría, mentiría</b>
 * (`requirements/pm.md` §5.2.7).
 *
 * <h2>El redondeo vive aquí y solo aquí</h2>
 *
 * <p>Dos decimales, {@link RoundingMode#HALF_UP}. Se hace en Java y no en SQL para que sea <b>una
 * sola regla</b>: el {@code ProductResponse} del alta no pasa por ninguna sentencia y construye el
 * suyo con {@link #vacio()}, y el día que alguien redondeara en el motor con otra regla, dos
 * lecturas del mismo producto darían dos promedios.
 *
 * <h2>Nulo no es cero</h2>
 *
 * <p>{@code average} es <b>nulo</b> cuando no hay reseñas: cero sería «todos la puntuaron pésimo»,
 * y no es lo mismo que «nadie la puntuó». {@code count} es cero en ese caso, y siempre está.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RatingSummary(BigDecimal average, long count) {

  private static final RatingSummary VACIO = new RatingSummary(null, 0);

  public RatingSummary {
    if (count < 0) {
      throw new IllegalArgumentException("La cantidad de reseñas no puede ser negativa.");
    }
    if ((count == 0) != (average == null)) {
      throw new IllegalArgumentException(
          "El promedio es nulo exactamente cuando no hay reseñas, y no en otro caso.");
    }
    if (average != null) {
      average = average.setScale(2, RoundingMode.HALF_UP);
    }
  }

  /** Un producto sin reseñas: promedio nulo y cantidad cero. */
  public static RatingSummary vacio() {
    return VACIO;
  }

  /**
   * Desde lo que devuelve el agregado de la sentencia: {@code avg(rating)} bruto —nulo sin filas— y
   * {@code count(*)} —cero sin filas—. {@code count(*)} sobre cero filas es cero y {@code avg} es
   * nulo, de modo que aquí no hay caso especial: el motor ya dice lo que la regla pide.
   */
  public static RatingSummary de(BigDecimal promedioBruto, long cantidad) {
    return cantidad == 0 ? VACIO : new RatingSummary(promedioBruto, cantidad);
  }
}
