package com.factech.nexus.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Los importes en centésimas (ADR-006, `RF-MV-001` · `T-40`).
 *
 * <p>Sin Spring: lo que se vigila es el factor y lo que pasa en los bordes. Un factor equivocado no
 * falla nunca por forma —devuelve un importe cien veces mayor con un {@code 200}—, de modo que esta
 * clase es la primera de las dos redes; la otra son las pruebas de integración de cada módulo.
 */
class MinorUnitsTest {

  private final MinorUnitsConverter convertidor = new MinorUnitsConverter();

  @Test
  @DisplayName("`12.50` se guarda `1250` y vuelve a ser `12.50`")
  void idaYVuelta() {
    assertThat(convertidor.convertToDatabaseColumn(new BigDecimal("12.50"))).isEqualTo(1250L);
    assertThat(convertidor.convertToEntityAttribute(1250L)).isEqualByComparingTo("12.50");
    assertThat(convertidor.convertToEntityAttribute(1250L).scale()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "el cero y la escala que llegue no cambian el valor: `49`, `49.0` y `49.0000` son 4900")
  void laEscalaNoEsInformacion() {
    assertThat(MinorUnits.toMinor(BigDecimal.ZERO)).isZero();
    assertThat(MinorUnits.toMinor(new BigDecimal("49"))).isEqualTo(4900L);
    assertThat(MinorUnits.toMinor(new BigDecimal("49.0"))).isEqualTo(4900L);
    assertThat(MinorUnits.toMinor(new BigDecimal("49.0000"))).isEqualTo(4900L);
  }

  @Test
  @DisplayName("un asiento de débito es negativo, y va y vuelve igual")
  void negativos() {
    assertThat(MinorUnits.toMinor(new BigDecimal("-12.50"))).isEqualTo(-1250L);
    assertThat(MinorUnits.fromMinor(-1250L)).isEqualByComparingTo("-12.50");
  }

  @Test
  @DisplayName("nulo pasa a nulo, en los dos sentidos")
  void nulo() {
    assertThat(convertidor.convertToDatabaseColumn(null)).isNull();
    assertThat(convertidor.convertToEntityAttribute(null)).isNull();
  }

  @Test
  @DisplayName("la red: un tercer decimal se redondea con HALF_UP, como `round` al migrar")
  void laRed() {
    assertThat(MinorUnits.toMinor(new BigDecimal("10.005"))).isEqualTo(1001L);
    assertThat(MinorUnits.toMinor(new BigDecimal("10.004"))).isEqualTo(1000L);
    assertThat(MinorUnits.toMinor(new BigDecimal("-10.005"))).isEqualTo(-1001L);
  }

  @Test
  @DisplayName("un importe que no cabe en un `long` falla en lugar de truncarse")
  void noCabe() {
    BigDecimal enorme = new BigDecimal(Long.MAX_VALUE).add(BigDecimal.ONE);

    assertThatThrownBy(() -> MinorUnits.toMinor(enorme)).isInstanceOf(ArithmeticException.class);
  }

  @Test
  @DisplayName("el SQL nativo puede devolver `Integer`, `BigInteger` o el `numeric` de un `SUM`")
  void loQueDevuelveElSqlNativo() {
    assertThat(MinorUnits.fromMinor(1250)).isEqualByComparingTo("12.50");
    assertThat(MinorUnits.fromMinor(BigInteger.valueOf(1250))).isEqualByComparingTo("12.50");
    assertThat(MinorUnits.fromMinor(new BigDecimal("1250"))).isEqualByComparingTo("12.50");
  }

  @Test
  @DisplayName("unas centésimas con fracción delatan una división previa, y se rechazan")
  void centesimasConFraccion() {
    assertThatThrownBy(() -> MinorUnits.fromMinor(new BigDecimal("12.50")))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> MinorUnits.fromMinor("1250"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
