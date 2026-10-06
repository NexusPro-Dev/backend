package com.factech.nexus.modules.indicators.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.modules.indicators.application.IndicatorPeriod;
import com.factech.nexus.modules.movements.application.SalesFigures.Interval;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.time.BusinessCalendar;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** `RF-IN-001` · `T-04` — el periodo en días de Bogotá (`spec.md` §6.1, §11). */
class SalesPeriodResolverTest {

  /** El 6 de octubre a la 01:00 UTC, que en Bogotá es todavía el día 5. */
  private final SalesPeriodResolver periodos =
      new SalesPeriodResolver(
          new BusinessCalendar(
              ZoneId.of("America/Bogota"),
              Clock.fixed(Instant.parse("2026-10-06T01:00:00Z"), ZoneOffset.UTC)));

  private final List<FieldError> problemas = new ArrayList<>();

  @Test
  @DisplayName("CA-IN-050 — sin fechas, desde el principio hasta hoy de Bogotá")
  void porDefecto() {
    assertThat(periodos.resolve(null, null, problemas))
        .isEqualTo(new IndicatorPeriod(null, LocalDate.of(2026, 10, 5), "America/Bogota"));
    assertThat(problemas).isEmpty();
  }

  @Test
  @DisplayName("CA-IN-051 — solo «hasta»: desde el principio")
  void soloHasta() {
    assertThat(periodos.resolve(null, LocalDate.of(2026, 8, 31), problemas).from()).isNull();
  }

  @Test
  @DisplayName("solo «desde»: hasta hoy")
  void soloDesde() {
    assertThat(periodos.resolve(LocalDate.of(2026, 9, 15), null, problemas).to())
        .isEqualTo(LocalDate.of(2026, 10, 5));
  }

  @Test
  @DisplayName("VAL-002 — «desde» posterior a «hasta»; y un «desde» futuro sin «hasta» también")
  void invertido() {
    assertThat(periodos.resolve(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1), problemas))
        .isNull();
    assertThat(periodos.resolve(LocalDate.of(2026, 12, 1), null, problemas)).isNull();
    assertThat(problemas).extracting(FieldError::code).containsExactly("VAL-002", "VAL-002");
  }

  @Test
  @DisplayName("CA-IN-052 — sin tope: varios años son un periodo válido")
  void sinTope() {
    assertThat(periodos.resolve(LocalDate.of(2020, 1, 1), LocalDate.of(2026, 10, 5), problemas))
        .isNotNull();
    assertThat(problemas).isEmpty();
  }

  @Test
  @DisplayName("VAL-005 — un tramo desconocido; sin tramo, el de por defecto")
  void tramo() {
    assertThat(SalesPeriodResolver.granularity("week", null, problemas))
        .isEqualTo(com.factech.nexus.modules.movements.application.SalesFigures.Granularity.WEEK);
    assertThat(SalesPeriodResolver.granularity(null, null, problemas)).isNull();
    SalesPeriodResolver.granularity("HORA", null, problemas);
    assertThat(problemas).extracting(FieldError::code).containsExactly("VAL-005");
  }

  @Test
  @DisplayName("CA-IN-009 — el intervalo va de la medianoche de Bogotá a la del día siguiente")
  void intervalo() {
    Interval un =
        periodos.interval(
            new IndicatorPeriod(
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 30), "America/Bogota"));

    assertThat(un.from()).isEqualTo(OffsetDateTime.parse("2026-09-30T05:00:00Z"));
    assertThat(un.to()).isEqualTo(OffsetDateTime.parse("2026-10-01T05:00:00Z"));

    // Sin «desde», sin límite inferior.
    assertThat(
            periodos
                .interval(new IndicatorPeriod(null, LocalDate.of(2026, 9, 30), "America/Bogota"))
                .from())
        .isNull();
  }
}
