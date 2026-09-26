package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.application.ListCourseCategoriesRequest;
import com.factech.nexus.modules.academy.domain.models.CourseOfferability;
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
 * cuenta de cursos como columna más: la subconsulta escalar {@link #CUENTA_DE_CURSOS} sobre {@code
 * course_category_items} unida a {@code courses} con {@code deleted_at IS NULL} —los vivos, no los
 * ofrecidos (`RF-AC-002` §14.2)—, real desde `RF-AC-016`, que también llenó {@link
 * #findAliveCoursesOf}.
 */
@Repository
public class JpaCourseCategoryQueryRepository implements CourseCategoryQueryRepository {

  /**
   * Los cursos vivos clasificados en la categoría (`RF-AC-016`, `CA-AC-127`): un inactivo cuenta,
   * un retirado no. Los vivos y no los ofrecidos (`RF-AC-002` §14.2).
   */
  private static final String CUENTA_DE_CURSOS =
      "(SELECT count(*) FROM course_category_items i JOIN courses c ON c.id = i.course_id"
          + " AND c.deleted_at IS NULL WHERE i.category_id = k.id)";

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
  public List<CourseCategoryRow> findAlive() {
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT "
                    + COLUMNAS
                    + " FROM course_categories k WHERE k.deleted_at IS NULL"
                    + " ORDER BY k.display_order, k.id",
                Tuple.class)
            .getResultList();
    return filas.stream().map(JpaCourseCategoryQueryRepository::categoria).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<CategoryCourseRow> findAliveCoursesOf(UUID categoryId) {
    // UNA sentencia, con las cuentas que `CourseOfferability` necesita como
    // columnas —las mismas subconsultas que el listado de cursos, sobre el
    // mismo alias `c`—, para decidir `offerable` por fila en Java.
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT c.id AS id, c.title AS title, c.status AS status,"
                    + " c.display_order AS display_order,"
                    + " c.short_description IS NOT NULL AS tiene_corta,"
                    + " c.long_description IS NOT NULL AS tiene_larga, "
                    + JpaCourseQueryRepository.CUENTA_DE_MODULOS_OFRECIBLES
                    + " AS offerable_module_count"
                    + " FROM course_category_items i"
                    + " JOIN courses c ON c.id = i.course_id AND c.deleted_at IS NULL"
                    + " WHERE i.category_id = :categoria"
                    + " ORDER BY c.display_order, c.id",
                Tuple.class)
            .setParameter("categoria", categoryId)
            .getResultList();
    return filas.stream()
        .map(
            fila ->
                new CategoryCourseRow(
                    (UUID) fila.get("id"),
                    (String) fila.get("title"),
                    (String) fila.get("status"),
                    ((Number) fila.get("display_order")).intValue(),
                    CourseOfferability.decidir(
                            false,
                            (String) fila.get("status"),
                            (Boolean) fila.get("tiene_corta"),
                            (Boolean) fila.get("tiene_larga"),
                            ((Number) fila.get("offerable_module_count")).longValue())
                        .offerable()))
        .toList();
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
