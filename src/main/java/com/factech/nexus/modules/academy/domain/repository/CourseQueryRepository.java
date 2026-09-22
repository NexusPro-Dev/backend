package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.application.ListCoursesRequest;
import com.factech.nexus.modules.academy.domain.models.CourseOfferability;
import com.factech.nexus.modules.academy.domain.models.LessonOfferability;
import com.factech.nexus.modules.academy.domain.models.ModuleOfferability;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Las lecturas del curso (`RF-AC-009`, `RF-AC-010` y todo lo que devuelve el curso), sobre
 * proyecciones y no sobre la entidad.
 *
 * <p><b>El instructor se resuelve por {@code JOIN users} en la misma sentencia</b> —tres columnas:
 * nombre de usuario, nombre y apellido; ni el correo ni el estado—, con el precedente de
 * `RF-PM-012`: la regla de ArchUnit prohíbe importar repositorios y entidades de `SP`, no nombrar
 * sus tablas en una sentencia de lectura, y un puerto llamado por fila sería el {@code N+1} de
 * siempre. Al <b>escribir</b> el instructor cruza por los puertos.
 *
 * <p><b>Las cuentas viajan en la misma sentencia que la fila</b>, como subconsultas escalares, y
 * son lo que {@link CourseOfferability} necesita para decidir por fila sin otra consulta. Las de
 * módulos y lecciones son reales desde el bloque 3; <b>la de membresías es un literal cero hasta
 * `RF-AC-020`</b>, que sustituye el literal y la nota que lo acompaña. Las lecturas de relaciones
 * devuelven vacío sin consultar nada hasta el bloque 4; las del árbol son reales.
 */
public interface CourseQueryRepository {

  Optional<CourseRow> findDetail(UUID id);

  List<CourseRow> search(ListCoursesRequest filtros, String ordenamiento, int offset, int limit);

  long count(ListCoursesRequest filtros);

  /** Las categorías vivas de un curso, en su orden. <b>Vacío hasta `RF-AC-016`.</b> */
  List<CategoryRef> findCategoriesOf(UUID courseId);

  /**
   * Las categorías vivas de varios cursos en una sentencia, agrupadas por curso — la segunda
   * sentencia fija de una página. <b>Vacío hasta `RF-AC-016`.</b>
   */
  Map<UUID, List<CategoryRef>> findCategoriesOfCourses(List<UUID> courseIds);

  /**
   * Los cursos que este recomienda, con lo que hace falta para decidir si se ofrecen. <b>Vacío
   * hasta `RF-AC-018`.</b>
   */
  List<RecommendedCourseRow> findRecommendedOf(UUID courseId);

  /**
   * Las membresías que abren el curso, resueltas por {@code JOIN memberships}. <b>Vacío hasta
   * `RF-AC-020`.</b>
   */
  List<MembershipRef> findMembershipsOf(UUID courseId);

  /** Los módulos del curso en su orden, vivos y retirados, con sus cuentas (`RF-AC-022`). */
  List<ModuleRow> findModulesOf(UUID courseId);

  /** Las lecciones de esos módulos en su orden, en una sentencia, sin contenido (`RF-AC-028`). */
  List<LessonRow> findLessonsOfModules(List<UUID> moduleIds);

  /** Cuántos módulos {@code ACTIVO} vivos tiene el curso: la condición de activar (`RN-AC-009`). */
  long countActiveModulesOf(UUID courseId);

  /**
   * Un curso como sale de la tabla, con su instructor y las cuentas que la ofrecibilidad y el
   * listado necesitan ya hechas.
   */
  record CourseRow(
      UUID id,
      String title,
      UUID instructorId,
      String instructorUsername,
      String instructorFirstName,
      String instructorLastName,
      String difficulty,
      String shortDescription,
      String longDescription,
      String introVideoUrl,
      int displayOrder,
      String status,
      UUID coverImageId,
      long membershipCount,
      long moduleCount,
      long offerableModuleCount,
      long lessonCount,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime deletedAt) {

    public boolean retirado() {
      return deletedAt != null;
    }

    /** `RN-AC-015` sobre las cuentas que vinieron con la fila. */
    public CourseOfferability.Resultado ofrecibilidad() {
      return CourseOfferability.decidir(
          retirado(),
          status,
          shortDescription != null,
          longDescription != null,
          membershipCount,
          offerableModuleCount);
    }
  }

  /** Una categoría dentro de un curso: lo justo para pintarla. */
  record CategoryRef(UUID id, String name, String color, String icon) {}

  /** Una membresía que abre el curso, con lo que `SP` publica de ella por su puerto. */
  record MembershipRef(UUID id, String code, String name, String color) {}

  /** Un curso recomendado, con su estado y si se ofrece, para administración y para el aula. */
  record RecommendedCourseRow(
      UUID id,
      String title,
      String status,
      String difficulty,
      UUID coverImageId,
      boolean offerable) {}

  /**
   * Un módulo como sale de la tabla, con la cuenta de lecciones ofrecibles —activas, vivas, con
   * contenido— y la duración —la suma de las activas vivas— ya hechas. Sirve al árbol del curso y
   * al detalle del módulo.
   */
  record ModuleRow(
      UUID id,
      UUID courseId,
      String title,
      String shortDescription,
      String longDescription,
      String presentationVideoUrl,
      int displayOrder,
      String status,
      UUID coverImageId,
      long offerableLessonCount,
      long durationMinutes,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime deletedAt) {

    public boolean retirado() {
      return deletedAt != null;
    }

    /** `RN-AC-015` para el módulo, con la cuenta que vino con la fila. */
    public ModuleOfferability.Resultado ofrecibilidad() {
      return ModuleOfferability.decidir(retirado(), status, offerableLessonCount);
    }
  }

  /** Una lección dentro del árbol, sin su contenido — solo si lo tiene. */
  record LessonRow(
      UUID id,
      UUID moduleId,
      String type,
      String title,
      String description,
      int durationMinutes,
      int displayOrder,
      String status,
      boolean open,
      boolean hasContent,
      OffsetDateTime deletedAt) {

    public boolean retirada() {
      return deletedAt != null;
    }

    /** `RN-AC-015` para la lección: activa, viva y con contenido. */
    public boolean ofrecida() {
      return LessonOfferability.offered(status, retirada(), hasContent);
    }
  }
}
