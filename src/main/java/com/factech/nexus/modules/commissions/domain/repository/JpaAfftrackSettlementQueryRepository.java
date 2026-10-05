package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.shared.persistence.MinorUnits;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de {@link AfftrackSettlementQueryRepository}: una sentencia con {@code JOIN} de
 * lectura, como {@link JpaCommissionAccrualQueryRepository}. Los FTD propios y de la red salen de
 * <b>una subconsulta agregada</b> unida por la liquidación, no correlacionada fila a fila.
 */
@Repository
public class JpaAfftrackSettlementQueryRepository implements AfftrackSettlementQueryRepository {

  private static final String COLUMNAS =
      """
      s.id AS id, s.closing_id AS closing_id, cl.closed_at AS closed_at,
      s.user_id AS user_id, us.username AS username,
      us.first_name AS user_nombre, us.last_name AS user_apellido,
      s.product_id AS product_id, p.code AS product_code, p.name AS product_name,
      p.currency_id AS currency_id, cu.code AS currency_code, cu.decimal_places AS decimal_places,
      s.carried_in AS carried_in, s.new_ftds AS new_ftds,
      COALESCE(f.propios, 0) AS own_ftds, COALESCE(f.de_la_red, 0) AS network_ftds,
      s.paid_ftds AS paid_ftds, s.carried_out AS carried_out, s.source AS source,
      k.fixed_amount AS amount_per_ftd, k.commission_amount AS amount
      """;

  private static final String TABLAS =
      """
      afftrack_settlements s
      JOIN commission_closings cl ON cl.id = s.closing_id
      LEFT JOIN users      us ON us.id = s.user_id
      LEFT JOIN products   p  ON p.id = s.product_id
      LEFT JOIN currencies cu ON cu.id = p.currency_id
      LEFT JOIN commissions k ON k.afftrack_settlement_id = s.id
      LEFT JOIN (SELECT settlement_id,
                        count(*) FILTER (WHERE chain_level = 0) AS propios,
                        count(*) FILTER (WHERE chain_level > 0) AS de_la_red
                   FROM afftrack_ftds GROUP BY settlement_id) f ON f.settlement_id = s.id
      """;

  private static final String ORDEN =
      " ORDER BY cl.closed_at DESC NULLS LAST, s.created_at DESC, us.username ASC, p.code ASC,"
          + " s.id DESC";

  private final EntityManager em;

  public JpaAfftrackSettlementQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<SettlementRow> search(SettlementFilter filtro, int offset, int limit) {
    Filtro f = predicado(filtro);
    Query consulta =
        em.createNativeQuery(
            "SELECT "
                + COLUMNAS
                + " FROM "
                + TABLAS
                + " WHERE "
                + f.sql()
                + ORDEN
                + " OFFSET :salto LIMIT :tope",
            Tuple.class);
    f.enlazar(consulta);
    consulta.setParameter("salto", offset).setParameter("tope", limit);
    List<Tuple> filas = consulta.getResultList();
    List<SettlementRow> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(comoFila(fila));
    }
    return resultado;
  }

  @Override
  @Transactional(readOnly = true)
  public long count(SettlementFilter filtro) {
    Filtro f = predicado(filtro);
    Query consulta =
        em.createNativeQuery(
            "SELECT count(*) FROM afftrack_settlements s"
                + " JOIN commission_closings cl ON cl.id = s.closing_id WHERE "
                + f.sql());
    f.enlazar(consulta);
    return ((Number) consulta.getSingleResult()).longValue();
  }

  private static Filtro predicado(SettlementFilter filtro) {
    Filtro f = new Filtro();
    f.igual("s.user_id", "persona", filtro.userId());
    f.igual("s.product_id", "producto", filtro.productId());
    f.igual("s.closing_id", "cierre", filtro.closingId());
    if (filtro.from() != null) {
      f.condicion("cl.closed_at >= :desde", "desde", filtro.from());
    }
    if (filtro.to() != null) {
      f.condicion("cl.closed_at <= :hasta", "hasta", filtro.to());
    }
    if (filtro.paid() != null) {
      f.crudo(filtro.paid() ? "s.paid_ftds > 0" : "s.paid_ftds = 0");
    }
    return f;
  }

  private static SettlementRow comoFila(Tuple fila) {
    return new SettlementRow(
        (UUID) fila.get("id"),
        (UUID) fila.get("closing_id"),
        CommissionRows.momento(fila.get("closed_at")),
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
        ((Number) fila.get("carried_in")).intValue(),
        ((Number) fila.get("new_ftds")).intValue(),
        ((Number) fila.get("own_ftds")).intValue(),
        ((Number) fila.get("network_ftds")).intValue(),
        ((Number) fila.get("paid_ftds")).intValue(),
        ((Number) fila.get("carried_out")).intValue(),
        (String) fila.get("source"),
        MinorUnits.fromMinor(fila.get("amount_per_ftd")),
        MinorUnits.fromMinor(fila.get("amount")));
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
