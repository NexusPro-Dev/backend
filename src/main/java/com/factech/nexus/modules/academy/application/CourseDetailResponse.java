package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.domain.models.CourseOfferability;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.CategoryRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.CourseRow;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.LessonRow;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.MembershipRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ModuleRow;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.RecommendedCourseRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * El curso como lo ve administración (`RF-AC-010`): lo suyo, el instructor resuelto, sus tres
 * relaciones, su árbol de módulos y lecciones con estados y marcas, y <b>por qué no se ofrece</b>.
 *
 * <p>Es la respuesta del alta, del detalle y de las siete escrituras del curso, para que el
 * frontend tenga una sola pantalla. <b>Todo siempre presente</b> —listas vacías, nulos donde no
 * hay— salvo {@code deletedAt} y {@code deletionReason}, que solo viajan en un retirado.
 *
 * <p>{@code offerable} y {@code offerableReason} los decide {@link CourseOfferability} con las
 * cuentas de la fila: {@code true} con motivo nulo, o {@code false} con el <b>primer</b> motivo en
 * su orden. <b>Las listas y el árbol viajan vacíos hasta sus requerimientos</b> (bloques 3 y 4),
 * que enmiendan la lectura y no esta forma.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "CourseDetailResponse")
public record CourseDetailResponse(
    UUID id,
    String title,
    InstructorRef instructor,
    String difficulty,
    String shortDescription,
    String longDescription,
    String introVideoUrl,
    int displayOrder,
    String status,
    String coverImageUrl,
    List<CourseCategoryRef> categories,
    List<RecommendedCourseRef> recommendedCourses,
    List<CourseMembershipRef> memberships,
    List<ModuleDetail> modules,
    long totalDurationMinutes,
    long lessonCount,
    boolean offerable,
    String offerableReason,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) OffsetDateTime deletedAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) String deletionReason) {

  /** Quien enseña el curso, con su nombre <b>actual</b>: no es una copia. */
  @Schema(name = "CourseInstructorRef")
  public record InstructorRef(UUID id, String username, String fullName) {

    static InstructorRef from(CourseRow fila) {
      return new InstructorRef(
          fila.instructorId(),
          fila.instructorUsername(),
          nombreCompleto(fila.instructorFirstName(), fila.instructorLastName()));
    }

    static String nombreCompleto(String nombre, String apellido) {
      String completo =
          ((nombre == null ? "" : nombre) + " " + (apellido == null ? "" : apellido)).trim();
      return completo.isEmpty() ? null : completo;
    }
  }

  @Schema(name = "CourseCategoryRef")
  public record CourseCategoryRef(UUID id, String name, String color, String icon) {

    static CourseCategoryRef from(CategoryRef fila) {
      return new CourseCategoryRef(fila.id(), fila.name(), fila.color(), fila.icon());
    }
  }

  /** Un curso recomendado, con su estado y si se ofrece: administración ve la que quedó colgada. */
  @Schema(name = "RecommendedCourseRef")
  public record RecommendedCourseRef(UUID id, String title, String status, boolean offerable) {

    static RecommendedCourseRef from(RecommendedCourseRow fila) {
      return new RecommendedCourseRef(fila.id(), fila.title(), fila.status(), fila.offerable());
    }
  }

  @Schema(name = "CourseMembershipRef")
  public record CourseMembershipRef(UUID id, String code, String name, String color) {

    static CourseMembershipRef from(MembershipRef fila) {
      return new CourseMembershipRef(fila.id(), fila.code(), fila.name(), fila.color());
    }
  }

  /** Un módulo dentro del árbol, con sus lecciones en orden y si se ofrece. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "CourseModuleDetail")
  public record ModuleDetail(
      UUID id,
      String title,
      String shortDescription,
      int displayOrder,
      String status,
      boolean deleted,
      String coverImageUrl,
      boolean offerable,
      long durationMinutes,
      List<LessonSummary> lessons) {}

  /** Una lección dentro del árbol, sin su contenido. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "CourseLessonSummary")
  public record LessonSummary(
      UUID id,
      String type,
      String title,
      int durationMinutes,
      int displayOrder,
      String status,
      boolean open,
      boolean deleted) {

    static LessonSummary from(LessonRow fila) {
      return new LessonSummary(
          fila.id(),
          fila.type(),
          fila.title(),
          fila.durationMinutes(),
          fila.displayOrder(),
          fila.status(),
          fila.open(),
          fila.retirada());
    }
  }

  public static CourseDetailResponse from(
      CourseRow fila,
      List<CategoryRef> categorias,
      List<RecommendedCourseRow> recomendados,
      List<MembershipRef> membresias,
      List<ModuleDetail> modulos,
      long duracionTotal,
      long lecciones,
      CourseOfferability.Resultado ofrecibilidad,
      String motivoDeRetiro) {
    return new CourseDetailResponse(
        fila.id(),
        fila.title(),
        InstructorRef.from(fila),
        fila.difficulty(),
        fila.shortDescription(),
        fila.longDescription(),
        fila.introVideoUrl(),
        fila.displayOrder(),
        fila.status(),
        AcademyImageUrls.de(fila.coverImageId()),
        categorias.stream().map(CourseCategoryRef::from).toList(),
        recomendados.stream().map(RecommendedCourseRef::from).toList(),
        membresias.stream().map(CourseMembershipRef::from).toList(),
        modulos,
        duracionTotal,
        lecciones,
        ofrecibilidad.offerable(),
        ofrecibilidad.reason(),
        fila.createdAt(),
        fila.updatedAt(),
        fila.deletedAt(),
        motivoDeRetiro);
  }

  /** Un módulo con sus lecciones, tal como el lector lo arma (`RF-AC-022` lo estrena). */
  public static ModuleDetail modulo(ModuleRow fila, List<LessonRow> lecciones, boolean ofrecible) {
    return new ModuleDetail(
        fila.id(),
        fila.title(),
        fila.shortDescription(),
        fila.displayOrder(),
        fila.status(),
        fila.retirado(),
        AcademyImageUrls.de(fila.coverImageId()),
        ofrecible,
        fila.durationMinutes(),
        lecciones.stream().map(LessonSummary::from).toList());
  }
}
