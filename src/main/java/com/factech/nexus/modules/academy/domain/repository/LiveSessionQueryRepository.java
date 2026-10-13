package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.LiveSessionSeries;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.MembershipRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ProductRef;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Las lecturas de las clases en vivo (`RF-AC-042`, `RF-AC-043`, `RF-AC-048`, `RF-AC-053`). */
public interface LiveSessionQueryRepository {

  Optional<LiveSessionRow> find(UUID id);

  List<MembershipRef> findMembershipsOf(UUID id);

  List<ProductRef> findProductsOf(UUID id);

  List<RegistrationRow> findRegistrationsOf(UUID id);

  Optional<RegistrationRow> findRegistration(UUID id, UUID userId);

  /** Los instructores de cada clase, en el orden en que se asignaron (`RN-AC-032`). */
  Map<UUID, List<UUID>> findInstructorsOf(Collection<UUID> ids);

  List<LiveSessionItemRow> search(LiveSessionFilter filtro, int offset, int size);

  long count(LiveSessionFilter filtro);

  /** Las programadas cuya última fecha no ha terminado en ese instante, con sus llaves. */
  List<UpcomingRow> findUpcoming(Instant ahora);

  /** De esas clases, en cuáles está registrada esa persona. */
  Set<UUID> registeredAmong(UUID userId, List<UUID> ids);

  /** Una clase con sus instructores, que deciden la propiedad (`RN-AC-028`, `RN-AC-032`). */
  record LiveSessionRow(
      UUID id,
      String title,
      String description,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      String recurrence,
      LocalDate repeatUntil,
      OffsetDateTime seriesEndsAt,
      UUID coverImageId,
      long zoomMeetingId,
      String status,
      OffsetDateTime cancelledAt,
      String cancellationReason,
      UUID createdBy,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      List<UUID> instructorIds) {

    public boolean programada() {
      return "PROGRAMADA".equals(status);
    }

    /** Terminó su última fecha (`RN-AC-031`). */
    public boolean terminada(Instant ahora) {
      return !ahora.isBefore(seriesEndsAt.toInstant());
    }

    public LiveSessionSeries serie() {
      return LiveSessionSeries.of(startsAt, endsAt, recurrence, repeatUntil);
    }

    /** Si la gobierna esa persona con los permisos propios: está en su lista de instructores. */
    public boolean deInstructor(UUID actor) {
      return actor != null && instructorIds.contains(actor);
    }
  }

  record RegistrationRow(
      UUID userId, String registrantId, String joinUrl, OffsetDateTime registeredAt) {}

  /** Una fila del listado de administración y del instructor. */
  record LiveSessionItemRow(
      UUID id,
      String title,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      String recurrence,
      LocalDate repeatUntil,
      OffsetDateTime seriesEndsAt,
      UUID coverImageId,
      String status,
      long membershipCount,
      long productCount,
      long registrationCount) {

    public LiveSessionSeries serie() {
      return LiveSessionSeries.of(startsAt, endsAt, recurrence, repeatUntil);
    }
  }

  /** Una clase de la vitrina del alumno, con sus llaves y sus instructores resueltos. */
  record UpcomingRow(
      UUID id,
      String title,
      String description,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      String recurrence,
      LocalDate repeatUntil,
      UUID coverImageId,
      List<UUID> instructorIds,
      List<MembershipRef> memberships,
      List<ProductRef> products) {

    public LiveSessionSeries serie() {
      return LiveSessionSeries.of(startsAt, endsAt, recurrence, repeatUntil);
    }
  }

  /**
   * El predicado del listado. {@code instructorId} no nulo deja solo las clases de las que esa
   * persona es instructor —el filtro de administración y el alcance de `RF-AC-048`—; {@code ended}
   * se compara con {@code ahora}.
   */
  record LiveSessionFilter(
      UUID instructorId,
      String status,
      Boolean ended,
      OffsetDateTime from,
      OffsetDateTime to,
      Instant ahora) {}
}
