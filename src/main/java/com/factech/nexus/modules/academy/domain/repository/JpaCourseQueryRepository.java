package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.application.ListCoursesRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CourseQueryRepository} sobre SQL nativo.
 *
 * <p><b>Un solo bloque de columnas</b> ({@link #COLUMNAS}) para el detalle y el listado, con el
 * instructor por {@code JOIN users} —tres columnas— y las cuatro cuentas como columnas más, cada
 * una una subconsulta escalar: el número de sentencias no depende de cuántos cursos se lean.
 * <b>{@link #CUENTA_DE_MEMBRESIAS} es un literal hasta `RF-AC-020`</b>, que la sustituye por la
 * subconsulta sobre {@code course_memberships}; las de módulos y lecciones son reales desde el
 * bloque 3, sobre los fragmentos de {@link JpaCourseModuleQueryRepository} que el aula reutiliza.
 * Las lecturas de relaciones devuelven vacío sin consultar nada hasta el bloque 4, cada una con la
 * nota de qué sentencia la sustituye; las del árbol son reales.
 */
@Repository
public class JpaCourseQueryRepository implements CourseQueryRepository {

  /**
   * `RF-AC-020` la sustituye por: {@code (SELECT count(*) FROM course_memberships m WHERE
   * m.course_id = c.id)}.
   */
  private static final String CUENTA_DE_MEMBRESIAS = "0";

  /** Los módulos vivos del curso (`RF-AC-022`): un inactivo cuenta, un retirado no. */
  private static final String CUENTA_DE_MODULOS =
      "(SELECT count(*) FROM course_modules m WHERE m.course_id = c.id AND m.deleted_at IS NULL)";

  /**
   * Los módulos ofrecibles del curso (`RN-AC-015`): vivos, {@code ACTIVO} y con al menos una
   * lección ofrecible, sobre el fragmento {@link JpaCourseModuleQueryRepository#MODULO_OFRECIBLE}
   * que el aula reutiliza. Es la entrada de {@code CourseOfferability} y no la decisión.
   */
  private static final String CUENTA_DE_MODULOS_OFRECIBLES =
      "(SELECT count(*) FROM course_modules m WHERE m.course_id = c.id AND "
          + JpaCourseModuleQueryRepository.MODULO_OFRECIBLE
          + ")";

  /** Las lecciones vivas de los módulos vivos del curso (`RF-AC-028`). */
  private static final String CUENTA_DE_LECCIONES =
      "(SELECT count(*) FROM lessons l JOIN course_modules m ON m.id = l.module_id"
          + " AND m.deleted_at IS NULL WHERE m.course_id = c.id AND l.deleted_at IS NULL)";

  private static final String COLUMNAS =
      """
      c.id AS id, c.title AS title, c.instructor_id AS instructor_id,
      u.username AS instructor_username, u.first_name AS instructor_first_name,
      u.last_name AS instructor_last_name,
      c.difficulty AS difficulty, c.short_description AS short_description,
      c.long_description AS long_description, c.intro_video_url AS intro_video_url,
      c.display_order AS display_order, c.status AS status, c.cover_image_id AS cover_image_id,
      """
          + CUENTA_DE_MEMBRESIAS
          + " AS membership_count, "
          + CUENTA_DE_MODULOS
          + " AS module_count, "
          + CUENTA_DE_MODULOS_OFRECIBLES
          + " AS offerable_module_count, "
          + CUENTA_DE_LECCIONES
          + """
           AS lesson_count,
          c.created_at AS created_at, c.updated_at AS updated_at, c.deleted_at AS deleted_at
          """;

  private static final String DESDE = " FROM courses c JOIN users u ON u.id = c.instructor_id ";

  private final EntityManager em;

  public JpaCourseQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CourseRow> findDetail(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    List<Tuple> filas =
        em.createNativeQuery("SELECT " + COLUMNAS + DESDE + "WHERE c.id = :id", Tuple.class)
            .setParameter("id", id)
            .getResultList();
    return filas.stream().map(JpaCourseQueryRepository::curso).findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public List<CourseRow> search(
      ListCoursesRequest filtros, String ordenamiento, int offset, int limit) {
    Query consulta =
        em.createNativeQuery(
            "SELECT "
                + COLUMNAS
                + DESDE
                + "WHERE "
                + predicado(filtros)
                + " ORDER BY "
                + ordenamiento,
            Tuple.class);
    enlazar(consulta, filtros);
    List<Tuple> filas = consulta.setFirstResult(offset).setMaxResults(limit).getResultList();
    return filas.stream().map(JpaCourseQueryRepository::curso).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long count(ListCoursesRequest filtros) {
    Query consulta =
        em.createNativeQuery("SELECT count(*)" + DESDE + "WHERE " + predicado(filtros));
    enlazar(consulta, filtros);
    return ((Number) consulta.getSingleResult()).longValue();
  }

  @Override
  @Transactional(readOnly = true)
  public List<CategoryRef> findCategoriesOf(UUID courseId) {
    // Hasta `RF-AC-016` no hay tabla de clasificación: ni una sentencia. Ese
    // requerimiento escribe aquí el SELECT sobre course_category_items JOIN
    // course_categories (deleted_at IS NULL) ORDER BY k.display_order, k.id.
    return List.of();
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, List<CategoryRef>> findCategoriesOfCourses(List<UUID> courseIds) {
    // `RF-AC-016`: el mismo SELECT con WHERE i.course_id IN (:ids), agrupado
    // por curso en Java — la segunda sentencia fija de una página.
    return Map.of();
  }

  @Override
  @Transactional(readOnly = true)
  public List<RecommendedCourseRow> findRecommendedOf(UUID courseId) {
    // `RF-AC-018`: course_recommendations JOIN courses con las COLUMNAS de
    // cuentas del recomendado, para decidir su ofrecibilidad por fila.
    return List.of();
  }

  @Override
  @Transactional(readOnly = true)
  public List<MembershipRef> findMembershipsOf(UUID courseId) {
    // `RF-AC-020`: course_memberships JOIN memberships (id, code, name, color)
    // ORDER BY m.level — cuatro columnas de lectura, como RF-PM-002.
    return List.of();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ModuleRow> findModulesOf(UUID courseId) {
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT "
                    + JpaCourseModuleQueryRepository.COLUMNAS_DEL_MODULO
                    + " FROM course_modules m WHERE m.course_id = :curso"
                    + " ORDER BY m.display_order, m.id",
                Tuple.class)
            .setParameter("curso", courseId)
            .getResultList();
    return filas.stream().map(JpaCourseModuleQueryRepository::modulo).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<LessonRow> findLessonsOfModules(List<UUID> moduleIds) {
    if (moduleIds.isEmpty()) {
      return List.of();
    }
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT "
                    + JpaLessonQueryRepository.COLUMNAS_DEL_RESUMEN
                    + " FROM lessons l WHERE l.module_id IN (:modulos)"
                    + " ORDER BY l.module_id, l.display_order, l.id",
                Tuple.class)
            .setParameter("modulos", moduleIds)
            .getResultList();
    return filas.stream().map(JpaLessonQueryRepository::resumen).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countActiveModulesOf(UUID courseId) {
    // La condición de activar el curso (`RN-AC-009`): un módulo ACTIVO vivo,
    // con o sin lección — la lección la exigió el módulo al activarse.
    return ((Number)
            em.createNativeQuery(
                    "SELECT count(*) FROM course_modules m WHERE m.course_id = :curso"
                        + " AND m.deleted_at IS NULL AND m.status = 'ACTIVO'")
                .setParameter("curso", courseId)
                .getSingleResult())
        .longValue();
  }

  private static String predicado(ListCoursesRequest filtros) {
    StringBuilder donde = new StringBuilder();
    donde.append(filtros.incluirEliminados() ? "1 = 1" : "c.deleted_at IS NULL");
    if (filtros.q() != null) {
      donde.append(" AND f_unaccent(lower(c.title)) LIKE f_unaccent(lower(:termino)) ESCAPE '\\'");
    }
    if (filtros.instructorId() != null) {
      donde.append(" AND c.instructor_id = :instructor");
    }
    if (filtros.difficulty() != null) {
      donde.append(" AND c.difficulty = :dificultad");
    }
    if (filtros.status() != null) {
      donde.append(" AND c.status = :estado");
    }
    if (filtros.categoryId() != null) {
      // `RF-AC-016` lo sustituye por EXISTS sobre course_category_items; hasta
      // entonces ningún curso está en ninguna categoría y el filtro no deja
      // pasar nada (`RF-AC-009` §6.1).
      donde.append(" AND 1 = 0");
    }
    return donde.toString();
  }

  private static void enlazar(Query consulta, ListCoursesRequest filtros) {
    if (filtros.q() != null) {
      consulta.setParameter("termino", "%" + escapar(filtros.q()) + "%");
    }
    if (filtros.instructorId() != null) {
      consulta.setParameter("instructor", filtros.instructorId());
    }
    if (filtros.difficulty() != null) {
      consulta.setParameter("dificultad", filtros.difficulty());
    }
    if (filtros.status() != null) {
      consulta.setParameter("estado", filtros.status());
    }
  }

  private static String escapar(String termino) {
    return termino.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static CourseRow curso(Tuple fila) {
    return new CourseRow(
        (UUID) fila.get("id"),
        (String) fila.get("title"),
        (UUID) fila.get("instructor_id"),
        (String) fila.get("instructor_username"),
        (String) fila.get("instructor_first_name"),
        (String) fila.get("instructor_last_name"),
        (String) fila.get("difficulty"),
        (String) fila.get("short_description"),
        (String) fila.get("long_description"),
        (String) fila.get("intro_video_url"),
        ((Number) fila.get("display_order")).intValue(),
        (String) fila.get("status"),
        (UUID) fila.get("cover_image_id"),
        ((Number) fila.get("membership_count")).longValue(),
        ((Number) fila.get("module_count")).longValue(),
        ((Number) fila.get("offerable_module_count")).longValue(),
        ((Number) fila.get("lesson_count")).longValue(),
        momento(fila.get("created_at")),
        momento(fila.get("updated_at")),
        momento(fila.get("deleted_at")));
  }

  static OffsetDateTime momento(Object valor) {
    return switch (valor) {
      case null -> null;
      case OffsetDateTime instante -> instante;
      case Instant instante -> instante.atOffset(ZoneOffset.UTC);
      case Timestamp marca -> marca.toInstant().atOffset(ZoneOffset.UTC);
      default ->
          throw new IllegalStateException(
              "Tipo temporal inesperado en la proyección: " + valor.getClass());
    };
  }
}
