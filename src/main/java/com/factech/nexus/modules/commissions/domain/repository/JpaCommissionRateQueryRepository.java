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
 * Adaptador de {@link CommissionRateQueryRepository}.
 *
 * <p><b>El rol llega resuelto en la MISMA sentencia</b>, con {@code LEFT JOIN}. Resolverlo fila a
 * fila contra la interfaz de `SP` sería el problema de las {@code N+1} consultas —cien tasas, cien
 * llamadas—, y eso no lo arregla que las llamadas sean a un puerto en lugar de a una tabla.
 *
 * <p><b>Y no rompe la frontera de D-25</b>: lo que `modules.md` §7 defiende es la frontera del
 * <b>código</b> —esta clase no importa repositorios ni entidades de otro módulo—, mientras que el
 * {@code JOIN} y las claves foráneas son integridad y lectura declaradas en el motor.
 *
 * <p><b>El predicado vive en un solo método</b> a propósito: el día que D-22 obligue a añadir el
 * alcance del actor, hay uno que tocar y no tres.
 */
@Repository
public class JpaCommissionRateQueryRepository implements CommissionRateQueryRepository {

  /**
   * La cuenta de asociaciones va como subconsulta correlacionada y no como {@code JOIN} agrupado.
   *
   * <p>Con un {@code LEFT JOIN} sobre la asociación, cada tasa aparecería una vez por producto y el
   * listado tendría que agrupar — y el {@code LIMIT} de la paginación se aplicaría a las filas del
   * producto cartesiano y no a las tasas, devolviendo <b>menos tasas de las pedidas</b> sin que
   * nada fallara.
   */
  private static final String COLUMNAS =
      """
      t.id AS id, t.role_id AS role_id, r.code AS role_code, r.name AS role_name,
      t.percentage AS percentage,
      (SELECT count(*) FROM product_commission_rates a
        WHERE a.commission_rate_id = t.id) AS asociados,
      t.deleted_at AS deleted_at
      """;

  private static final String TABLAS =
      """
      commission_rates t
      LEFT JOIN roles r ON r.id = t.role_id
      """;

  private final EntityManager em;

  public JpaCommissionRateQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<RateRow> search(RateFilters filtros, int offset, int limit) {
    Filtro filtro = predicado(filtros);

    Query consulta =
        em.createNativeQuery(
            "SELECT "
                + COLUMNAS
                + " FROM "
                + TABLAS
                + " WHERE "
                + filtro.sql()
                + " ORDER BY r.code ASC, t.percentage DESC, t.id DESC OFFSET :salto LIMIT :tope",
            Tuple.class);

    filtro.enlazar(consulta);
    consulta.setParameter("salto", offset).setParameter("tope", limit);

    List<Tuple> filas = consulta.getResultList();
    List<RateRow> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(comoFila(fila));
    }
    return resultado;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<RateRow> findRow(UUID id) {
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
  public long count(RateFilters filtros) {
    Filtro filtro = predicado(filtros);
    Query consulta =
        em.createNativeQuery("SELECT count(*) FROM " + TABLAS + " WHERE " + filtro.sql());
    filtro.enlazar(consulta);
    return ((Number) consulta.getSingleResult()).longValue();
  }

  private static Filtro predicado(RateFilters f) {
    Filtro filtro = new Filtro();
    filtro.igual("t.role_id", "rol", f.roleId());
    if (!f.includeDeleted()) {
      filtro.crudo("t.deleted_at IS NULL");
    }
    return filtro;
  }

  private static RateRow comoFila(Tuple fila) {
    return new RateRow(
        (UUID) fila.get("id"),
        (UUID) fila.get("role_id"),
        (String) fila.get("role_code"),
        (String) fila.get("role_name"),
        (BigDecimal) fila.get("percentage"),
        ((Number) fila.get("asociados")).longValue(),
        CommissionRows.momento(fila.get("deleted_at")));
  }

  /** Predicado y parámetros, construidos a la vez, como en la auditoría. */
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
