package com.factech.nexus.modules.academy.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.modules.academy.domain.models.LiveSessionSeries.Occurrence;
import com.factech.nexus.modules.academy.domain.models.LiveSessionSeries.Recurrence;
import com.factech.nexus.shared.error.FieldError;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Las fechas de una clase que se repite (`RF-AC-055` · `T-02`, `RN-AC-031`). */
class LiveSessionSeriesTest {

  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
  // Jueves 15 de octubre de 2026, de 19:00 a 20:30 en Bogotá.
  private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-10-15T19:00:00-05:00");
  private static final OffsetDateTime FIN = OffsetDateTime.parse("2026-10-15T20:30:00-05:00");
  private static final Instant ANTES = Instant.parse("2026-10-01T00:00:00Z");

  private static List<FieldError> comprobar(
      OffsetDateTime inicio, Recurrence frecuencia, LocalDate hasta, Instant ahora) {
    List<FieldError> problemas = new ArrayList<>();
    LiveSessionSeries.comprobar(
        inicio, inicio.plusMinutes(90), frecuencia, hasta, false, BOGOTA, ahora, problemas);
    return problemas;
  }

  @Test
  @DisplayName("diaria, semanal el mismo día de la semana y mensual el mismo día del mes")
  void fechas() {
    List<Occurrence> diaria =
        new LiveSessionSeries(INICIO, FIN, Recurrence.DAILY, LocalDate.parse("2026-10-19"))
            .fechas(BOGOTA);
    assertThat(diaria).hasSize(5);
    assertThat(diaria.get(4).startsAt()).isEqualTo(OffsetDateTime.parse("2026-10-19T19:00-05:00"));
    assertThat(diaria.get(4).endsAt()).isEqualTo(OffsetDateTime.parse("2026-10-19T20:30-05:00"));

    // Hasta un miércoles: el último jueves anterior.
    List<Occurrence> semanal =
        new LiveSessionSeries(INICIO, FIN, Recurrence.WEEKLY, LocalDate.parse("2026-11-04"))
            .fechas(BOGOTA);
    assertThat(semanal)
        .extracting(f -> f.startsAt().toLocalDate())
        .containsExactly(
            LocalDate.parse("2026-10-15"),
            LocalDate.parse("2026-10-22"),
            LocalDate.parse("2026-10-29"));

    LiveSessionSeries mensual =
        new LiveSessionSeries(INICIO, FIN, Recurrence.MONTHLY, LocalDate.parse("2027-01-15"));
    assertThat(mensual.veces(BOGOTA)).isEqualTo(4);
    assertThat(mensual.finDeLaSerie(BOGOTA))
        .isEqualTo(OffsetDateTime.parse("2027-01-15T20:30-05:00"));
  }

  @Test
  @DisplayName("sin repetición, la serie es la primera fecha")
  void sola() {
    LiveSessionSeries sola = LiveSessionSeries.of(INICIO, FIN, null, null);
    assertThat(sola.fechas(BOGOTA)).containsExactly(new Occurrence(INICIO, FIN));
    assertThat(sola.finDeLaSerie(BOGOTA)).isEqualTo(FIN);
  }

  @Test
  @DisplayName("la siguiente es la que está en curso o la próxima; ninguna tras la última")
  void siguiente() {
    LiveSessionSeries serie =
        new LiveSessionSeries(INICIO, FIN, Recurrence.DAILY, LocalDate.parse("2026-10-17"));
    Instant enLaSegunda = Instant.parse("2026-10-17T00:30:00Z"); // 16-10 a las 19:30 en Bogotá
    assertThat(serie.siguiente(enLaSegunda, BOGOTA).orElseThrow().startsAt().toLocalDate())
        .isEqualTo(LocalDate.parse("2026-10-16"));
    assertThat(serie.enCurso(enLaSegunda, BOGOTA)).isTrue();

    Instant entreDos = Instant.parse("2026-10-17T03:00:00Z"); // 16-10 a las 22:00
    assertThat(serie.siguiente(entreDos, BOGOTA).orElseThrow().startsAt().toLocalDate())
        .isEqualTo(LocalDate.parse("2026-10-17"));
    assertThat(serie.enCurso(entreDos, BOGOTA)).isFalse();

    assertThat(serie.siguiente(Instant.parse("2026-10-18T03:00:00Z"), BOGOTA)).isEmpty();
  }

  @Test
  @DisplayName("de 2 a 60 fechas, la mensual no después del 28, la fecha final solo con repetición")
  void comprueba() {
    assertThat(comprobar(INICIO, Recurrence.DAILY, LocalDate.parse("2026-10-16"), ANTES)).isEmpty();
    assertThat(comprobar(INICIO, Recurrence.DAILY, LocalDate.parse("2026-10-15"), ANTES))
        .extracting(FieldError::code)
        .containsExactly("VAL-003");
    // Del 15-10 al 13-12 son 60 días; al 14-12, 61.
    assertThat(comprobar(INICIO, Recurrence.DAILY, LocalDate.parse("2026-12-13"), ANTES)).isEmpty();
    assertThat(comprobar(INICIO, Recurrence.DAILY, LocalDate.parse("2026-12-14"), ANTES))
        .extracting(FieldError::field)
        .containsExactly("repeatUntil");
    assertThat(
            comprobar(
                OffsetDateTime.parse("2026-10-29T19:00:00-05:00"),
                Recurrence.MONTHLY,
                LocalDate.parse("2027-01-31"),
                ANTES))
        .extracting(FieldError::field)
        .containsExactly("startsAt");
    assertThat(comprobar(INICIO, null, LocalDate.parse("2026-10-20"), ANTES))
        .extracting(FieldError::code)
        .containsExactly("VAL-003");
    assertThat(comprobar(INICIO, Recurrence.WEEKLY, null, ANTES))
        .extracting(FieldError::code)
        .containsExactly("VAL-002");
    assertThat(
            comprobar(
                INICIO,
                Recurrence.DAILY,
                LocalDate.parse("2026-10-16"),
                Instant.parse("2026-10-20T00:00:00Z")))
        .extracting(FieldError::message)
        .containsExactly("La última fecha de la serie ya terminó.");
  }

  @Test
  @DisplayName("lee la frecuencia y la fecha final; NONE solo al corregir")
  void lee() {
    List<FieldError> problemas = new ArrayList<>();
    assertThat(LiveSessionSeries.leerFrecuencia("weekly", false, problemas))
        .contains(Recurrence.WEEKLY);
    assertThat(LiveSessionSeries.leerFrecuencia("NONE", true, problemas)).isEmpty();
    assertThat(problemas).isEmpty();
    assertThat(LiveSessionSeries.leerFrecuencia("NONE", false, problemas)).isEmpty();
    assertThat(LiveSessionSeries.leerFrecuencia("YEARLY", true, problemas)).isEmpty();
    assertThat(LiveSessionSeries.leerHasta("31-12-2026", problemas)).isNull();
    assertThat(problemas)
        .extracting(FieldError::field)
        .containsExactly("recurrence", "recurrence", "repeatUntil");
  }
}
