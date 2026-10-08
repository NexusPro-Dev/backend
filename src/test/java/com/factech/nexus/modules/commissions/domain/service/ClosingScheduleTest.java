package com.factech.nexus.modules.commissions.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El próximo cierre y su ventana, con el cierre mensual en Bogotá (`RF-CM-028`, `CA-CM-383`). */
class ClosingScheduleTest {

  private final ClosingSchedule horario =
      new ClosingSchedule(true, "0 0 0 1 * *", ZoneId.of("America/Bogota"));

  @Test
  @DisplayName(
      "CA-CM-383 — el próximo es las 00:00 del día 1 en Bogotá, y la ventana se abre a las 00:00"
          + " del penúltimo día de un mes de 31")
  void mesDeTreintaYUno() {
    OffsetDateTime turno = horario.next(OffsetDateTime.parse("2026-10-08T12:00:00Z"));

    assertThat(turno.toInstant())
        .isEqualTo(OffsetDateTime.parse("2026-11-01T05:00:00Z").toInstant());
    assertThat(horario.windowOpensAt(turno).toInstant())
        .isEqualTo(OffsetDateTime.parse("2026-10-30T05:00:00Z").toInstant());
  }

  @Test
  @DisplayName("CA-CM-383 — en un mes de 30, la ventana se abre a las 00:00 del día 29")
  void mesDeTreinta() {
    OffsetDateTime turno = horario.next(OffsetDateTime.parse("2026-11-15T12:00:00Z"));

    assertThat(turno.toInstant())
        .isEqualTo(OffsetDateTime.parse("2026-12-01T05:00:00Z").toInstant());
    assertThat(horario.windowOpensAt(turno).toInstant())
        .isEqualTo(OffsetDateTime.parse("2026-11-29T05:00:00Z").toInstant());
  }

  @Test
  @DisplayName("FA-002 — a la hora exacta de un turno, el próximo es el siguiente")
  void alaHoraExacta() {
    OffsetDateTime turno = horario.next(OffsetDateTime.parse("2026-11-01T05:00:00Z"));

    assertThat(turno.toInstant())
        .isEqualTo(OffsetDateTime.parse("2026-12-01T05:00:00Z").toInstant());
  }

  @Test
  @DisplayName("apagado lo dice; encendido también")
  void encendido() {
    assertThat(horario.enabled()).isTrue();
    assertThat(new ClosingSchedule(false, "0 0 0 1 * *", ZoneId.of("UTC")).enabled()).isFalse();
  }
}
