package com.factech.nexus.modules.products.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.ValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `RN-PM-037` — forma, cota y resta en un solo tipo (`RF-PM-023` · `T-01`).
 *
 * <p>Las cotas en el céntimo; {@code 49.00} y {@code 49.0000} son el mismo precio; y {@code
 * precioDentroDe} en fijo, porcentaje y fijo mayor que el precio.
 */
class DiscountValueTest {

  @Test
  @DisplayName("forma: el porcentaje va de 0 a 100 con dos decimales; 100.01 y 12.345 se rechazan")
  void formaDelPorcentaje() {
    assertThatCode(() -> DiscountValue.of(DiscountType.PORCENTAJE, new BigDecimal("12.5"), 2))
        .doesNotThrowAnyException();
    assertThatCode(() -> DiscountValue.of(DiscountType.PORCENTAJE, new BigDecimal("100"), 2))
        .doesNotThrowAnyException();
    assertThatCode(() -> DiscountValue.of(DiscountType.PORCENTAJE, new BigDecimal("0"), 2))
        .doesNotThrowAnyException();

    assertThatThrownBy(() -> DiscountValue.of(DiscountType.PORCENTAJE, new BigDecimal("100.01"), 2))
        .isInstanceOfSatisfying(
            ValidationException.class, e -> assertThat(e.errorCode()).isEqualTo("VAL-003"));
    assertThatThrownBy(() -> DiscountValue.of(DiscountType.PORCENTAJE, new BigDecimal("12.345"), 2))
        .isInstanceOfSatisfying(
            ValidationException.class, e -> assertThat(e.errorCode()).isEqualTo("VAL-003"));
  }

  @Test
  @DisplayName("forma: el fijo no es negativo y cabe en los decimales de la moneda")
  void formaDelFijo() {
    assertThatCode(() -> DiscountValue.of(DiscountType.FIJO, new BigDecimal("49.99"), 2))
        .doesNotThrowAnyException();
    // 4150 en una moneda sin decimales: cabe.
    assertThatCode(() -> DiscountValue.of(DiscountType.FIJO, new BigDecimal("4150"), 0))
        .doesNotThrowAnyException();

    assertThatThrownBy(() -> DiscountValue.of(DiscountType.FIJO, new BigDecimal("-0.01"), 2))
        .isInstanceOfSatisfying(
            ValidationException.class, e -> assertThat(e.errorCode()).isEqualTo("VAL-004"));
    assertThatThrownBy(() -> DiscountValue.of(DiscountType.FIJO, new BigDecimal("49.999"), 2))
        .isInstanceOfSatisfying(
            ValidationException.class, e -> assertThat(e.errorCode()).isEqualTo("VAL-004"));
    assertThatThrownBy(() -> DiscountValue.of(DiscountType.FIJO, new BigDecimal("4150.5"), 0))
        .isInstanceOfSatisfying(
            ValidationException.class, e -> assertThat(e.errorCode()).isEqualTo("VAL-004"));
  }

  @Test
  @DisplayName("forma: sin forma o sin valor es VAL-002, y dice cuál falta")
  void formaObligatoria() {
    assertThatThrownBy(() -> DiscountValue.of(null, BigDecimal.ONE, 2))
        .isInstanceOfSatisfying(
            ValidationException.class, e -> assertThat(e.errorCode()).isEqualTo("VAL-002"));
    assertThatThrownBy(() -> DiscountValue.of(DiscountType.FIJO, null, 2))
        .isInstanceOfSatisfying(
            ValidationException.class, e -> assertThat(e.errorCode()).isEqualTo("VAL-002"));
  }

