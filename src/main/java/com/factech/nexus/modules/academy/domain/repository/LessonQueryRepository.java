package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.CourseOfferability;
import com.factech.nexus.modules.academy.domain.models.LessonOfferability;
import com.factech.nexus.modules.academy.domain.models.ModuleOfferability;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * La lectura de una lección entera, <b>con su contenido</b>, para las operaciones que la devuelven
 * (`RF-AC-028` a `RF-AC-030`) y para el detalle de administración (`RF-AC-036`), que la lee en
 * cualquier estado. La pertenencia en los tres niveles va en el {@code WHERE}.
 */
public interface LessonQueryRepository {

  Optional<LessonDetailRow> findDetail(UUID courseId, UUID moduleId, UUID lessonId);

  /**
   * La lección del aula (`RF-AC-035`): la lección con su contenido, si es de un módulo de ese
   * curso, con lo que los tres objetos de ofrecibilidad necesitan de cada nivel y las dos cuentas
   * de llaves del curso — en una sentencia.
   */
  Optional<ClassroomLessonRow> findClassroomLesson(UUID courseId, UUID lessonId);

  /** La lección con las entradas de ofrecibilidad de su módulo y de su curso. */
  record ClassroomLessonRow(
      LessonDetailRow lesson,
      String moduleStatus,
      boolean moduleRetired,
      long moduleOfferableLessonCount,
      String courseStatus,
      boolean courseRetired,
      boolean courseHasShortDescription,
      boolean courseHasLongDescription,
      long courseOfferableModuleCount,
      long courseMembershipCount,
      long courseProductCount) {

    /** `RN-AC-015` en los tres niveles, en orden: el curso, el módulo y la lección. */
    public boolean ofrecida() {
      return CourseOfferability.decidir(
                  courseRetired,
                  courseStatus,
                  courseHasShortDescription,
                  courseHasLongDescription,
                  courseOfferableModuleCount)
              .offerable()
          && ModuleOfferability.decidir(moduleRetired, moduleStatus, moduleOfferableLessonCount)
              .offerable()
          && LessonOfferability.offered(
              lesson.status(), lesson.retirada(), lesson.content() != null);
    }

    /** Un curso sin membresías ni servicios es de todos (`requirements/ac.md` §5.2.12). */
    public boolean cursoSinLlaves() {
      return courseMembershipCount == 0 && courseProductCount == 0;
    }
  }

  /** La lección como sale de la tabla, contenido incluido. */
  record LessonDetailRow(
      UUID id,
      UUID moduleId,
      UUID courseId,
      String type,
      String title,
      String description,
      String content,
      int durationSeconds,
      int displayOrder,
      boolean open,
      String status,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime deletedAt) {

    public boolean retirada() {
      return deletedAt != null;
    }
  }
}
