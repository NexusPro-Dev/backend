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
      UUID courseId,
      @Schema(
              description =
                  "Día y hora de inicio: 2026-10-15T19:00:00-05:00, o sin zona y entonces en"
                      + " America/Bogota.")
          String startsAt,
      @Schema(description = "Día y hora de fin, de 15 minutos a 10 horas después del inicio.")
          String endsAt,
      List<UUID> membershipIds,
      List<UUID> productIds) {}

  /**
   * Corregir (`RF-AC-045`, `RF-AC-050`): solo lo que viene. <b>Las listas, si vienen, reemplazan
   * enteras</b>; {@code removeCourse} deja la clase suelta.
   */
  @Schema(name = "UpdateLiveSessionRequest")
  public record Update(
      String title,
      String description,
      UUID courseId,
      @Schema(description = "Verdadero para dejar la clase suelta, sin curso.")
          Boolean removeCourse,
      String startsAt,
      String endsAt,
      List<UUID> membershipIds,
      List<UUID> productIds) {}

  /** Cancelar (`RF-AC-046`, `RF-AC-051`). */
  @Schema(name = "CancelLiveSessionRequest")
  public record Cancel(String reason) {}

  /** Los filtros del listado (`RF-AC-042`, `RF-AC-048`). */
  public record ListFilter(
      Integer page,
      Integer size,
      UUID courseId,
      String status,
      Boolean ended,
      OffsetDateTime from,
      OffsetDateTime to) {}

  /** Los filtros de la vitrina del alumno (`RF-AC-053`). */
  public record AvailableFilter(UUID courseId, Boolean onlyAccessible) {

    public boolean soloAccesibles() {
      return Boolean.TRUE.equals(onlyAccessible);
    }
  }
}
