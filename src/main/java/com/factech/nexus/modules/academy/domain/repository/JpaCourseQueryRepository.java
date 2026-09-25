package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.application.ListCoursesRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * una una subconsulta escalar: el número de sentencias no depende de cuántos cursos se lean. Las de
 * membresías y servicios son reales desde `RF-AC-020` y `RF-AC-037`; las de módulos y lecciones
 * desde el bloque 3, sobre los fragmentos de {@link JpaCourseModuleQueryRepository} que el aula
 * reutiliza. De las relaciones, solo las recomendaciones devuelven vacío sin consultar nada, hasta
 * `RF-AC-018`; las del árbol son reales.
 *
 * <p>{@link #CUENTA_DE_MEMBRESIAS} y {@link #CUENTA_DE_MODULOS_OFRECIBLES} son de paquete porque
 * los cursos del detalle de la categoría (`RF-AC-003`) deciden su ofrecibilidad con las mismas
 * cuentas, sobre el mismo alias {@code c}.
 */
@Repository
public class JpaCourseQueryRepository implements CourseQueryRepository {

  /** Las membresías que abren el curso (`RF-AC-020`). Alias {@code cm}: {@code m} es del módulo. */
  static final String CUENTA_DE_MEMBRESIAS =
      "(SELECT count(*) FROM course_memberships cm WHERE cm.course_id = c.id)";

  /**
   * Los servicios que abren el curso (`RF-AC-037`): todas sus filas, también las de un producto que
   * `PM` retiró después — quien lo compró lo tiene hasta que venza (`RN-AC-020`).
   */
  static final String CUENTA_DE_SERVICIOS =
      "(SELECT count(*) FROM course_products s WHERE s.course_id = c.id)";

  /** Los módulos vivos del curso (`RF-AC-022`): un inactivo cuenta, un retirado no. */
  private static final String CUENTA_DE_MODULOS =
      "(SELECT count(*) FROM course_modules m WHERE m.course_id = c.id AND m.deleted_at IS NULL)";

  /**
   * Los módulos ofrecibles del curso (`RN-AC-015`): vivos, {@code ACTIVO} y con al menos una
   * lección ofrecible, sobre el fragmento {@link JpaCourseModuleQueryRepository#MODULO_OFRECIBLE}
   * que el aula reutiliza. Es la entrada de {@code CourseOfferability} y no la decisión.
   */
  static final String CUENTA_DE_MODULOS_OFRECIBLES =
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
          + CUENTA_DE_SERVICIOS
          + " AS product_count, "
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
    return findCategoriesOfCourses(List.of(courseId)).getOrDefault(courseId, List.of());
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, List<CategoryRef>> findCategoriesOfCourses(List<UUID> courseIds) {
    if (courseIds.isEmpty()) {
      return Map.of();
    }
    // UNA sentencia para toda la página (`CA-AC-128`). La categoría retirada no
    // vuelve: su fila de clasificación permanece (`RN-AC-010`), pero el JOIN la
    // descarta.
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT i.course_id AS course_id, k.id AS id, k.name AS name,
                       k.color AS color, k.icon AS icon
                  FROM course_category_items i
                  JOIN course_categories k ON k.id = i.category_id AND k.deleted_at IS NULL
                 WHERE i.course_id IN (:cursos)
                 ORDER BY k.display_order, k.id
                """,
                Tuple.class)
            .setParameter("cursos", courseIds)
            .getResultList();
    Map<UUID, List<CategoryRef>> porCurso = new LinkedHashMap<>();
    for (Tuple fila : filas) {
      porCurso
          .computeIfAbsent((UUID) fila.get("course_id"), curso -> new ArrayList<>())
          .add(
              new CategoryRef(
                  (UUID) fila.get("id"),
                  (String) fila.get("name"),
                  (String) fila.get("color"),
                  (String) fila.get("icon")));
    }
    return porCurso;
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
    // Cuatro columnas de `memberships` por lectura, como RF-PM-002: la regla
    // —que exista— cruzó por el puerto al escribir. En el orden de la cadena.
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT ms.id AS id, ms.code AS code, ms.name AS name, ms.color AS color
                  FROM course_memberships cm
                  JOIN memberships ms ON ms.id = cm.membership_id
                 WHERE cm.course_id = :curso
                 ORDER BY ms.level, ms.id
                """,
                Tuple.class)
            .setParameter("curso", courseId)
            .getResultList();
    return filas.stream()
        .map(
            fila ->
                new MembershipRef(
                    (UUID) fila.get("id"),
                    (String) fila.get("code"),
                    (String) fila.get("name"),
                    (String) fila.get("color")))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProductRef> findProductsOf(UUID courseId) {
    // Tres columnas de `products` por lectura, como las de `memberships`
    // (`RF-AC-037` §14.2): la regla cruzó por el puerto al escribir.
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT p.id AS id, p.code AS code, p.name AS name
                  FROM course_products s
                  JOIN products p ON p.id = s.product_id
                 WHERE s.course_id = :curso
                 ORDER BY p.code, p.id
                """,
                Tuple.class)
            .setParameter("curso", courseId)
            .getResultList();
    return filas.stream()
        .map(
            fila ->
                new ProductRef(
                    (UUID) fila.get("id"), (String) fila.get("code"), (String) fila.get("name")))
        .toList();
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
      // Solo la categoría VIVA acota: la retirada no sale en `categories` de
      // ninguna fila, y un filtro que devolviera cursos sin ella en su lista no
      // cuadraría con lo que la fila enseña. Filtrar por una retirada es vacío.
      donde.append(
          " AND EXISTS (SELECT 1 FROM course_category_items i JOIN course_categories k"
              + " ON k.id = i.category_id AND k.deleted_at IS NULL"
              + " WHERE i.course_id = c.id AND i.category_id = :categoria)");
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
    if (filtros.categoryId() != null) {
      consulta.setParameter("categoria", filtros.categoryId());
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
        ((Number) fila.get("product_count")).longValue(),
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
