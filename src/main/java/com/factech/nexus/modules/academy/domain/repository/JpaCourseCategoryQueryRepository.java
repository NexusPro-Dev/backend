package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.application.ListCourseCategoriesRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CourseCategoryQueryRepository} sobre SQL nativo.
 *
 * <p><b>Un solo bloque de columnas</b> ({@link #COLUMNAS}) para el detalle y el listado, con la
 * cuenta de cursos como columna más. La cuenta es hoy un literal: {@code course_category_items} no
 * existe hasta `RF-AC-016`, y ese requerimiento reemplaza {@link #CUENTA_DE_CURSOS} por la
 * subconsulta escalar sobre esa tabla unida a {@code courses} con {@code deleted_at IS NULL} —los
 * vivos, no los ofrecidos (`RF-AC-002` §14.2)—. Lo mismo con {@link #findAliveCoursesOf}, que hoy
 * devuelve vacío sin consultar nada.
 */
@Repository
public class JpaCourseCategoryQueryRepository implements CourseCategoryQueryRepository {

  /**
   * `RF-AC-016` la sustituye por: {@code (SELECT count(*) FROM course_category_items i JOIN courses
   * c ON c.id = i.course_id AND c.deleted_at IS NULL WHERE i.category_id = k.id)}.
   */
  private static final String CUENTA_DE_CURSOS = "0";

  private static final String COLUMNAS =
      """
      k.id AS id, k.name AS name, k.description AS description,
      k.color AS color, k.icon AS icon, k.display_order AS display_order,
      k.cover_image_id AS cover_image_id,
      """
          + CUENTA_DE_CURSOS
          + """
           AS course_count,
          k.created_at AS created_at, k.updated_at AS updated_at, k.deleted_at AS deleted_at
          """;

  private final EntityManager em;

  public JpaCourseCategoryQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CourseCategoryRow> findDetail(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT " + COLUMNAS + " FROM course_categories k WHERE k.id = :id", Tuple.class)
            .setParameter("id", id)
            .getResultList();
    return filas.stream().map(JpaCourseCategoryQueryRepository::categoria).findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public List<CategoryCourseRow> findAliveCoursesOf(UUID categoryId) {
    // Hasta `RF-AC-016` no hay tabla de clasificación que consultar: ni una
    // sentencia. Ese requerimiento escribe aquí el SELECT sobre
    // course_category_items JOIN courses (deleted_at IS NULL) ORDER BY
    // c.display_order, c.id, y el bloque 3 le añade la ofrecibilidad.
    return List.of();
  }

  @Override
  @Transactional(readOnly = true)
  public List<CourseCategoryRow> search(
      ListCourseCategoriesRequest filtros, String ordenamiento, int offset, int limit) {
    Query consulta =
        em.createNativeQuery(
            "SELECT "
                + COLUMNAS
                + " FROM course_categories k WHERE "
                + predicado(filtros)
                + " ORDER BY "
                + ordenamiento,
            Tuple.class);
    enlazar(consulta, filtros);
    List<Tuple> filas = consulta.setFirstResult(offset).setMaxResults(limit).getResultList();
    return filas.stream().map(JpaCourseCategoryQueryRepository::categoria).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long count(ListCourseCategoriesRequest filtros) {
    Query consulta =
        em.createNativeQuery(
            "SELECT count(*) FROM course_categories k WHERE " + predicado(filtros));
    enlazar(consulta, filtros);
    return ((Number) consulta.getSingleResult()).longValue();
  }

  private static String predicado(ListCourseCategoriesRequest filtros) {
    StringBuilder donde = new StringBuilder();
    donde.append(filtros.incluirEliminadas() ? "1 = 1" : "k.deleted_at IS NULL");
    if (filtros.q() != null) {
      donde.append(" AND f_unaccent(lower(k.name)) LIKE f_unaccent(lower(:termino)) ESCAPE '\\'");
    }
    return donde.toString();
  }

  private static void enlazar(Query consulta, ListCourseCategoriesRequest filtros) {
    if (filtros.q() != null) {
      consulta.setParameter("termino", "%" + escapar(filtros.q()) + "%");
    }
  }

  private static String escapar(String termino) {
    return termino.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static CourseCategoryRow categoria(Tuple fila) {
    return new CourseCategoryRow(
        (UUID) fila.get("id"),
        (String) fila.get("name"),
        (String) fila.get("description"),
        (String) fila.get("color"),
        (String) fila.get("icon"),
        ((Number) fila.get("display_order")).intValue(),
        (UUID) fila.get("cover_image_id"),
        ((Number) fila.get("course_count")).longValue(),
        momento(fila.get("created_at")),
        momento(fila.get("updated_at")),
        momento(fila.get("deleted_at")));
  }

  private static OffsetDateTime momento(Object valor) {
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
