package com.factech.nexus.modules.academy.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link LessonProgressRepository} sobre SQL nativo.
 *
 * <p><b>Las dos escrituras son una sentencia cada una</b>: {@code INSERT … ON CONFLICT DO UPDATE …
 * RETURNING}. {@code GREATEST} conserva el máximo y {@code COALESCE} la primera fecha de
 * completitud, de modo que dos reportes simultáneos no se pisan (`RF-AC-039` · plan §1).
 *
 * <p><b>«Ofrecible hoy» es el predicado del aula</b>: la lección de {@link
 * JpaCourseModuleQueryRepository#LECCION_OFRECIBLE} en un módulo activo y vivo. Un módulo así
 * tiene, por construcción, al menos una lección ofrecible —esta—, que es lo que le falta a ese
 * predicado para ser {@code MODULO_OFRECIBLE}.
 */
@Repository
public class JpaLessonProgressRepository implements LessonProgressRepository {

  private static final String DEVUELVE =
      " RETURNING lesson_id, watched_seconds, completed_at, first_opened_at, last_opened_at";

  private static final String ABRIR =
      """
      INSERT INTO lesson_progress
             (user_id, lesson_id, watched_seconds, completed_at, first_opened_at, last_opened_at)
      VALUES (:usuario, :leccion, 0, CASE WHEN CAST(:completa AS boolean) THEN now() END,
              now(), now())
      ON CONFLICT ON CONSTRAINT pk_lesson_progress DO UPDATE
         SET last_opened_at = now(),
             completed_at = COALESCE(lesson_progress.completed_at, EXCLUDED.completed_at)
      """
          + DEVUELVE;

  private static final String REPORTAR =
      """
      INSERT INTO lesson_progress
             (user_id, lesson_id, watched_seconds, completed_at, first_opened_at, last_opened_at)
      VALUES (:usuario, :leccion, :segundos, CASE WHEN :segundos >= :umbral THEN now() END,
              now(), now())
      ON CONFLICT ON CONSTRAINT pk_lesson_progress DO UPDATE
         SET watched_seconds = GREATEST(lesson_progress.watched_seconds,
                                        EXCLUDED.watched_seconds),
             completed_at = COALESCE(lesson_progress.completed_at,
                 CASE WHEN GREATEST(lesson_progress.watched_seconds, EXCLUDED.watched_seconds)
                           >= :umbral THEN now() END),
             last_opened_at = now()
      """
          + DEVUELVE;

  /** La lección ofrecible hoy, sobre los alias {@code l} y {@code m}. */
  private static final String OFRECIBLE =
      "("
          + JpaCourseModuleQueryRepository.LECCION_OFRECIBLE
          + " AND m.deleted_at IS NULL AND m.status = 'ACTIVO')";

  /** Lo que vale cada lección para el avance (`RN-AC-023`), sobre {@code ol} y {@code pp}. */
  private static final String CIFRAS =
      """
      count(ol.id) AS lesson_count,
      count(pp.completed_at) AS completed_lessons,
      COALESCE(sum(CASE WHEN pp.completed_at IS NOT NULL THEN ol.duration_seconds
                        ELSE LEAST(COALESCE(pp.watched_seconds, 0), ol.duration_seconds) END), 0)
          AS watched_seconds,
      COALESCE(sum(ol.duration_seconds), 0) AS total_seconds
      """;

  private static final String LECCIONES_OFRECIBLES =
      "(SELECT l.id, l.duration_seconds, m.course_id FROM lessons l"
          + " JOIN course_modules m ON m.id = l.module_id WHERE "
          + OFRECIBLE
          + ")";

  private final EntityManager em;

  public JpaLessonProgressRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional
  public ProgressRow open(UUID userId, UUID lessonId, boolean completa) {
    Tuple fila =
        (Tuple)
            em.createNativeQuery(ABRIR, Tuple.class)
                .setParameter("usuario", userId)
                .setParameter("leccion", lessonId)
                .setParameter("completa", completa)
                .getSingleResult();
    return progreso(fila);
  }

  @Override
  @Transactional
  public ProgressRow report(UUID userId, UUID lessonId, int segundos, int umbral) {
    Tuple fila =
        (Tuple)
            em.createNativeQuery(REPORTAR, Tuple.class)
                .setParameter("usuario", userId)
                .setParameter("leccion", lessonId)
                .setParameter("segundos", segundos)
                .setParameter("umbral", umbral)
                .getSingleResult();
    return progreso(fila);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, ProgressRow> findOfUserInLessons(UUID userId, Collection<UUID> lessonIds) {
    if (userId == null || lessonIds.isEmpty()) {
      return Map.of();
    }
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT lesson_id, watched_seconds, completed_at, first_opened_at, last_opened_at"
                    + " FROM lesson_progress WHERE user_id = :usuario AND lesson_id IN (:lecciones)",
                Tuple.class)
            .setParameter("usuario", userId)
            .setParameter("lecciones", lessonIds)
            .getResultList();
    Map<UUID, ProgressRow> porLeccion = new HashMap<>();
    filas.forEach(fila -> porLeccion.put((UUID) fila.get("lesson_id"), progreso(fila)));
    return porLeccion;
  }

  @Override
  @Transactional(readOnly = true)
  public List<CourseLessonProgressRow> findOfUserInCourse(UUID userId, UUID courseId) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT p.lesson_id AS lesson_id, p.watched_seconds AS watched_seconds,"
                    + " p.completed_at AS completed_at, p.first_opened_at AS first_opened_at,"
                    + " p.last_opened_at AS last_opened_at, l.module_id AS module_id,"
                    + " m.title AS module_title, l.title AS title, l.type AS type,"
                    + " l.duration_seconds AS duration_seconds, l.display_order AS display_order, "
                    + OFRECIBLE
                    + " AS offerable"
                    + " FROM lesson_progress p JOIN lessons l ON l.id = p.lesson_id"
                    + " JOIN course_modules m ON m.id = l.module_id"
                    + " WHERE p.user_id = :usuario AND m.course_id = :curso"
                    + " ORDER BY m.display_order, m.id, l.display_order, l.id",
                Tuple.class)
            .setParameter("usuario", userId)
            .setParameter("curso", courseId)
            .getResultList();
    return filas.stream()
        .map(
            fila ->
                new CourseLessonProgressRow(
                    progreso(fila),
                    (UUID) fila.get("module_id"),
                    (String) fila.get("module_title"),
                    (String) fila.get("title"),
                    (String) fila.get("type"),
                    ((Number) fila.get("duration_seconds")).intValue(),
                    ((Number) fila.get("display_order")).intValue(),
                    (Boolean) fila.get("offerable")))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, CourseFigures> figuresOfUser(UUID userId, Collection<UUID> courseIds) {
    if (userId == null || courseIds.isEmpty()) {
      return Map.of();
    }
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT ol.course_id AS course_id, "
                    + CIFRAS
                    + " FROM "
                    + LECCIONES_OFRECIBLES
                    + " ol LEFT JOIN lesson_progress pp ON pp.lesson_id = ol.id"
                    + " AND pp.user_id = :usuario"
                    + " WHERE ol.course_id IN (:cursos) GROUP BY ol.course_id",
                Tuple.class)
            .setParameter("usuario", userId)
            .setParameter("cursos", courseIds)
            .getResultList();
    Map<UUID, CourseFigures> porCurso = new HashMap<>();
    filas.forEach(fila -> porCurso.put((UUID) fila.get("course_id"), cifras(fila)));
    return porCurso;
  }

  @Override
  @Transactional(readOnly = true)
  public List<StudentCourseRow> search(StudentProgressFilter filtro, int offset, int size) {
    Map<String, Object> parametros = new LinkedHashMap<>();
    String sql =
        listado(filtro, parametros)
            + " ORDER BY f.last_activity_at DESC, f.user_id, f.course_id"
            + " OFFSET :desde LIMIT :cuantos";
    parametros.put("desde", offset);
    parametros.put("cuantos", size);
    Query consulta = em.createNativeQuery(sql, Tuple.class);
    parametros.forEach(consulta::setParameter);
    @SuppressWarnings("unchecked")
    List<Tuple> filas = consulta.getResultList();
    List<StudentCourseRow> pagina = new ArrayList<>();
    for (Tuple fila : filas) {
      pagina.add(
          new StudentCourseRow(
              (UUID) fila.get("user_id"),
              (UUID) fila.get("course_id"),
              (String) fila.get("course_title"),
              (String) fila.get("course_status"),
              (Boolean) fila.get("course_deleted"),
              cifras(fila),
              JpaCourseQueryRepository.momento(fila.get("first_opened_at")),
              JpaCourseQueryRepository.momento(fila.get("last_activity_at"))));
    }
    return pagina;
  }

  @Override
  @Transactional(readOnly = true)
  public long count(StudentProgressFilter filtro) {
    Map<String, Object> parametros = new LinkedHashMap<>();
    Query consulta =
        em.createNativeQuery("SELECT count(*) FROM (" + listado(filtro, parametros) + ") t");
    parametros.forEach(consulta::setParameter);
    return ((Number) consulta.getSingleResult()).longValue();
  }

  /**
   * El listado sin orden ni página: las filas alumno–curso con al menos una apertura dentro del
   * alcance, y sus cifras sobre lo ofrecible hoy. <b>El predicado se escribe por partes</b> y solo
   * se enlaza lo que se usa: un parámetro nulo sin tipo falla en PostgreSQL (`RF-MV-008`).
   */
  private static String listado(StudentProgressFilter filtro, Map<String, Object> parametros) {
    List<String> donde = new ArrayList<>();
    if (!filtro.everyone()) {
      List<String> alcance = new ArrayList<>();
      if (!filtro.people().isEmpty()) {
        alcance.add("p.user_id IN (:personas)");
        parametros.put("personas", filtro.people());
      }
      if (filtro.instructorId() != null) {
        alcance.add("c.instructor_id = :instructor");
        parametros.put("instructor", filtro.instructorId());
      }
      // Quien no alcanza a nadie no llega aquí (el servicio responde vacío); por si acaso, nada.
      donde.add(alcance.isEmpty() ? "false" : "(" + String.join(" OR ", alcance) + ")");
    }
    if (filtro.userId() != null) {
      donde.add("p.user_id = :usuario");
      parametros.put("usuario", filtro.userId());
    }
    if (filtro.courseId() != null) {
      donde.add("m.course_id = :curso");
      parametros.put("curso", filtro.courseId());
    }
    String terminado = "(k.lesson_count > 0 AND k.completed_lessons = k.lesson_count)";
    String alFinal =
        filtro.completed() == null
            ? ""
            : " WHERE " + (filtro.completed() ? terminado : "NOT " + terminado);
    return "WITH f AS (SELECT p.user_id, m.course_id,"
        + " min(p.first_opened_at) AS first_opened_at, max(p.last_opened_at) AS last_activity_at"
        + " FROM lesson_progress p JOIN lessons l ON l.id = p.lesson_id"
        + " JOIN course_modules m ON m.id = l.module_id JOIN courses c ON c.id = m.course_id"
        + (donde.isEmpty() ? "" : " WHERE " + String.join(" AND ", donde))
        + " GROUP BY p.user_id, m.course_id),"
        + " k AS (SELECT f.user_id, f.course_id, "
        + CIFRAS
        + " FROM f LEFT JOIN "
        + LECCIONES_OFRECIBLES
        + " ol ON ol.course_id = f.course_id"
        + " LEFT JOIN lesson_progress pp ON pp.lesson_id = ol.id AND pp.user_id = f.user_id"
        + " GROUP BY f.user_id, f.course_id)"
        + " SELECT f.user_id AS user_id, f.course_id AS course_id, c.title AS course_title,"
        + " c.status AS course_status, (c.deleted_at IS NOT NULL) AS course_deleted,"
        + " k.lesson_count AS lesson_count, k.completed_lessons AS completed_lessons,"
        + " k.watched_seconds AS watched_seconds, k.total_seconds AS total_seconds,"
        + " f.first_opened_at AS first_opened_at, f.last_activity_at AS last_activity_at"
        + " FROM f JOIN k ON k.user_id = f.user_id AND k.course_id = f.course_id"
        + " JOIN courses c ON c.id = f.course_id"
        + alFinal;
  }

  private static ProgressRow progreso(Tuple fila) {
    return new ProgressRow(
        (UUID) fila.get("lesson_id"),
        ((Number) fila.get("watched_seconds")).intValue(),
        JpaCourseQueryRepository.momento(fila.get("completed_at")),
        JpaCourseQueryRepository.momento(fila.get("first_opened_at")),
        JpaCourseQueryRepository.momento(fila.get("last_opened_at")));
  }

  private static CourseFigures cifras(Tuple fila) {
    return new CourseFigures(
        ((Number) fila.get("lesson_count")).longValue(),
        ((Number) fila.get("completed_lessons")).longValue(),
        ((Number) fila.get("watched_seconds")).longValue(),
        ((Number) fila.get("total_seconds")).longValue());
  }
}
