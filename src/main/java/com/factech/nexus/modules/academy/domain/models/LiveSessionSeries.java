package com.factech.nexus.modules.academy.domain.models;

import com.factech.nexus.shared.error.FieldError;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Las fechas de una clase en vivo (`RN-AC-031`): la primera —{@code startsAt} y {@code endsAt}— y,
 * si se repite, las demás a la misma hora <b>cada día</b>, <b>cada semana el mismo día de la
 * semana</b> o <b>cada mes el mismo día del mes</b>, hasta {@code repeatUntil} incluido, en la zona
 * de la plataforma.
 *
 * <p><b>Un solo sitio para las fechas</b>: programar y corregir las comprueban, y las lecturas
 * preguntan aquí cuál es la siguiente, si alguna está en curso y cuándo termina la última. Sin
 * repetición, la serie es la primera fecha y nada más.
 */
public record LiveSessionSeries(
    OffsetDateTime startsAt, OffsetDateTime endsAt, Recurrence recurrence, LocalDate repeatUntil) {

  /** El máximo de fechas de una reunión recurrente de Zoom. */
  public static final int MAXIMO = 60;

  /** El último día del mes que tienen todos los meses. */
  public static final int DIA_MENSUAL_MAXIMO = 28;

  /** Cada cuánto se repite. */
  public enum Recurrence {
    DAILY,
    WEEKLY,
    MONTHLY
  }

  /** Una fecha de la serie. */
  public record Occurrence(OffsetDateTime startsAt, OffsetDateTime endsAt) {}

  /** La serie de una fila, con la frecuencia como texto. */
  public static LiveSessionSeries of(
      OffsetDateTime startsAt, OffsetDateTime endsAt, String recurrence, LocalDate repeatUntil) {
    return new LiveSessionSeries(
        startsAt,
        endsAt,
        recurrence == null ? null : Recurrence.valueOf(recurrence),
        recurrence == null ? null : repeatUntil);
  }

  /**
   * Interpreta {@code recurrence}.
   *
   * @param admiteNinguna si {@code NONE} vale —al corregir, quita la repetición—
   * @return la frecuencia; vacía si no viene, si es {@code NONE} o si no se entiende —y entonces
   *     anota el problema—
   */
  public static Optional<Recurrence> leerFrecuencia(
      String valor, boolean admiteNinguna, List<FieldError> problemas) {
    if (valor == null) {
      return Optional.empty();
    }
    String limpio = valor.trim().toUpperCase(java.util.Locale.ROOT);
    if (admiteNinguna && limpio.equals("NONE")) {
      return Optional.empty();
    }
    try {
      return Optional.of(Recurrence.valueOf(limpio));
    } catch (IllegalArgumentException desconocida) {
      problemas.add(
          new FieldError(
              "recurrence",
              "VAL-002",
              admiteNinguna
                  ? "La repetición debe ser DAILY, WEEKLY, MONTHLY o NONE."
                  : "La repetición debe ser DAILY, WEEKLY o MONTHLY."));
      return Optional.empty();
    }
  }

  /**
   * Interpreta {@code repeatUntil}, un día sin hora.
   *
   * @return el día, o nulo si no viene o no se entiende —y entonces anota el problema—
   */
  public static LocalDate leerHasta(String valor, List<FieldError> problemas) {
    if (valor == null || valor.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(valor.trim());
    } catch (DateTimeParseException fueraDeForma) {
      problemas.add(
          new FieldError(
              "repeatUntil", "VAL-002", "La fecha final debe tener la forma 2026-12-31."));
      return null;
    }
  }

  /**
   * Comprueba `RN-AC-031` y anota lo que falle: {@code repeatUntil} con repetición y solo con ella,
   * de 2 a 60 fechas, la mensual no después del día 28 y la última fecha sin terminar. Inicio y fin
   * los comprueba {@link LiveSessionSchedule}.
   */
  public static void comprobar(
      OffsetDateTime inicio,
      OffsetDateTime fin,
      Recurrence frecuencia,
      LocalDate hasta,
      boolean hastaIlegible,
      ZoneId zona,
      Instant ahora,
      List<FieldError> problemas) {
    if (frecuencia == null) {
      if (hasta != null) {
        problemas.add(
            new FieldError("repeatUntil", "VAL-003", "La fecha final solo va con una repetición."));
      }
      return;
    }
    if (hasta == null) {
      if (!hastaIlegible) {
        problemas.add(
            new FieldError(
                "repeatUntil",
                "VAL-002",
                "La fecha final es obligatoria cuando la clase se repite."));
      }
      return;
    }
    if (inicio == null || fin == null) {
      return;
    }
    if (frecuencia == Recurrence.MONTHLY
        && inicio.atZoneSameInstant(zona).getDayOfMonth() > DIA_MENSUAL_MAXIMO) {
      problemas.add(
          new FieldError(
              "startsAt",
              "VAL-003",
              "Una clase mensual no puede empezar después del día 28: no todos los meses lo"
                  + " tienen."));
      return;
    }
    LiveSessionSeries serie = new LiveSessionSeries(inicio, fin, frecuencia, hasta);
    List<Occurrence> fechas = serie.fechas(zona, MAXIMO + 1);
    if (fechas.size() < 2) {
      problemas.add(
          new FieldError(
              "repeatUntil",
              "VAL-003",
              "La repetición debe dar al menos dos fechas: la final va después de la primera."));
    } else if (fechas.size() > MAXIMO) {
      problemas.add(
          new FieldError(
              "repeatUntil",
              "VAL-003",
              "La repetición da más de 60 fechas, el máximo de Zoom: adelante la fecha final."));
    } else if (!ahora.isBefore(fechas.get(fechas.size() - 1).endsAt().toInstant())) {
      problemas.add(
          new FieldError("repeatUntil", "VAL-003", "La última fecha de la serie ya terminó."));
    }
  }

  /** Todas las fechas, de la primera a la última. */
  public List<Occurrence> fechas(ZoneId zona) {
    return fechas(zona, MAXIMO);
  }

  /** Cuántas fechas tiene. */
  public int veces(ZoneId zona) {
    return fechas(zona).size();
  }

  /** El fin de la última fecha. */
  public OffsetDateTime finDeLaSerie(ZoneId zona) {
    List<Occurrence> fechas = fechas(zona);
    return fechas.get(fechas.size() - 1).endsAt();
  }

  /** La fecha en curso o la siguiente; vacía si la última ya terminó. */
  public Optional<Occurrence> siguiente(Instant ahora, ZoneId zona) {
    return fechas(zona).stream().filter(f -> ahora.isBefore(f.endsAt().toInstant())).findFirst();
  }

  /** Si alguna fecha está en curso. */
  public boolean enCurso(Instant ahora, ZoneId zona) {
    return siguiente(ahora, zona)
        .filter(f -> !ahora.isBefore(f.startsAt().toInstant()))
        .isPresent();
  }

  /** Las fechas, sin pasar de {@code tope}: lo justo para saber si sobran. */
  private List<Occurrence> fechas(ZoneId zona, int tope) {
    Duration lapso = Duration.between(startsAt, endsAt);
    if (recurrence == null || repeatUntil == null) {
      return List.of(new Occurrence(startsAt, endsAt));
    }
    ZonedDateTime primera = startsAt.atZoneSameInstant(zona);
    List<Occurrence> fechas = new ArrayList<>();
    for (int k = 0; fechas.size() < tope; k++) {
      ZonedDateTime inicio =
          switch (recurrence) {
            case DAILY -> primera.plusDays(k);
            case WEEKLY -> primera.plusWeeks(k);
            case MONTHLY -> primera.plusMonths(k);
          };
      if (inicio.toLocalDate().isAfter(repeatUntil)) {
        break;
      }
      fechas.add(new Occurrence(inicio.toOffsetDateTime(), inicio.plus(lapso).toOffsetDateTime()));
    }
    return fechas;
  }
}
