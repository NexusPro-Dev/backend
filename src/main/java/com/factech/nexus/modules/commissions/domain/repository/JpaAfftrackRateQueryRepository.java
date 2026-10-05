package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.shared.persistence.MinorUnits;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de {@link AfftrackRateQueryRepository}: una sentencia con {@code JOIN} de lectura a
 * {@code products}, {@code currencies} y {@code roles}, el precedente de {@link
 * JpaCommissionRateQueryRepository}.
 */
@Repository
public class JpaAfftrackRateQueryRepository implements AfftrackRateQueryRepository {

  private static final String COLUMNAS =
      """
      e.id AS id,
      e.product_id AS product_id, p.code AS product_code, p.name AS product_name,
      p.currency_id AS currency_id, c.code AS currency_code, c.decimal_places AS decimal_places,
      e.role_id AS role_id, r.code AS role_code, r.name AS role_name,
      e.threshold AS threshold, e.amount_per_ftd AS amount_per_ftd,
      e.threshold * e.amount_per_ftd AS amount_at_threshold,
      e.created_at AS created_at, e.deleted_at AS deleted_at
      """;

  private static final String TABLAS =
      """
      afftrack_rates e
      LEFT JOIN products   p ON p.id = e.product_id
      LEFT JOIN currencies c ON c.id = p.currency_id
      LEFT JOIN roles      r ON r.id = e.role_id
      """;

  /** La escala junta y en el orden en que se alcanza (`spec.md` §2.1). */
  private static final String ORDEN =
      " ORDER BY p.code ASC, r.code ASC, e.threshold ASC, e.id DESC";

  private final EntityManager em;

  public JpaAfftrackRateQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AfftrackRateRow> search(AfftrackRateFilters filtros, int offset, int limit) {
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
    List<AfftrackRateRow> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(comoFila(fila));
    }
    return resultado;
  }

  @Override
  @Transactional(readOnly = true)
  public long count(AfftrackRateFilters filtros) {
    Filtro filtro = predicado(filtros);
    Query consulta =
        em.createNativeQuery("SELECT count(*) FROM " + TABLAS + " WHERE " + filtro.sql());
    filtro.enlazar(consulta);
    return ((Number) consulta.getSingleResult()).longValue();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AfftrackRateRow> findRow(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createNativeQuery(
            "SELECT " + COLUMNAS + " FROM " + TABLAS + " WHERE e.id = :id", Tuple.class)
        .setParameter("id", id)
        .getResultList()
        .stream()
        .findFirst()
        .map(fila -> comoFila((Tuple) fila));
  }

  private static Filtro predicado(AfftrackRateFilters f) {
    Filtro filtro = new Filtro();
    filtro.igual("e.product_id", "producto", f.productId());
    filtro.igual("e.role_id", "rol", f.roleId());
    if (!f.includeDeleted()) {
      filtro.crudo("e.deleted_at IS NULL");
    }
    return filtro;
  }

  private static AfftrackRateRow comoFila(Tuple fila) {
    return new AfftrackRateRow(
        (UUID) fila.get("id"),
        (UUID) fila.get("product_id"),
        (String) fila.get("product_code"),
        (String) fila.get("product_name"),
        (UUID) fila.get("currency_id"),
        (String) fila.get("currency_code"),
        ((Number) fila.get("decimal_places")).intValue(),
        (UUID) fila.get("role_id"),
        (String) fila.get("role_code"),
        (String) fila.get("role_name"),
        ((Number) fila.get("threshold")).intValue(),
        MinorUnits.fromMinor(fila.get("amount_per_ftd")),
        MinorUnits.fromMinor(fila.get("amount_at_threshold")),
        CommissionRows.momento(fila.get("created_at")),
        CommissionRows.momento(fila.get("deleted_at")));
  }

  private static final class Filtro {

    private final StringBuilder donde = new StringBuilder("1 = 1");
    private final Map<String, Object> parametros = new LinkedHashMap<>();

    void crudo(String sql) {
      donde.append(" AND ").append(sql);
    }

    void igual(String columna, String nombre, Object valor) {
      if (valor != null) {
        donde.append(" AND (").append(columna).append(" = :").append(nombre).append(")");
        parametros.put(nombre, valor);
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
