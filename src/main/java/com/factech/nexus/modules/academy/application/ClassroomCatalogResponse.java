package com.factech.nexus.modules.academy.application;

import com.factech.nexus.modules.academy.application.CourseDetailResponse.CourseCategoryRef;
import com.factech.nexus.modules.academy.application.CourseDetailResponse.InstructorRef;
import com.factech.nexus.modules.academy.domain.repository.CourseCategoryQueryRepository.CourseCategoryRow;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.CategoryRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ClassroomCandidate;
import com.factech.nexus.modules.system.users.application.CurrentMembershipLookup.CurrentMembershipView;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * El catálogo del alumno (`RF-AC-033`): su membresía vigente, los cajones y los cursos ofrecidos
 * con {@code accessible} y lo que verá.
 *
 * <p><b>Todo siempre presente</b>: {@code currentMembership} y {@code coverImageUrl} viajan nulos
 * cuando no hay, y las listas vacías.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "ClassroomCatalogResponse")
public record ClassroomCatalogResponse(
    CurrentMembershipRef currentMembership,
    List<ClassroomCategoryItem> categories,
    List<ClassroomCourseItem> courses) {

  /** La membresía vigente de quien pregunta, o nula. Sin nivel ni fechas. */
  @Schema(name = "ClassroomCurrentMembershipRef")
  public record CurrentMembershipRef(UUID id, String code, String name, String color) {

    public static CurrentMembershipRef from(CurrentMembershipView vigente) {
      return vigente == null
          ? null
          : new CurrentMembershipRef(vigente.id(), vigente.code(), vigente.name(), vigente.color());
    }
  }

  /** Un cajón: una categoría viva, con o sin cursos. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "ClassroomCategoryItem")
  public record ClassroomCategoryItem(
      UUID id, String name, String color, String icon, int displayOrder, String coverImageUrl) {

    public static ClassroomCategoryItem from(CourseCategoryRow fila) {
      return new ClassroomCategoryItem(
          fila.id(),
          fila.name(),
          fila.color(),
          fila.icon(),
          fila.displayOrder(),
          AcademyImageUrls.de(fila.coverImageId()));
    }
  }

  /** Un curso ofrecido, con lo que el alumno verá y si se le abre entero. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "ClassroomCourseItem")
  public record ClassroomCourseItem(
      UUID id,
      String title,
      InstructorRef instructor,
      String difficulty,
      String shortDescription,
      int displayOrder,
      String coverImageUrl,
      List<CourseCategoryRef> categories,
      long totalDurationSeconds,
      long lessonCount,
      @Schema(description = "Lecciones abiertas (demostraciones) que el alumno verá.")
          long openLessonCount,
      @Schema(
              description =
                  "El curso entero se abre a quien pregunta: no declara llaves, su membresía"
                      + " vigente está en la lista o tiene vigente uno de sus servicios.")
          boolean accessible) {

    public static ClassroomCourseItem from(
        ClassroomCandidate candidato, List<CategoryRef> categorias, boolean accesible) {
      var fila = candidato.course();
      return new ClassroomCourseItem(
          fila.id(),
          fila.title(),
          InstructorRef.from(fila),
          fila.difficulty(),
          fila.shortDescription(),
          fila.displayOrder(),
          AcademyImageUrls.de(fila.coverImageId()),
          categorias.stream().map(CourseCategoryRef::from).toList(),
          candidato.durationSeconds(),
          candidato.lessonCount(),
          candidato.openLessonCount(),
          accesible);
    }
  }
}
