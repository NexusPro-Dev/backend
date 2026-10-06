package com.factech.nexus.modules.indicators.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.modules.movements.application.SalesFigures.Granularity;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** `RF-IN-002` · `T-02` — los inicios de tramo (`spec.md` §2.2 y §13). */
class SalesCalendarTest {

  private static LocalDate d(String iso) {
    return LocalDate.parse(iso);
  }

  @Test
  @DisplayName("por días, uno por día del periodo, los dos extremos incluidos")
  void dias() {
    assertThat(SalesCalendar.starts(d("2026-09-08"), d("2026-09-10"), Granularity.DAY))
        .containsExactly(d("2026-09-08"), d("2026-09-09"), d("2026-09-10"));
    assertThat(SalesCalendar.starts(d("2026-09-08"), d("2026-09-08"), Granularity.DAY))
        .containsExactly(d("2026-09-08"));
  }

  @Test
  @DisplayName("por semanas, de lunes: un periodo que empieza en domingo arranca el lunes anterior")
  void semanas() {
    // El 13 de septiembre de 2026 es domingo.
    assertThat(SalesCalendar.starts(d("2026-09-13"), d("2026-09-23"), Granularity.WEEK))
        .containsExactly(d("2026-09-07"), d("2026-09-14"), d("2026-09-21"));
  }

  @Test
  @DisplayName("por meses: del 31 de enero al 1 de marzo son tres tramos, febrero bisiesto o no")
  void meses() {
    assertThat(SalesCalendar.starts(d("2028-01-31"), d("2028-03-01"), Granularity.MONTH))
        .containsExactly(d("2028-01-01"), d("2028-02-01"), d("2028-03-01"));
    assertThat(SalesCalendar.starts(d("2026-01-31"), d("2026-03-01"), Granularity.MONTH))
        .containsExactly(d("2026-01-01"), d("2026-02-01"), d("2026-03-01"));
  }

  @Test
  @DisplayName("366 días por días son 366 tramos")
  void tope() {
    assertThat(SalesCalendar.starts(d("2028-01-01"), d("2028-12-31"), Granularity.DAY))
        .hasSize(366);
  }
}
