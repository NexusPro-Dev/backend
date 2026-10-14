package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.application.CourseDetailResponse.CourseMembershipRef;
import com.factech.nexus.modules.academy.application.CourseDetailResponse.CourseProductRef;
import com.factech.nexus.modules.academy.application.LiveSessionDetailResponse.LiveSessionCategoryRef;
import com.factech.nexus.modules.academy.application.LiveSessionDetailResponse.LiveSessionInstructorRef;
import com.factech.nexus.modules.academy.domain.models.LiveSessionSeries.Occurrence;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository.LiveSessionItemRow;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionQueryRepository.UpcomingRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
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
      @Schema(description = "La portada, pública; nula si no tiene.") String coverImageUrl,
      @Schema(description = "Su categoría (RN-AC-034).") LiveSessionCategoryRef category,
      @Schema(description = "Los instructores de la clase; vacía si no tiene (RN-AC-032).")
          List<LiveSessionInstructorRef> instructors,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      @Schema(description = "DAILY, WEEKLY o MONTHLY; nula si no se repite (RN-AC-031).")
          String recurrence,
      @Schema(description = "El último día de la serie, incluido; nulo si no se repite.")
          LocalDate repeatUntil,
      @Schema(
              description =
                  "Inicio de la fecha en curso o la siguiente; nulo si terminó o se canceló.")
          OffsetDateTime nextStartsAt,
      @Schema(
              description =
                  "Fin de la fecha en curso o la siguiente; nulo si terminó o se canceló.")
          OffsetDateTime nextEndsAt,
      String status,
      @Schema(description = "Terminó su última fecha.") boolean ended,
      long membershipCount,
      long productCount,
      long registrationCount) {

    public static Item from(
        LiveSessionItemRow fila,
        boolean terminada,
        Occurrence siguiente,
        List<LiveSessionInstructorRef> instructores) {
      return new Item(
          fila.id(),
          fila.title(),
          AcademyImageUrls.de(fila.coverImageId()),
          new LiveSessionCategoryRef(fila.categoryId(), fila.categoryName()),
          instructores,
          fila.startsAt(),
          fila.endsAt(),
          fila.recurrence(),
          fila.repeatUntil(),
          siguiente == null ? null : siguiente.startsAt(),
          siguiente == null ? null : siguiente.endsAt(),
          fila.status(),
          terminada,
          fila.membershipCount(),
          fila.productCount(),
          fila.registrationCount());
    }
  }

  /**
   * El enlace de anfitrión, recién pedido a Zoom y sin guardar (`RN-AC-030`), con la fecha en curso
   * o la siguiente (`RN-AC-031`).
   */
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
      @Schema(description = "La portada, pública; nula si no tiene.") String coverImageUrl,
      @Schema(description = "Su categoría (RN-AC-034).") LiveSessionCategoryRef category,
      @Schema(description = "Los instructores de la clase; vacía si no tiene (RN-AC-032).")
          List<LiveSessionInstructorRef> instructors,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      @Schema(description = "DAILY, WEEKLY o MONTHLY; nula si no se repite (RN-AC-031).")
          String recurrence,
      @Schema(description = "El último día de la serie, incluido; nulo si no se repite.")
          LocalDate repeatUntil,
      @Schema(
              description =
                  "Inicio de la fecha en curso o la siguiente; nulo si terminó o se canceló.")
          OffsetDateTime nextStartsAt,
      @Schema(
              description =
                  "Fin de la fecha en curso o la siguiente; nulo si terminó o se canceló.")
          OffsetDateTime nextEndsAt,
      @Schema(description = "Alguna de sus fechas ya empezó y no ha terminado.") boolean inProgress,
      @Schema(description = "Se le abre a quien pregunta.") boolean accessible,
      @Schema(description = "Ya pidió entrar: tiene su enlace.") boolean registered,
      List<CourseMembershipRef> memberships,
      List<CourseProductRef> products) {

    public static Available from(
        UpcomingRow fila,
        Occurrence siguiente,
        List<LiveSessionInstructorRef> instructores,
        boolean enCurso,
        boolean accesible,
        boolean registrado) {
      return new Available(
          fila.id(),
          fila.title(),
          fila.description(),
          AcademyImageUrls.de(fila.coverImageId()),
          new LiveSessionCategoryRef(fila.categoryId(), fila.categoryName()),
          instructores,
          fila.startsAt(),
          fila.endsAt(),
          fila.recurrence(),
          fila.repeatUntil(),
          siguiente == null ? null : siguiente.startsAt(),
          siguiente == null ? null : siguiente.endsAt(),
          enCurso,
          accesible,
          registrado,
          fila.memberships().stream().map(CourseMembershipRef::from).toList(),
          fila.products().stream().map(CourseProductRef::from).toList());
    }
  }

  /**
   * Lo que recibe quien entra: su enlace personal (`RN-AC-027`), con la fecha en curso o la
   * siguiente (`RN-AC-031`).
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "JoinLiveSessionResponse")
  public record Join(
      @Schema(description = "El enlace personal de Zoom de quien pregunta. No se comparte.")
          String joinUrl,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      OffsetDateTime registeredAt) {}
}
