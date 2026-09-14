package com.factech.nexus.modules.products.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** `RN-PM-031` — el redondeo vive aquí y solo aquí (`RF-PM-009` · `T-04`). */
class RatingSummaryTest {

  @Test
  @DisplayName("redondea a dos decimales a la mitad hacia arriba")
  void redondeaHalfUp() {
    assertThat(RatingSummary.de(new BigDecimal("4.335"), 3).average()).isEqualByComparingTo("4.34");
    assertThat(RatingSummary.de(new BigDecimal("4.3333333333"), 3).average())
        .isEqualByComparingTo("4.33");
    assertThat(RatingSummary.de(new BigDecimal("4.5"), 2).average().scale()).isEqualTo(2);
  }

  @Test
  @DisplayName("sin reseñas: average nulo y count cero — nulo no es cero")
  void vacio() {
    assertThat(RatingSummary.vacio().average()).isNull();
    assertThat(RatingSummary.vacio().count()).isZero();
    // El agregado del motor devuelve avg nulo y count cero sin filas.
    assertThat(RatingSummary.de(null, 0)).isEqualTo(RatingSummary.vacio());
  }

  @Test
  @DisplayName("nunca se construye con promedio y sin reseñas, ni al revés")
  void coherencia() {
    assertThatThrownBy(() -> new RatingSummary(new BigDecimal("4.00"), 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RatingSummary(null, 2))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RatingSummary(null, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
