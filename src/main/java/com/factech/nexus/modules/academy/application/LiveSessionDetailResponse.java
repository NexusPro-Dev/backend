package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.application.CourseDetailResponse.CourseMembershipRef;
import com.factech.nexus.modules.academy.application.CourseDetailResponse.CourseProductRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.MembershipRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ProductRef;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository.LiveSessionRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Una clase en vivo entera para administración y para el instructor (`RF-AC-043`): su acceso, su
 * reunión —<b>solo el identificador</b>, sin enlace ni contraseña (`RN-AC-026`)— y quién se
 * registró para entrar. Todo siempre presente.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "LiveSessionDetailResponse")
public record LiveSessionDetailResponse(
    UUID id,
    String title,
    String description,
    LiveSessionCourseRef course,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    @Schema(description = "PROGRAMADA o CANCELADA.") String status,
    @Schema(description = "Su hora de fin ya pasó.") boolean ended,
    OffsetDateTime cancelledAt,
    String cancellationReason,
    @Schema(description = "El identificador de la reunión en Zoom; sin enlace ni contraseña.")
        long zoomMeetingId,
    List<CourseMembershipRef> memberships,
    List<CourseProductRef> products,
    List<LiveSessionRegistrationItem> registrations,
    UUID createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  /** El curso del que cuelga, o nulo si es suelta. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "LiveSessionCourseRef")
  public record LiveSessionCourseRef(UUID id, String title) {

    public static LiveSessionCourseRef of(UUID id, String title) {
      return id == null ? null : new LiveSessionCourseRef(id, title);
    }
  }

  /** Quién pidió entrar, y cuándo. Sin su enlace: es de esa persona. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "LiveSessionRegistrationItem")
  public record LiveSessionRegistrationItem(
      UUID userId, String username, String fullName, OffsetDateTime registeredAt) {}

  public static LiveSessionDetailResponse from(
      LiveSessionRow fila,
      boolean terminada,
      List<MembershipRef> membresias,
      List<ProductRef> servicios,
      List<LiveSessionRegistrationItem> registrados) {
    return new LiveSessionDetailResponse(
        fila.id(),
        fila.title(),
        fila.description(),
        LiveSessionCourseRef.of(fila.courseId(), fila.courseTitle()),
        fila.startsAt(),
        fila.endsAt(),
        fila.status(),
        terminada,
        fila.cancelledAt(),
        fila.cancellationReason(),
        fila.zoomMeetingId(),
        membresias.stream().map(CourseMembershipRef::from).toList(),
        servicios.stream().map(CourseProductRef::from).toList(),
        registrados,
        fila.createdBy(),
        fila.createdAt(),
        fila.updatedAt());
  }
}
