package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.MembershipRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ProductRef;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
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

  List<LiveSessionItemRow> search(LiveSessionFilter filtro, int offset, int size);

  long count(LiveSessionFilter filtro);

  /** Las programadas que no han terminado en ese instante, por inicio, con sus llaves. */
  List<UpcomingRow> findUpcoming(Instant ahora, UUID courseId);

  /** De esas clases, en cuáles está registrada esa persona. */
  Set<UUID> registeredAmong(UUID userId, List<UUID> ids);

  /** Una clase con su curso y el instructor del curso, que decide la propiedad (`RN-AC-028`). */
  record LiveSessionRow(
      UUID id,
      UUID courseId,
      String courseTitle,
      UUID courseInstructorId,
      String title,
      String description,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      long zoomMeetingId,
      String status,
      OffsetDateTime cancelledAt,
      String cancellationReason,
      UUID createdBy,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {

    public boolean programada() {
      return "PROGRAMADA".equals(status);
    }

    public boolean terminada(Instant ahora) {
      return !ahora.isBefore(endsAt.toInstant());
    }

    /** Si la gobierna ese instructor: cuelga de un curso que él dicta. */
    public boolean deInstructor(UUID actor) {
      return courseId != null && actor != null && actor.equals(courseInstructorId);
    }
  }

  record RegistrationRow(
      UUID userId, String registrantId, String joinUrl, OffsetDateTime registeredAt) {}

  /** Una fila del listado de administración y del instructor. */
  record LiveSessionItemRow(
      UUID id,
      UUID courseId,
      String courseTitle,
      String title,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      String status,
      long membershipCount,
      long productCount,
      long registrationCount) {}

  /** Una clase de la vitrina del alumno, con sus llaves resueltas. */
  record UpcomingRow(
      UUID id,
      UUID courseId,
      String courseTitle,
      String title,
      String description,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      List<MembershipRef> memberships,
      List<ProductRef> products) {}

  /**
   * El predicado del listado. {@code instructorId} no nulo deja solo las clases de los cursos que
   * dicta (`RF-AC-048`); {@code ended} se compara con {@code ahora}.
   */
  record LiveSessionFilter(
      UUID instructorId,
      UUID courseId,
      String status,
      Boolean ended,
      OffsetDateTime from,
      OffsetDateTime to,
      Instant ahora) {}
}