  @Test
  @DisplayName("cota: el fijo igual al precio pasa y el céntimo de más no; 49.00 es 49.0000")
  void cotaDelFijoEnElCentimo() {
    DiscountValue igual = DiscountValue.of(DiscountType.FIJO, new BigDecimal("49.00"), 2);
    assertThatCode(() -> igual.verificarCota(new BigDecimal("49.0000"), "USD", "EX-006"))
        .doesNotThrowAnyException();

    DiscountValue unCentimoMas = DiscountValue.of(DiscountType.FIJO, new BigDecimal("49.01"), 2);
    assertThatThrownBy(() -> unCentimoMas.verificarCota(new BigDecimal("49.0000"), "USD", "EX-006"))
        .isInstanceOfSatisfying(
            BusinessRuleException.class, e -> assertThat(e.errorCode()).isEqualTo("EX-006"))
        .hasMessageContaining("49 USD");
  }

  @Test
  @DisplayName("cota: el porcentaje nunca excede — cien de 49.00 es cero, no negativo")
  void cotaDelPorcentaje() {
    DiscountValue cien = DiscountValue.of(DiscountType.PORCENTAJE, new BigDecimal("100"), 2);
    assertThatCode(() -> cien.verificarCota(new BigDecimal("49.00"), "USD", "EX-006"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("cota: un producto GRATUITO solo admite cero, en las dos formas")
  void gratuitoSoloAdmiteCero() {
    assertThatCode(
            () ->
                DiscountValue.of(DiscountType.PORCENTAJE, BigDecimal.ZERO, 2)
                    .verificarCota(BigDecimal.ZERO, "USD", "EX-006"))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                DiscountValue.of(DiscountType.PORCENTAJE, new BigDecimal("10"), 2)
                    .verificarCota(new BigDecimal("0.00"), "USD", "EX-006"))
        .isInstanceOfSatisfying(
            BusinessRuleException.class, e -> assertThat(e.errorCode()).isEqualTo("EX-006"));
    assertThatThrownBy(
            () ->
                DiscountValue.of(DiscountType.FIJO, new BigDecimal("0.01"), 2)
                    .verificarCota(new BigDecimal("0.00"), "USD", "EX-003"))
        .isInstanceOfSatisfying(
            BusinessRuleException.class, e -> assertThat(e.errorCode()).isEqualTo("EX-003"));
  }

  @Test
  @DisplayName("precioDentroDe: fijo, porcentaje, y fijo mayor que el precio → cero")
  void precioDentroDe() {
    assertThat(
            DiscountValue.of(DiscountType.FIJO, new BigDecimal("20.00"), 2)
                .precioDentroDe(new BigDecimal("100.00"), 2))
        .isEqualByComparingTo("80.00");
    // 10 % de 49.99 = 4.999 → rebaja 5.00 → 44.99: se redondea la rebaja.
    assertThat(
            DiscountValue.of(DiscountType.PORCENTAJE, new BigDecimal("10"), 2)
                .precioDentroDe(new BigDecimal("49.99"), 2))
        .isEqualByComparingTo("44.99");
    assertThat(
            DiscountValue.of(DiscountType.FIJO, new BigDecimal("100.00"), 2)
                .precioDentroDe(new BigDecimal("50.00"), 2))
        .isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("mismoValorQue compara forma y cifra por valor, no por escala")
  void mismoValor() {
    DiscountValue a = DiscountValue.of(DiscountType.FIJO, new BigDecimal("10.00"), 2);
    DiscountValue b = DiscountValue.leido(DiscountType.FIJO, new BigDecimal("10.0000"));
    DiscountValue c = DiscountValue.of(DiscountType.PORCENTAJE, new BigDecimal("10.00"), 2);
    assertThat(a.mismoValorQue(b)).isTrue();
    assertThat(a.mismoValorQue(c)).isFalse();
    assertThat(a.mismoValorQue(null)).isFalse();
  }

  @Test
  @DisplayName("valorEnEscala: dos decimales el porcentaje, los de la moneda el fijo")
  void valorEnEscala() {
    assertThat(DiscountValue.leido(DiscountType.PORCENTAJE, new BigDecimal("10")).valorEnEscala(0))
        .hasScaleOf(2);
    assertThat(DiscountValue.leido(DiscountType.FIJO, new BigDecimal("10.0000")).valorEnEscala(0))
        .hasScaleOf(0);
  }
}
