package com.factech.nexus.modules.commissions.interfaces;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.shared.time.BusinessCalendar;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

/** Cuándo corre el cierre programado, y qué turno nombra (`CA-CM-179`). */
class CommissionClosingJobTest {

  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

  private final CommissionClosingJob job =
      new CommissionClosingJob(
          null, new BusinessCalendar(BOGOTA, Clock.systemUTC()), "0 0 0 1 * *");

  @Test
  @DisplayName(
      "CA-CM-179 — dos instancias que disparan a milisegundos distintos nombran el MISMO turno: la"
          + " hora nominal, no la real")
  void turnoNominal() {
    OffsetDateTime una =
        job.turnoNominal(ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 3_000_000, BOGOTA));
    OffsetDateTime otra =
        job.turnoNominal(ZonedDateTime.of(2026, 10, 1, 0, 0, 1, 250_000_000, BOGOTA));

    assertThat(una).isEqualTo(otra);
    assertThat(una.toInstant()).isEqualTo(OffsetDateTime.parse("2026-10-01T05:00:00Z").toInstant());
  }

  @Test
  @DisplayName("CA-CM-179 — corre en la zona del negocio, con la expresión de configuración")
  void enLaZonaDelNegocio() throws Exception {
    Scheduled programa =
        CommissionClosingJob.class.getMethod("ejecutar").getAnnotation(Scheduled.class);

    assertThat(programa.zone()).isEqualTo("${nexus.business.zone}");
    assertThat(programa.cron()).isEqualTo("${nexus.commissions.closing.cron}");
  }
}
