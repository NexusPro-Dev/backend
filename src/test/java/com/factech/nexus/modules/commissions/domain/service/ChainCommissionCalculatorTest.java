package com.factech.nexus.modules.commissions.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.modules.commissions.domain.models.AccrualOutcome;
import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import com.factech.nexus.modules.commissions.domain.models.RateSource;
import com.factech.nexus.modules.commissions.domain.repository.CommissionResolutionRepository.ResolvedRate;
import com.factech.nexus.modules.commissions.domain.service.ChainCommissionCalculator.Level;
import com.factech.nexus.modules.commissions.domain.service.ChainCommissionCalculator.Verdict;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Las tres reglas que deciden dinero, sin base de datos (`RF-CM-013` · `T-08`). */
class ChainCommissionCalculatorTest {

  private static final UUID A = UUID.randomUUID();
  private static final UUID B = UUID.randomUUID();
  private static final UUID C = UUID.randomUUID();

  @Test
  @DisplayName("CA-CM-160 — el porcentaje va sobre la base BRUTA y el fijo paga POR UNIDAD")
  void baseBrutaYFijoPorUnidad() {
    Verdict v =
        ChainCommissionCalculator.calcular(
            new BigDecimal("100.00"),
            3,
            List.of(nivel(A, 0, porcentaje("10.00")), nivel(B, 1, fijo("3.0000"))));

    assertThat(v.outcome()).isEqualTo(AccrualOutcome.DEVENGADA);
    assertThat(v.commissions().get(0).amount()).isEqualByComparingTo("30");
    assertThat(v.commissions().get(1).amount()).isEqualByComparingTo("9");
    assertThat(v.commissions().get(0).amount().scale()).isEqualTo(4);
  }

  @Test
  @DisplayName("CA-CM-158 — quien no tiene tasa no cobra y la cadena sigue")
  void sinTasaNoCorta() {
    Verdict v =
        ChainCommissionCalculator.calcular(
            new BigDecimal("100.00"),
            1,
            List.of(
                nivel(A, 0, porcentaje("10.00")),
                new Level(B, 1, Optional.empty()),
                nivel(C, 2, porcentaje("2.00"))));

    assertThat(v.commissions()).extracting(c -> c.level()).containsExactly(0, 2);
  }

  @Test
  @DisplayName("CA-CM-161 — si la cadena pasa del importe de la línea, se RECHAZA y no se recorta")
  void pasaDelCien() {
    Verdict v =
        ChainCommissionCalculator.calcular(
            new BigDecimal("100.00"),
            1,
            List.of(
                nivel(A, 0, porcentaje("60.00")),
                nivel(B, 1, porcentaje("30.00")),
                nivel(C, 2, porcentaje("20.00"))));

    assertThat(v.outcome()).isEqualTo(AccrualOutcome.RECHAZADA);
    assertThat(v.commissions()).isEmpty();
    assertThat(v.reason()).contains("110").contains("100");
  }

  @Test
  @DisplayName("exactamente el 100 % cabe")
  void elCienCabe() {
    Verdict v =
        ChainCommissionCalculator.calcular(
            new BigDecimal("100.00"),
            1,
            List.of(nivel(A, 0, porcentaje("60.00")), nivel(B, 1, porcentaje("40.00"))));

    assertThat(v.outcome()).isEqualTo(AccrualOutcome.DEVENGADA);
  }

  @Test
  @DisplayName("CA-CM-162 — sin tasa en toda la cadena, SIN_COMISION")
  void sinComision() {
    Verdict v =
        ChainCommissionCalculator.calcular(
            new BigDecimal("100.00"), 1, List.of(new Level(A, 0, Optional.empty())));

    assertThat(v.outcome()).isEqualTo(AccrualOutcome.SIN_COMISION);
  }

  @Test
  @DisplayName("una tasa del 0 % COBRA cero: tenía tasa, y eso es lo que se copia")
  void ceroEsTasa() {
    Verdict v =
        ChainCommissionCalculator.calcular(
            new BigDecimal("100.00"), 1, List.of(nivel(A, 0, porcentaje("0.00"))));

    assertThat(v.outcome()).isEqualTo(AccrualOutcome.DEVENGADA);
    assertThat(v.commissions().get(0).amount()).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("un producto gratuito paga su fijo sin tope (`RN-CM-020`)")
  void gratuitoSinTope() {
    Verdict v =
        ChainCommissionCalculator.calcular(
            new BigDecimal("0.00"), 2, List.of(nivel(A, 0, fijo("5.0000"))));

    assertThat(v.outcome()).isEqualTo(AccrualOutcome.DEVENGADA);
    assertThat(v.commissions().get(0).amount()).isEqualByComparingTo("10");
  }

  private static Level nivel(UUID persona, int nivel, ResolvedRate tasa) {
    return new Level(persona, nivel, Optional.of(tasa));
  }

  private static ResolvedRate porcentaje(String valor) {
    return new ResolvedRate(
        RateSource.ROL,
        UUID.randomUUID(),
        CommissionRateType.PORCENTAJE,
        new BigDecimal(valor),
        null,
        null);
  }

  private static ResolvedRate fijo(String valor) {
    return new ResolvedRate(
        RateSource.ROL,
        UUID.randomUUID(),
        CommissionRateType.FIJO,
        new BigDecimal(valor),
        null,
        null);
  }
}
