package com.factech.nexus.modules.products.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `RN-PM-036` y `RN-PM-037` — la cuenta del paquete vive aquí y solo aquí (`RF-PM-017` · `T-04`).
 *
 * <p>Los dos casos de `requirements/pm.md` §5.2.10, el fijo mayor que el precio, el porcentaje
 * cien, la lista vacía y una moneda de cero decimales.
 */
class PackagePricingTest {

  private static final UUID ORO = UUID.randomUUID();
  private static final UUID BOT = UUID.randomUUID();

  @Test
  @DisplayName("el ejemplo del módulo: 10 % sobre 299.00 y 39.00 fijo sobre 39.00 → 269.10")
  void ejemploDelModulo() {
    PackagePricing cuenta =
        PackagePricing.calcular(
            2,
            List.of(
                new PackagePricing.Linea(ORO, new BigDecimal("299.00"), porcentaje("10")),
                new PackagePricing.Linea(BOT, new BigDecimal("39.00"), fijo("39.00"))));

    assertThat(cuenta.precioDe(ORO)).isEqualByComparingTo("269.10");
    assertThat(cuenta.precioDe(BOT)).isEqualByComparingTo("0.00");
    assertThat(cuenta.listPrice()).isEqualByComparingTo("338.00");
    assertThat(cuenta.price()).isEqualByComparingTo("269.10");
    assertThat(cuenta.savings()).isEqualByComparingTo("68.90");
  }

  @Test
  @DisplayName("se redondea POR PRODUCTO y el total es la suma: tres porcentajes con medio céntimo")
  void redondeaPorProductoYElTotalCuadra() {
    // 12.5 % de 49.99 = 6.24875 → rebaja 6.25 → 43.74; tres veces.
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();
    PackagePricing cuenta =
        PackagePricing.calcular(
            2,
            List.of(
                new PackagePricing.Linea(a, new BigDecimal("49.99"), porcentaje("12.5")),
                new PackagePricing.Linea(b, new BigDecimal("49.99"), porcentaje("12.5")),
                new PackagePricing.Linea(c, new BigDecimal("49.99"), porcentaje("12.5"))));

    assertThat(cuenta.precioDe(a)).isEqualByComparingTo("43.74");
    // La suma de las tres líneas redondeadas, no el redondeo de la suma.
    assertThat(cuenta.price()).isEqualByComparingTo("131.22");
    assertThat(cuenta.listPrice()).isEqualByComparingTo("149.97");
    assertThat(cuenta.savings()).isEqualByComparingTo("18.75");
    assertThat(cuenta.price().scale()).isEqualTo(2);
  }

  @Test
  @DisplayName("un fijo que hoy supera el precio cuenta CERO, nunca negativo (`RN-PM-037`)")
  void fijoMayorQueElPrecioCuentaCero() {
    PackagePricing cuenta =
        PackagePricing.calcular(
            2, List.of(new PackagePricing.Linea(BOT, new BigDecimal("50.00"), fijo("100.00"))));

    assertThat(cuenta.precioDe(BOT)).isEqualByComparingTo("0.00");
    assertThat(cuenta.price()).isEqualByComparingTo("0.00");
    assertThat(cuenta.savings()).isEqualByComparingTo("50.00");
  }

  @Test
  @DisplayName("el porcentaje cien deja el producto en cero, y el cero lo deja a su precio")
  void porcentajeCienYCero() {
    PackagePricing cuenta =
        PackagePricing.calcular(
            2,
            List.of(
                new PackagePricing.Linea(ORO, new BigDecimal("299.00"), porcentaje("100")),
                new PackagePricing.Linea(BOT, new BigDecimal("39.00"), porcentaje("0"))));

    assertThat(cuenta.precioDe(ORO)).isEqualByComparingTo("0.00");
    assertThat(cuenta.precioDe(BOT)).isEqualByComparingTo("39.00");
    assertThat(cuenta.price()).isEqualByComparingTo("39.00");
  }

  @Test
  @DisplayName("la lista vacía devuelve tres ceros en la escala de la moneda")
  void listaVacia() {
    PackagePricing cuenta = PackagePricing.calcular(2, List.of());

    assertThat(cuenta.listPrice()).isEqualByComparingTo("0").hasScaleOf(2);
    assertThat(cuenta.price()).isEqualByComparingTo("0").hasScaleOf(2);
    assertThat(cuenta.savings()).isEqualByComparingTo("0").hasScaleOf(2);
    assertThat(cuenta.precioDe(ORO)).isNull();
  }

  @Test
  @DisplayName("una moneda de cero decimales redondea a la unidad, a la mitad hacia arriba")
  void monedaSinDecimales() {
    // 15 % de 4150 = 622.5 → rebaja 623 → 3527.
    PackagePricing cuenta =
        PackagePricing.calcular(
            0, List.of(new PackagePricing.Linea(ORO, new BigDecimal("4150"), porcentaje("15"))));

    assertThat(cuenta.precioDe(ORO)).isEqualByComparingTo("3527").hasScaleOf(0);
    assertThat(cuenta.savings()).isEqualByComparingTo("623");
  }

  private static DiscountValue porcentaje(String valor) {
    return DiscountValue.leido(DiscountType.PORCENTAJE, new BigDecimal(valor));
  }

  private static DiscountValue fijo(String valor) {
    return DiscountValue.leido(DiscountType.FIJO, new BigDecimal(valor));
  }
}
