package com.factech.nexus.modules.academy.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.shared.error.FieldError;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** `RN-AC-029`: inicio y fin, la zona por omisión y los límites (`RF-AC-044` · `T-03`). */
class LiveSessionScheduleTest {

  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
  private static final Instant AHORA = Instant.parse("2026-10-09T12:00:00Z");

  @Test
  @DisplayName("una hora sin zona se entiende en America/Bogota; con zona, se respeta")
  void zona() {
    List<FieldError> problemas = new ArrayList<>();
    assertThat(LiveSessionSchedule.leer("2026-10-15T19:00:00", "startsAt", BOGOTA, problemas))
        .isEqualTo(OffsetDateTime.parse("2026-10-15T19:00:00-05:00"));
    assertThat(LiveSessionSchedule.leer("2026-10-15T19:00:00Z", "startsAt", BOGOTA, problemas))
        .isEqualTo(OffsetDateTime.parse("2026-10-15T19:00:00Z"));
    assertThat(problemas).isEmpty();
    assertThat(LiveSessionSchedule.leer("mañana", "startsAt", BOGOTA, problemas)).isNull();
    assertThat(LiveSessionSchedule.leer(null, "endsAt", BOGOTA, problemas)).isNull();
    assertThat(problemas).extracting(FieldError::field).containsExactly("startsAt", "endsAt");
  }

  @Test
  @DisplayName("inicio en el pasado, menos de 15 minutos o más de 10 horas: un problema cada uno")
  void limites() {
    OffsetDateTime inicio = OffsetDateTime.parse("2026-10-15T19:00:00-05:00");
    List<FieldError> bien = new ArrayList<>();
    LiveSessionSchedule.comprobar(inicio, inicio.plusMinutes(15), AHORA, bien);
    LiveSessionSchedule.comprobar(inicio, inicio.plusHours(10), AHORA, bien);
    assertThat(bien).isEmpty();

    List<FieldError> mal = new ArrayList<>();
    LiveSessionSchedule.comprobar(inicio, inicio.plusMinutes(14), AHORA, mal);
    LiveSessionSchedule.comprobar(inicio, inicio.plusHours(10).plusMinutes(1), AHORA, mal);
    LiveSessionSchedule.comprobar(inicio, inicio.minusMinutes(30), AHORA, mal);
    LiveSessionSchedule.comprobar(
        OffsetDateTime.parse("2026-10-01T10:00:00-05:00"),
        OffsetDateTime.parse("2026-10-01T11:00:00-05:00"),
        AHORA,
        mal);
    assertThat(mal)
        .extracting(FieldError::field)
        .containsExactly("endsAt", "endsAt", "endsAt", "startsAt");
  }

  @Test
  @DisplayName("la duración para Zoom en minutos hacia arriba; terminada y en curso por la hora")
  void duracionYEstado() {
    OffsetDateTime inicio = OffsetDateTime.parse("2026-10-09T07:00:00-05:00");
    assertThat(new LiveSessionSchedule(inicio, inicio.plusMinutes(90)).minutos()).isEqualTo(90);
    assertThat(new LiveSessionSchedule(inicio, inicio.plusSeconds(61 * 60 + 1)).minutos())
        .isEqualTo(62);
    assertThat(LiveSessionSchedule.enCurso(inicio, inicio.plusHours(1), AHORA)).isTrue();
    // Termina justo cuando su fin llega, ni un instante después.
    assertThat(LiveSessionSchedule.terminada(inicio, AHORA)).isTrue();
    assertThat(LiveSessionSchedule.terminada(inicio.plusHours(1), AHORA)).isFalse();
  }
}
