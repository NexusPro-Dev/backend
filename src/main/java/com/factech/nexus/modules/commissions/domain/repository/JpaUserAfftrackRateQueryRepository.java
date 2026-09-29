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

/**
 * Adaptador de {@link UserAfftrackRateQueryRepository}: una sentencia con {@code JOIN} de lectura a
 * {@code users}, {@code products} y {@code currencies}, como {@link
 * JpaUserCommissionRateQueryRepository}.
 */
@Repository
public class JpaUserAfftrackRateQueryRepository implements UserAfftrackRateQueryRepository {

  private static final String COLUMNAS =
      """
      u.id AS id, u.user_id AS user_id, us.username AS username,
      us.first_name AS user_nombre, us.last_name AS user_apellido,
      u.product_id AS product_id, p.code AS product_code, p.name AS product_name,
      p.currency_id AS currency_id, c.code AS currency_code, c.decimal_places AS decimal_places,
      u.threshold AS threshold, u.amount_per_ftd AS amount_per_ftd,
      u.threshold * u.amount_per_ftd AS amount_at_threshold,
      u.valid_from AS valid_from, u.valid_to AS valid_to,
      u.created_at AS created_at, u.deleted_at AS deleted_at
      """;

  private static final String TABLAS =
      """
      user_afftrack_rates u
      LEFT JOIN users      us ON us.id = u.user_id
      LEFT JOIN products   p  ON p.id = u.product_id
      LEFT JOIN currencies c  ON c.id = p.currency_id
      """;

  private static final String ORDEN =
      " ORDER BY us.username ASC, p.code ASC, u.threshold ASC, u.valid_from DESC, u.id DESC";

  private final EntityManager em;

  public JpaUserAfftrackRateQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserAfftrackRateRow> search(UserAfftrackRateFilters filtros, int offset, int limit) {
    Filtro filtro = predicado(filtros);
    Query consulta =
        em.createNativeQuery(
            "SELECT "
                + COLUMNAS
                + " FROM "
                + TABLAS
                + " WHERE "
                + filtro.sql()
                + ORDEN
                + " OFFSET :salto LIMIT :tope",
            Tuple.class);
    filtro.enlazar(consulta);
    consulta.setParameter("salto", offset).setParameter("tope", limit);
    List<Tuple> filas = consulta.getResultList();
    List<UserAfftrackRateRow> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(comoFila(fila));
    }
    return resultado;
  }

  @Override
  @Transactional(readOnly = true)
  public long count(UserAfftrackRateFilters filtros) {
    Filtro filtro = predicado(filtros);
    Query consulta =
        em.createNativeQuery("SELECT count(*) FROM " + TABLAS + " WHERE " + filtro.sql());
    filtro.enlazar(consulta);
    return ((Number) consulta.getSingleResult()).longValue();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserAfftrackRateRow> findRow(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createNativeQuery(
            "SELECT " + COLUMNAS + " FROM " + TABLAS + " WHERE u.id = :id", Tuple.class)
        .setParameter("id", id)
        .getResultList()
        .stream()
        .findFirst()
        .map(fila -> comoFila((Tuple) fila));
  }

  private static Filtro predicado(UserAfftrackRateFilters f) {
    Filtro filtro = new Filtro();
    filtro.igual("u.user_id", "persona", f.userId());
    filtro.igual("u.product_id", "producto", f.productId());
    if (f.onDate() != null) {
      // El predicado del cierre, no una copia: `CA-CM-236`.
      filtro.condicion(AfftrackSql.VIGENTE_EN, "dia", f.onDate().toString());
    } else if (!f.includeDeleted()) {
      filtro.crudo("u.deleted_at IS NULL");
    }
    return filtro;
  }

  private static UserAfftrackRateRow comoFila(Tuple fila) {
    return new UserAfftrackRateRow(
        (UUID) fila.get("id"),
        (UUID) fila.get("user_id"),
        (String) fila.get("username"),
        CommissionRows.nombreCompleto(
            (String) fila.get("user_nombre"), (String) fila.get("user_apellido")),
        (UUID) fila.get("product_id"),
        (String) fila.get("product_code"),
        (String) fila.get("product_name"),
        (UUID) fila.get("currency_id"),
        (String) fila.get("currency_code"),
        ((Number) fila.get("decimal_places")).intValue(),
        ((Number) fila.get("threshold")).intValue(),
        (BigDecimal) fila.get("amount_per_ftd"),
        (BigDecimal) fila.get("amount_at_threshold"),
        CommissionRows.fecha(fila.get("valid_from")),
        CommissionRows.fecha(fila.get("valid_to")),
        CommissionRows.momento(fila.get("created_at")),
        CommissionRows.momento(fila.get("deleted_at")));
  }

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
