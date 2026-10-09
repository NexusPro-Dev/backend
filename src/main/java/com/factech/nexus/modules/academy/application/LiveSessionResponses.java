package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.application.CourseDetailResponse.CourseMembershipRef;
import com.factech.nexus.modules.academy.application.CourseDetailResponse.CourseProductRef;
import com.factech.nexus.modules.academy.application.LiveSessionDetailResponse.LiveSessionCourseRef;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository.LiveSessionItemRow;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository.UpcomingRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Las respuestas pequeñas de las clases en vivo (`RF-AC-042`, `RF-AC-047`, `RF-AC-053`, `054`). */
public final class LiveSessionResponses {

  private LiveSessionResponses() {}

  /** Una fila del listado de administración y del instructor. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "LiveSessionItem")
  public record Item(
      UUID id,
      String title,
      LiveSessionCourseRef course,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      String status,
      boolean ended,
      long membershipCount,
      long productCount,
      long registrationCount) {

    public static Item from(LiveSessionItemRow fila, boolean terminada) {
      return new Item(
          fila.id(),
          fila.title(),
          LiveSessionCourseRef.of(fila.courseId(), fila.courseTitle()),
          fila.startsAt(),
          fila.endsAt(),
          fila.status(),
          terminada,
          fila.membershipCount(),
          fila.productCount(),
          fila.registrationCount());
    }
  }

  /** El enlace de anfitrión, recién pedido a Zoom y sin guardar (`RN-AC-030`). */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "LiveSessionHostLinkResponse")
  public record HostLink(String startUrl, OffsetDateTime startsAt, OffsetDateTime endsAt) {}

  /** Una clase de la vitrina del alumno: sin nada de Zoom. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "AvailableLiveSessionItem")
  public record Available(
      UUID id,
      String title,
      String description,
      LiveSessionCourseRef course,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      @Schema(description = "Ya empezó y no ha terminado.") boolean inProgress,
      @Schema(description = "Se le abre a quien pregunta.") boolean accessible,
      @Schema(description = "Ya pidió entrar: tiene su enlace.") boolean registered,
      java.util.List<CourseMembershipRef> memberships,
      java.util.List<CourseProductRef> products) {

    public static Available from(
        UpcomingRow fila, boolean enCurso, boolean accesible, boolean registrado) {
      return new Available(
          fila.id(),
          fila.title(),
          fila.description(),
          LiveSessionCourseRef.of(fila.courseId(), fila.courseTitle()),
          fila.startsAt(),
          fila.endsAt(),
          enCurso,
          accesible,
          registrado,
          fila.memberships().stream().map(CourseMembershipRef::from).toList(),
          fila.products().stream().map(CourseProductRef::from).toList());
    }
  }

  /** Lo que recibe quien entra: su enlace personal (`RN-AC-027`). */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "JoinLiveSessionResponse")
  public record Join(
      @Schema(description = "El enlace personal de Zoom de quien pregunta. No se comparte.")
          String joinUrl,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      OffsetDateTime registeredAt) {}
}
