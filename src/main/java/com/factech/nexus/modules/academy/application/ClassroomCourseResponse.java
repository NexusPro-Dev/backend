package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.application.ClassroomCatalogResponse.CurrentMembershipRef;
import com.factech.nexus.modules.academy.application.CourseDetailResponse.CourseCategoryRef;
import com.factech.nexus.modules.academy.application.CourseDetailResponse.CourseMembershipRef;
import com.factech.nexus.modules.academy.application.CourseDetailResponse.CourseProductRef;
import com.factech.nexus.modules.academy.application.CourseDetailResponse.InstructorRef;
import com.factech.nexus.modules.academy.domain.models.LessonProgress;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.CategoryRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.CourseRow;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.LessonRow;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.MembershipRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ModuleRow;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ProductRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.RecommendedCourseRow;
import com.factech.nexus.modules.academy.domain.repository.LessonProgressRepository.CourseFigures;
import com.factech.nexus.modules.academy.domain.repository.LessonProgressRepository.ProgressRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * El curso como lo ve el alumno (`RF-AC-034`): solo lo ofrecido, <b>sin estados, sin {@code
 * offerable} y sin contenido</b>, con {@code accessible} en el curso y en cada lección y las dos
 * listas de llaves como invitación. Todo siempre presente.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "ClassroomCourseResponse")
public record ClassroomCourseResponse(
    UUID id,
    String title,
    InstructorRef instructor,
    String difficulty,
    String shortDescription,
    String longDescription,
    String introVideoUrl,
    int displayOrder,
    String coverImageUrl,
    List<CourseCategoryRef> categories,
    List<ClassroomRecommendedCourse> recommendedCourses,
    List<CourseMembershipRef> memberships,
    List<CourseProductRef> products,
    CurrentMembershipRef currentMembership,
    boolean accessible,
    List<ClassroomModuleItem> modules,
    long totalDurationSeconds,
    long lessonCount,
    long openLessonCount,
    @Schema(description = "El avance de quien pregunta en este curso (`RN-AC-023`).")
        ProgressFigures progress) {

  /** Un curso recomendado que se ofrece: una invitación, sin estado. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "ClassroomRecommendedCourse")
  public record ClassroomRecommendedCourse(
      UUID id, String title, String difficulty, String coverImageUrl) {

    public static ClassroomRecommendedCourse from(RecommendedCourseRow fila) {
      return new ClassroomRecommendedCourse(
          fila.id(), fila.title(), fila.difficulty(), AcademyImageUrls.de(fila.coverImageId()));
    }
  }

  /** Un módulo ofrecible, con sus lecciones ofrecibles y su duración sobre ellas. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "ClassroomModuleItem")
  public record ClassroomModuleItem(
      UUID id,
      String title,
      String shortDescription,
      String longDescription,
      String presentationVideoUrl,
      int displayOrder,
      String coverImageUrl,
      long durationSeconds,
      List<ClassroomLessonItem> lessons) {

    public static ClassroomModuleItem from(ModuleRow fila, List<ClassroomLessonItem> lecciones) {
      return new ClassroomModuleItem(
          fila.id(),
          fila.title(),
          fila.shortDescription(),
          fila.longDescription(),
          fila.presentationVideoUrl(),
          fila.displayOrder(),
          AcademyImageUrls.de(fila.coverImageId()),
          lecciones.stream().mapToLong(ClassroomLessonItem::durationSeconds).sum(),
          lecciones);
    }
  }

  /** Una lección ofrecible, sin su contenido, con si se le abre a quien pregunta. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "ClassroomLessonItem")
  public record ClassroomLessonItem(
      UUID id,
      String type,
      String title,
      String description,
      int durationSeconds,
      int displayOrder,
      boolean open,
      boolean accessible,
      @Schema(description = "La mayor posición reportada por quien pregunta; 0 si no la abrió.")
          int watchedSeconds,
      boolean completed,
      OffsetDateTime completedAt) {

    /** Con el progreso de quien pregunta en esta lección, o nulo si nunca la abrió. */
    public static ClassroomLessonItem from(
        LessonRow fila, boolean accesible, ProgressRow progreso) {
      return new ClassroomLessonItem(
          fila.id(),
          fila.type(),
          fila.title(),
          fila.description(),
          fila.durationSeconds(),
          fila.displayOrder(),
          fila.open(),
          accesible,
          progreso == null ? 0 : progreso.watchedSeconds(),
          progreso != null && progreso.completada(),
          progreso == null ? null : progreso.completedAt());
    }
  }

  public static ClassroomCourseResponse from(
      CourseRow fila,
      List<CategoryRef> categorias,
      List<RecommendedCourseRow> recomendados,
      List<MembershipRef> membresias,
      List<ProductRef> servicios,
      CurrentMembershipRef vigente,
      boolean accesible,
      List<ClassroomModuleItem> modulos) {
    List<ClassroomLessonItem> lecciones =
        modulos.stream().flatMap(modulo -> modulo.lessons().stream()).toList();
    return new ClassroomCourseResponse(
        fila.id(),
        fila.title(),
        InstructorRef.from(fila),
        fila.difficulty(),
        fila.shortDescription(),
        fila.longDescription(),
        fila.introVideoUrl(),
        fila.displayOrder(),
        AcademyImageUrls.de(fila.coverImageId()),
        categorias.stream().map(CourseCategoryRef::from).toList(),
        recomendados.stream().map(ClassroomRecommendedCourse::from).toList(),
        membresias.stream().map(CourseMembershipRef::from).toList(),
        servicios.stream().map(CourseProductRef::from).toList(),
        vigente,
        accesible,
        modulos,
        modulos.stream().mapToLong(ClassroomModuleItem::durationSeconds).sum(),
        lecciones.size(),
        lecciones.stream().filter(ClassroomLessonItem::open).count(),
        avance(lecciones));
  }

  /**
   * `RN-AC-023` sobre las lecciones del árbol, que son las ofrecibles: lo mismo que suma el SQL.
   */
  private static ProgressFigures avance(List<ClassroomLessonItem> lecciones) {
    long vistos = 0;
    long total = 0;
    long completadas = 0;
    for (ClassroomLessonItem leccion : lecciones) {
      vistos +=
          LessonProgress.credito(
              leccion.watchedSeconds(), leccion.durationSeconds(), leccion.completed());
      total += leccion.durationSeconds();
      completadas += leccion.completed() ? 1 : 0;
    }
    return ProgressFigures.from(new CourseFigures(lecciones.size(), completadas, vistos, total));
  }
}
