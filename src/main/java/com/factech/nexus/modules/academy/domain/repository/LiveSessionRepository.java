package com.factech.nexus.modules.academy.domain.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Las escrituras de las clases en vivo (`live_sessions` y las cuatro que cuelgan de ella, `ac.md`
 * §8.10 a §8.14). <b>SQL nativo</b>, como {@code lesson_progress}: no hay una entidad con
 * comportamiento que justifique JPA, y reemplazar una lista es borrar e insertar.
 */
public interface LiveSessionRepository {

  void insert(NewLiveSession clase);

  void update(
      UUID id,
      String title,
      String description,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      String recurrence,
      LocalDate repeatUntil,
      OffsetDateTime seriesEndsAt);

  void cancel(UUID id, String reason, OffsetDateTime at);

  /** Borra la lista entera y escribe la nueva. */
  void replaceMemberships(UUID id, Collection<UUID> membershipIds);

  /** Borra la lista entera y escribe la nueva. */
  void replaceProducts(UUID id, Collection<UUID> productIds);

  /** Borra la lista de instructores entera y escribe la nueva (`RN-AC-032`). */
  void replaceInstructors(UUID id, Collection<UUID> instructorIds);

  /** Apunta la portada, o la quita con nulo (`RN-AC-032`). */
  void setCover(UUID id, UUID coverImageId);

  void saveRegistration(UUID id, UUID userId, String registrantId, String joinUrl);

  /** La clase con su fila bloqueada, para corregir, cancelar, iniciar o registrar. */
  Optional<LiveSessionQueryRepository.LiveSessionRow> findForUpdate(UUID id);

  /**
   * Lo que se inserta al programar. {@code recurrence} y {@code repeatUntil} nulos, una sola fecha;
   * {@code seriesEndsAt}, el fin de la última (`RN-AC-031`).
   */
  record NewLiveSession(
      UUID id,
      String title,
      String description,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      String recurrence,
      LocalDate repeatUntil,
      OffsetDateTime seriesEndsAt,
      long zoomMeetingId,
      UUID createdBy) {}
}
