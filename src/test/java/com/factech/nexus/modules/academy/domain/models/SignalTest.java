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

/** `CA-AC-350`: hasta cuándo se muestra una señal, y las comprobaciones de sus campos. */
class SignalTest {

  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
  private static final ZoneOffset MENOS_CINCO = ZoneOffset.ofHours(-5);

  private static OffsetDateTime bogota(int dia, int hora, int minuto) {
    return OffsetDateTime.of(2026, 10, dia, hora, minuto, 0, 0, MENOS_CINCO);
  }

  @Test
  @DisplayName("`CA-AC-350` — creada a las 11:00, hasta la medianoche de su día")
  void hastaLaMedianoche() {
    assertThat(Signal.visibleUntil(bogota(14, 11, 0), BOGOTA)).isEqualTo(bogota(15, 0, 0));
    assertThat(Signal.visibleUntil(bogota(14, 0, 0), BOGOTA)).isEqualTo(bogota(15, 0, 0));
    assertThat(Signal.visibleUntil(bogota(14, 23, 59), BOGOTA)).isEqualTo(bogota(15, 0, 0));
  }

  @Test
  @DisplayName("`CA-AC-350` — la hora de la señal no cuenta, aunque ya haya pasado")
  void laHoraNoCuenta() {
    Signal pasada =
        Signal.create(
            java.util.UUID.randomUUID(),
            "EUR/USD",
            LocalTime.of(10, 0),
            1,
            SignalDirection.UP,
            SubscriptionType.FREE,
            null,
            bogota(14, 11, 0),
            BOGOTA);
    assertThat(pasada.getVisibleUntil()).isEqualTo(bogota(15, 0, 0));
  }

  @Test
  @DisplayName("`CA-AC-350` — el día es el de Bogotá aunque el instante llegue en UTC")
  void diaDeBogotaDesdeUtc() {
    // 03:00 UTC del 15 son las 22:00 del 14 en Bogotá: se ve hasta las 00:00 del 15 en Bogotá.
    OffsetDateTime enUtc = OffsetDateTime.of(2026, 10, 15, 3, 0, 0, 0, ZoneOffset.UTC);
    assertThat(Signal.visibleUntil(enUtc, BOGOTA)).isEqualTo(bogota(15, 0, 0));
  }

  @Test
  @DisplayName("`CA-AC-348` — la dirección acepta sus sinónimos y se guarda UP o DOWN")
  void direccion() {
    List<FieldError> errores = new ArrayList<>();
    for (String sube : List.of("UP", "up", " Sube ", "call", "BUY")) {
      assertThat(Signal.direccion(sube, "d", errores)).isEqualTo(SignalDirection.UP);
    }
    for (String baja : List.of("DOWN", "down", "BAJA", "Put", "sell")) {
      assertThat(Signal.direccion(baja, "d", errores)).isEqualTo(SignalDirection.DOWN);
    }
    assertThat(errores).isEmpty();

    assertThat(Signal.direccion(null, "[0].direction", errores)).isNull();
    assertThat(Signal.direccion("lateral", "[1].direction", errores)).isNull();
    assertThat(errores)
        .extracting(FieldError::field, FieldError::code)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("[0].direction", "VAL-011"),
            org.assertj.core.groups.Tuple.tuple("[1].direction", "VAL-011"));
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
