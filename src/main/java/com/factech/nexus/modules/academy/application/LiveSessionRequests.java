package com.factech.nexus.modules.academy.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Las peticiones de las clases en vivo (`RF-AC-042` a `RF-AC-054`), juntas: son seis registros
 * pequeños que solo se entienden uno al lado del otro.
 */
public final class LiveSessionRequests {

  private LiveSessionRequests() {}

  /** Programar (`RF-AC-044`, `RF-AC-049`). Las horas, con o sin zona (`RN-AC-029`). */
  @Schema(name = "ScheduleLiveSessionRequest")
  public record Schedule(
      String title,
      String description,
      @Schema(description = "La categoría de la clase, obligatoria y viva (RN-AC-034).")
          UUID categoryId,
      @Schema(
              description =
                  "Los instructores de la clase: personas con courses:teach, sin repetir. Por"
                      + " /mine, quien programa entra siempre (RN-AC-032).")
          List<UUID> instructorIds,
      @Schema(
              description =
                  "Día y hora de inicio: 2026-10-15T19:00:00-05:00, o sin zona y entonces en"
                      + " America/Bogota.")
          String startsAt,
      @Schema(description = "Día y hora de fin, de 15 minutos a 10 horas después del inicio.")
          String endsAt,
      @Schema(
              description =
                  "DAILY, WEEKLY (el día de la semana del inicio) o MONTHLY (el día del mes del"
                      + " inicio, no después del 28); ausente, una sola fecha (RN-AC-031).")
          String recurrence,
      @Schema(
              description =
                  "El último día de la serie, incluido, como 2026-12-31: obligatorio con"
                      + " recurrence; de 2 a 60 fechas.")
          String repeatUntil,
      List<UUID> membershipIds,
      List<UUID> productIds) {}

  /**
   * Corregir (`RF-AC-045`, `RF-AC-050`): solo lo que viene. <b>Las listas, si vienen, reemplazan
   * enteras</b>, también la de instructores (`RN-AC-032`).
   */
  @Schema(name = "UpdateLiveSessionRequest")
  public record Update(
      String title,
      String description,
      @Schema(description = "Si viene, cambia la categoría por otra viva (RN-AC-034).")
          UUID categoryId,
      @Schema(
              description =
                  "Si viene, reemplaza la lista de instructores; por /mine, tiene que incluir a"
                      + " quien corrige.")
          List<UUID> instructorIds,
      String startsAt,
      String endsAt,
      @Schema(description = "DAILY, WEEKLY, MONTHLY, o NONE para quitar la repetición.")
          String recurrence,
      @Schema(description = "El último día de la serie, incluido, como 2026-12-31.")
          String repeatUntil,
      List<UUID> membershipIds,
      List<UUID> productIds) {}

  /** Cancelar (`RF-AC-046`, `RF-AC-051`). */
  @Schema(name = "CancelLiveSessionRequest")
  public record Cancel(String reason) {}

  /** Los filtros del listado (`RF-AC-042`, `RF-AC-048`). */
  public record ListFilter(
      Integer page,
      Integer size,
      @Schema(description = "Solo las clases de las que esa persona es instructor.")
          UUID instructorId,
      @Schema(description = "Solo las clases de esa categoría (RN-AC-034).") UUID categoryId,
      String status,
      Boolean ended,
      OffsetDateTime from,
      OffsetDateTime to) {}

  /** Los filtros de la vitrina del alumno (`RF-AC-053`). */
  public record AvailableFilter(
      Boolean onlyAccessible,
      @Schema(description = "Solo las clases de esa categoría (RN-AC-034).") UUID categoryId) {

    public boolean soloAccesibles() {
      return Boolean.TRUE.equals(onlyAccessible);
    }
  }
}
