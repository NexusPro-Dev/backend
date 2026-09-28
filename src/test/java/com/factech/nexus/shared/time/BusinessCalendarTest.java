package com.factech.nexus.shared.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El día del negocio no es el de UTC (`architecture.md` v0.39.0 §15.1.1). */
class BusinessCalendarTest {

  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

  @Test
  @DisplayName("a la 01:00 en UTC del 11, hoy es el 10 en Bogotá")
  void hoyEnBogota() {
    BusinessCalendar calendario =
        new BusinessCalendar(
            BOGOTA, Clock.fixed(Instant.parse("2026-09-11T01:00:00Z"), ZoneOffset.UTC));

    assertThat(calendario.hoy()).isEqualTo(LocalDate.of(2026, 9, 10));
  }

  @Test
  @DisplayName("un instante de las 20:00 del 10 en Bogotá es del día 10")
  void diaDeUnInstante() {
    BusinessCalendar calendario = new BusinessCalendar(BOGOTA, Clock.systemUTC());

    assertThat(calendario.diaDe(OffsetDateTime.parse("2026-09-11T01:00:00Z")))
        .isEqualTo(LocalDate.of(2026, 9, 10));
    assertThat(calendario.diaDe(OffsetDateTime.parse("2026-09-11T05:00:00Z")))
        .isEqualTo(LocalDate.of(2026, 9, 11));
  }
}
