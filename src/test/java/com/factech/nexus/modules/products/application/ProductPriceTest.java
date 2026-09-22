package com.factech.nexus.modules.products.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La escala del precio (`CA-PM-082`, `RF-PM-003` · `plan.md` §10 riesgo 2).
 *
 * <p>Sin Spring, porque es una regla de presentación pura — y compartida por las tres respuestas
 * del módulo, que es lo que impide que el mismo producto llegue con dos precios distintos según por
 * dónde se pida.
 */
class ProductPriceTest {

  @Test
  @DisplayName("`CA-PM-082` — la escala la fija la MONEDA, no la columna: 49.9900 sale 49.99")
  void laEscalaLaFijaLaMoneda() {
    assertThat(ProductPrice.enLaEscalaDe(new BigDecimal("49.9900"), 2))
        .isEqualTo(new BigDecimal("49.99"));
  }

  @Test
  @DisplayName("en una moneda de CERO decimales, 50.0000 sale 50 y no 50.00")
  void monedaSinDecimales() {
    assertThat(ProductPrice.enLaEscalaDe(new BigDecimal("50.0000"), 0))
        .isEqualTo(new BigDecimal("50"));
  }

  @Test
  @DisplayName("un precio con menos decimales de los que admite su moneda se completa")
  void completaLosDecimalesQueFaltan() {
    // `10` en dólares es `10.00`: la moneda declara dos decimales y el contrato
    // los declara siempre, para que el cliente no tenga que decidir la escala.
    assertThat(ProductPrice.enLaEscalaDe(new BigDecimal("10"), 2))
        .isEqualTo(new BigDecimal("10.00"));
  }

  @Test
  @DisplayName("lo que NO cabe en la moneda no se redondea: se muestra")
  void loQueNoCabeSeMuestra() {
    // `RN-PM-007` impide al escribir que esto llegue por la API, de modo que
    // solo puede venir de una carga directa en la base. Recortarlo escondería
    // el dato inválido justo donde alguien podría verlo, y encima cobraría de
    // menos. `spec.md` §13 de `RF-PM-003` lo resuelve así.
    assertThat(ProductPrice.enLaEscalaDe(new BigDecimal("49.9950"), 2))
        .isEqualTo(new BigDecimal("49.995"));
  }

  @Test
  @DisplayName("y tampoco se redondea hacia arriba, que sería cobrar de más")
  void noRedondeaHaciaArriba() {
    assertThat(ProductPrice.enLaEscalaDe(new BigDecimal("49.9999"), 2))
        .isNotEqualTo(new BigDecimal("50.00"));
  }
}
