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
 * <p><b>El producto y el rol llegan resueltos en la MISMA sentencia</b>, con {@code LEFT JOIN}.
 * Resolverlos fila a fila contra las interfaces de `PM` y `SP` sería el problema de las {@code N+1}
 * consultas —cien tasas, doscientas llamadas—, y eso no lo arregla que las llamadas sean a un
 * puerto en lugar de a una tabla.
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
   * Desde el 15-09-2026 el producto es una columna de la propia tasa (`RN-CM-021`) y se une como el
   * rol: una fila por tasa, sin subconsulta que contar ni producto cartesiano que agrupar.
   */
  private static final String COLUMNAS =
      """
      t.id AS id,
      t.product_id AS product_id, p.code AS product_code, p.name AS product_name,
      p.price AS product_price,
      p.currency_id AS currency_id, c.code AS currency_code, c.decimal_places AS decimal_places,
      t.role_id AS role_id, r.code AS role_code, r.name AS role_name,
      t.rate_type AS rate_type, t.percentage AS percentage, t.fixed_amount AS fixed_amount,
      t.deleted_at AS deleted_at
      """;

  /**
   * <b>El orden del catálogo, y por qué ya no puede ordenar por «lo que más paga».</b>
   *
   * <p>Desde el 15-09-2026 va <b>primero por producto</b>: el catálogo se lee como «qué paga cada
   * producto» (`RN-CM-021`), y dentro de cada producto, por rol.
   *
   * <p>Hasta el 02-09-2026 era {@code r.code ASC, t.percentage DESC}: dentro de cada rol, arriba lo
   * que más paga. Con dos formas eso <b>compara cosas que no admiten un «mayor que»</b> — cuál paga
   * más entre «10 %» y «10.000 fijos» depende del precio del producto, que este listado no cruza.
   *
   * <p>Ordenar por la cifra a secas sería <b>peor que no ordenar</b>: produciría una lista que
   * <b>parece</b> de mayor a menor sin serlo, poniendo «100 fijos» por encima de «50 %».
   *
   * <p><b>El {@code COALESCE} solo es correcto porque va DETRÁS de {@code rate_type}.</b> Dentro de
   * un grupo ya no hay más que una de las dos columnas llena, de modo que la fusión no compara nada
   * heterogéneo. Movido delante —o sin {@code rate_type} en medio— haría exactamente lo que este
   * comentario existe para impedir, <b>y sin ningún síntoma</b>: la consulta funciona, la página se
   * llena, y el orden miente.
   *
   * <p><b>{@code rate_type ASC} ordena alfabéticamente</b>, de modo que {@code FIJO} va antes que
   * {@code PORCENTAJE} por el alfabeto y no porque nadie lo haya decidido. Es arbitrario y estable,
   * que es todo lo que hace falta: lo que importa es que los dos grupos <b>no se intercalen</b>.
   */
  private static final String ORDEN =
      " ORDER BY p.code ASC, r.code ASC, t.rate_type ASC,"
          + " COALESCE(t.percentage, t.fixed_amount) DESC, t.id DESC";

  private static final String TABLAS =
      """
      commission_rates t
      LEFT JOIN products   p ON p.id = t.product_id
      LEFT JOIN currencies c ON c.id = p.currency_id
      LEFT JOIN roles      r ON r.id = t.role_id
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
                + ORDEN
                + " OFFSET :salto LIMIT :tope",
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
    filtro.igual("t.product_id", "producto", f.productId());
    filtro.igual("t.role_id", "rol", f.roleId());
    // El filtro por forma viaja como texto y no como enum: la columna es
    // `varchar(20)` y el driver no tiene por qué saber traducir el tipo de Java.
    filtro.igual("t.rate_type", "forma", f.rateType() == null ? null : f.rateType().name());
    if (!f.includeDeleted()) {
      filtro.crudo("t.deleted_at IS NULL");
    }
    return filtro;
  }

  private static RateRow comoFila(Tuple fila) {
    return new RateRow(
        (UUID) fila.get("id"),
        (UUID) fila.get("product_id"),
        (String) fila.get("product_code"),
        (String) fila.get("product_name"),
        (BigDecimal) fila.get("product_price"),
        (UUID) fila.get("currency_id"),
        (String) fila.get("currency_code"),
        ((Number) fila.get("decimal_places")).intValue(),
        (UUID) fila.get("role_id"),
        (String) fila.get("role_code"),
        (String) fila.get("role_name"),
        CommissionRows.forma(fila.get("rate_type")),
        (BigDecimal) fila.get("percentage"),
        (BigDecimal) fila.get("fixed_amount"),
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
