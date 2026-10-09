package com.factech.nexus.modules.academy.domain.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Las escrituras de las clases en vivo (`live_sessions` y las tres que cuelgan de ella, `ac.md`
 * §8.10 a §8.13). <b>SQL nativo</b>, como {@code lesson_progress}: no hay una entidad con
 * comportamiento que justifique JPA, y reemplazar una lista es borrar e insertar.
 */
public interface LiveSessionRepository {

  void insert(NewLiveSession clase);

  void update(
      UUID id,
      String title,
      String description,
      UUID courseId,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt);

  void cancel(UUID id, String reason, OffsetDateTime at);

  /** Borra la lista entera y escribe la nueva. */
  void replaceMemberships(UUID id, Collection<UUID> membershipIds);

  /** Borra la lista entera y escribe la nueva. */
  void replaceProducts(UUID id, Collection<UUID> productIds);

  void saveRegistration(UUID id, UUID userId, String registrantId, String joinUrl);

  /** La clase con su fila bloqueada, para corregir, cancelar, iniciar o registrar. */
  Optional<LiveSessionQueryRepository.LiveSessionRow> findForUpdate(UUID id);

  /** Lo que se inserta al programar. */
  record NewLiveSession(
      UUID id,
      UUID courseId,
      String title,
      String description,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      long zoomMeetingId,
      UUID createdBy) {}
}
