package com.factech.nexus.modules.commissions.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de {@link UserCommissionRateQueryRepository}. */
@Repository
public class JpaUserCommissionRateQueryRepository implements UserCommissionRateQueryRepository {

  private static final String COLUMNAS =
      """
      t.id AS id, t.user_id AS user_id, u.username AS username,
      u.first_name AS user_nombre, u.last_name AS user_apellido,
      t.percentage AS percentage, t.valid_from AS valid_from, t.valid_to AS valid_to,
      t.deleted_at AS deleted_at
      """;

  private static final String TABLAS =
      """
      user_commission_rates t
      LEFT JOIN users u ON u.id = t.user_id
      """;

  private final EntityManager em;

  public JpaUserCommissionRateQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserRateRow> search(UserRateFilters filtros, int offset, int limit) {
    Filtro filtro = predicado(filtros);

    Query consulta =
        em.createNativeQuery(
            "SELECT "
                + COLUMNAS
                + " FROM "
                + TABLAS
                + " WHERE "
                + filtro.sql()
                + " ORDER BY t.valid_from DESC, t.id DESC OFFSET :salto LIMIT :tope",
            Tuple.class);

    filtro.enlazar(consulta);
    consulta.setParameter("salto", offset).setParameter("tope", limit);

    List<Tuple> filas = consulta.getResultList();
    List<UserRateRow> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(comoFila(fila));
    }
    return resultado;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserRateRow> findRow(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createNativeQuery(
            "SELECT " + COLUMNAS + " FROM " + TABLAS + " WHERE t.id = :id", Tuple.class)
        .setParameter("id", id)
        .getResultList()
        .stream()
        .findFirst()
        .map(fila -> comoFila((Tuple) fila));
  }

  @Override
  @Transactional(readOnly = true)
  public long count(UserRateFilters filtros) {
    Filtro filtro = predicado(filtros);
    Query consulta =
        em.createNativeQuery("SELECT count(*) FROM " + TABLAS + " WHERE " + filtro.sql());
    filtro.enlazar(consulta);
    return ((Number) consulta.getSingleResult()).longValue();
  }

  private static Filtro predicado(UserRateFilters f) {
    Filtro filtro = new Filtro();
    filtro.igual("t.user_id", "persona", f.userId());

    if (!f.includeDeleted()) {
      filtro.crudo("t.deleted_at IS NULL");
    }
    if (f.onDate() != null) {
      // Pertenencia, no igualdad: «las que rigen ese día».
      filtro.condicion(
          "t.valid_from <= CAST(:fecha AS date)"
              + " AND (t.valid_to IS NULL OR t.valid_to >= CAST(:fecha AS date))",
          "fecha",
          f.onDate().toString());
    }
    return filtro;
  }

  private static UserRateRow comoFila(Tuple fila) {
    return new UserRateRow(
        (UUID) fila.get("id"),
        (UUID) fila.get("user_id"),
        (String) fila.get("username"),
        CommissionRows.nombreCompleto(
            (String) fila.get("user_nombre"), (String) fila.get("user_apellido")),
        (BigDecimal) fila.get("percentage"),
        CommissionRows.fecha(fila.get("valid_from")),
        CommissionRows.fecha(fila.get("valid_to")),
        CommissionRows.momento(fila.get("deleted_at")));
  }

  /** Predicado y parámetros, construidos a la vez. */
  private static final class Filtro {

    private final StringBuilder donde = new StringBuilder("1 = 1");
    private final Map<String, Object> parametros = new LinkedHashMap<>();

    void crudo(String sql) {
      donde.append(" AND ").append(sql);
    }

    void condicion(String sql, String nombre, Object valor) {
      donde.append(" AND (").append(sql).append(")");
      parametros.put(nombre, valor);
    }

    void igual(String columna, String nombre, Object valor) {
      if (valor != null) {
        condicion(columna + " = :" + nombre, nombre, valor);
      }
    }

    String sql() {
      return donde.toString();
    }

    void enlazar(Query consulta) {
      parametros.forEach(consulta::setParameter);
    }
  }
}
