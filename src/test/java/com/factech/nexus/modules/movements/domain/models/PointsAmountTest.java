package com.factech.nexus.modules.movements.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Los dos redondeos de la etapa 3, opuestos a propósito (`RN-MV-051`, `RN-MV-052`). */
class PointsAmountTest {

  @Test
  @DisplayName("comprar redondea hacia abajo: 10.00 a 0.3336 dan 3.33, no 3.34 (CA-MV-307)")
  void comprarHaciaAbajo() {
    assertThat(PointsAmount.comprados(new BigDecimal("10.00"), new BigDecimal("0.3336")))
        .isEqualByComparingTo("3.33");
    assertThat(PointsAmount.comprados(new BigDecimal("0.01"), new BigDecimal("0.0001")))
        .isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("pagar redondea hacia arriba: 10.00 a 0.3331 cuestan 3.34, no 3.33 (CA-MV-333)")
  void pagarHaciaArriba() {
    assertThat(PointsAmount.costo(new BigDecimal("10.00"), new BigDecimal("0.3331")))
        .isEqualByComparingTo("3.34");
  }

  @Test
  @DisplayName("una cifra exacta no se mueve en ningún sentido, y siempre sale a dos decimales")
  void exactas() {
    assertThat(PointsAmount.comprados(new BigDecimal("10.00"), new BigDecimal("100")))
        .isEqualTo(new BigDecimal("1000.00"));
    assertThat(PointsAmount.costo(new BigDecimal("10.00"), new BigDecimal("100")))
        .isEqualTo(new BigDecimal("1000.00"));
  }
}
