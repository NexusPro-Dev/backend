package com.factech.nexus.modules.academy.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.shared.error.FieldError;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** `CA-AC-340`: hasta cuándo se muestra una señal, y las comprobaciones de sus campos. */
class SignalTest {

  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
  private static final ZoneOffset MENOS_CINCO = ZoneOffset.ofHours(-5);

  private static OffsetDateTime bogota(int dia, int hora, int minuto) {
    return OffsetDateTime.of(2026, 10, dia, hora, minuto, 0, 0, MENOS_CINCO);
  }

  @Test
  @DisplayName("`CA-AC-340` — creada a las 11:00 con hora 10:00, hasta mañana a las 10:00")
  void horaYaPasada() {
    assertThat(Signal.visibleUntil(LocalTime.of(10, 0), bogota(14, 11, 0), BOGOTA))
        .isEqualTo(bogota(15, 10, 0));
  }

  @Test
  @DisplayName("`CA-AC-340` — creada a las 09:00 con hora 10:30, hasta hoy a las 10:30")
  void horaPorLlegar() {
    assertThat(Signal.visibleUntil(LocalTime.of(10, 30), bogota(14, 9, 0), BOGOTA))
        .isEqualTo(bogota(14, 10, 30));
  }

  @Test
  @DisplayName("`CA-AC-340` — creada justo a su hora, hasta mañana a esa hora")
  void justoASuHora() {
    assertThat(Signal.visibleUntil(LocalTime.of(10, 0), bogota(14, 10, 0), BOGOTA))
        .isEqualTo(bogota(15, 10, 0));
  }

  @Test
  @DisplayName("`CA-AC-340` — la hora es de Bogotá aunque el instante llegue en UTC")
  void horaDeBogotaDesdeUtc() {
    // 03:00 UTC del 15 son las 22:00 del 14 en Bogotá: las 23:00 aún no han llegado.
    OffsetDateTime enUtc = OffsetDateTime.of(2026, 10, 15, 3, 0, 0, 0, ZoneOffset.UTC);
    assertThat(Signal.visibleUntil(LocalTime.of(23, 0), enUtc, BOGOTA))
        .isEqualTo(bogota(14, 23, 0));
  }

  @Test
  @DisplayName("Las comprobaciones acumulan con el campo que se les da")
  void comprobaciones() {
    List<FieldError> errores = new ArrayList<>();
    assertThat(Signal.par(" eur/usd ", "p", errores)).isEqualTo("EUR/USD");
    assertThat(Signal.hora("9:05", "h", errores)).isEqualTo(LocalTime.of(9, 5));
    assertThat(Signal.tipo("12", "t", errores)).isEqualTo(12);
    assertThat(Signal.suscripcion("premium", "s", errores)).isEqualTo(SubscriptionType.PREMIUM);
    assertThat(errores).isEmpty();

    assertThat(Signal.par("EUR USD", "[0].currencyPair", errores)).isNull();
    assertThat(Signal.hora("24:00", "[0].signalTime", errores)).isNull();
    assertThat(Signal.tipo("-1", "[0].signalType", errores)).isNull();
    assertThat(Signal.tipo((Integer) null, "[1].signalType", errores)).isNull();
    assertThat(Signal.suscripcion("gold", "[0].subscriptionType", errores)).isNull();
    Signal.membresias(SubscriptionType.PERSONALIZED, 0, "[0].membershipIds", errores);
    Signal.membresias(SubscriptionType.FREE, 1, "[1].membershipIds", errores);
    Signal.membresias(null, 1, "[2].membershipIds", errores);
    assertThat(errores)
        .extracting(FieldError::field, FieldError::code)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("[0].currencyPair", "VAL-001"),
            org.assertj.core.groups.Tuple.tuple("[0].signalTime", "VAL-002"),
            org.assertj.core.groups.Tuple.tuple("[0].signalType", "VAL-003"),
            org.assertj.core.groups.Tuple.tuple("[1].signalType", "VAL-003"),
            org.assertj.core.groups.Tuple.tuple("[0].subscriptionType", "VAL-004"),
            org.assertj.core.groups.Tuple.tuple("[0].membershipIds", "VAL-005"),
            org.assertj.core.groups.Tuple.tuple("[1].membershipIds", "VAL-006"));
  }
}
