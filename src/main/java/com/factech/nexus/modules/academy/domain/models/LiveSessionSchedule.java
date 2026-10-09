package com.factech.nexus.modules.academy.domain.models;

import com.factech.nexus.shared.error.FieldError;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * El horario de una clase en vivo (`RN-AC-029`): **día, hora de inicio y hora de fin**, por
 * decisión del responsable del proyecto.
 *
 * <p>Una hora llega <b>con su zona</b> ({@code 2026-10-15T19:00:00-05:00}) o sin ella ({@code
 * 2026-10-15T19:00:00}), y entonces se entiende en la zona de la plataforma. El inicio no va en el
 * pasado, y el fin va de 15 minutos a 10 horas después. <b>Que la clase haya terminado se
 * calcula</b> con el fin; a Zoom se le da la duración en minutos que resulta.
 */
public record LiveSessionSchedule(OffsetDateTime startsAt, OffsetDateTime endsAt) {

  public static final Duration MINIMA = Duration.ofMinutes(15);
  public static final Duration MAXIMA = Duration.ofHours(10);

  /**
   * Interpreta una hora con o sin zona.
   *
   * @return la hora, o nula si viene vacía o no se entiende —y entonces anota el problema—
   */
  public static OffsetDateTime leer(
      String valor, String campo, ZoneId zona, List<FieldError> problemas) {
    if (valor == null || valor.isBlank()) {
      problemas.add(new FieldError(campo, "VAL-002", "La fecha y hora es obligatoria."));
      return null;
    }
    String limpio = valor.trim();
    try {
      return OffsetDateTime.parse(limpio);
    } catch (DateTimeParseException conZona) {
      try {
        return LocalDateTime.parse(limpio).atZone(zona).toOffsetDateTime();
      } catch (DateTimeParseException sinZona) {
        problemas.add(
            new FieldError(
                campo,
                "VAL-002",
                "La fecha y hora debe tener la forma 2026-10-15T19:00:00, con o sin zona."));
        return null;
      }
    }
  }

  /** Comprueba `RN-AC-029` y anota lo que falle. */
  public static void comprobar(
      OffsetDateTime inicio, OffsetDateTime fin, Instant ahora, List<FieldError> problemas) {
    if (inicio == null || fin == null) {
      return;
    }
    if (inicio.toInstant().isBefore(ahora)) {
      problemas.add(
          new FieldError("startsAt", "VAL-003", "La clase no se puede programar en el pasado."));
    }
    Duration lapso = Duration.between(inicio, fin);
    if (lapso.compareTo(MINIMA) < 0 || lapso.compareTo(MAXIMA) > 0) {
      problemas.add(
          new FieldError(
              "endsAt",
              "VAL-003",
              "La hora de fin debe ser de 15 minutos a 10 horas después de la de inicio."));
    }
  }

  /** La duración que se le da a Zoom, en minutos enteros hacia arriba. */
  public int minutos() {
    long segundos = Duration.between(startsAt, endsAt).getSeconds();
    return (int) ((segundos + 59) / 60);
  }

  /** Si ya terminó en ese instante. */
  public static boolean terminada(OffsetDateTime fin, Instant ahora) {
    return !ahora.isBefore(fin.toInstant());
  }

  /** Si está en curso en ese instante. */
  public static boolean enCurso(OffsetDateTime inicio, OffsetDateTime fin, Instant ahora) {
    return !ahora.isBefore(inicio.toInstant()) && ahora.isBefore(fin.toInstant());
  }
}
