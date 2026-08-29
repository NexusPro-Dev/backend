package com.factech.nexus.modules.commissions.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
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
 * Adaptador de {@link CommissionRateQueryRepository}.
 *
 * <p><b>El rol, el producto y la persona llegan resueltos en la MISMA sentencia</b>, con {@code
 * LEFT JOIN}. Resolverlos fila a fila contra las interfaces de `SP` y `PM` sería el problema de las
 * {@code N+1} consultas —cien tarifas, trescientas llamadas—, y eso no lo arregla que las llamadas
 * sean a un puerto en lugar de a una tabla.
 *
 * <p><b>Y no rompe la frontera de D-25</b>: lo que `modules.md` §7 defiende es la frontera del
 * <b>código</b> —esta clase no importa repositorios ni entidades de otro módulo—, mientras que el
 * {@code JOIN} y las claves foráneas son integridad y lectura declaradas en el motor, que es donde
 * `PM` ya se apoya para hablar de `SP`.
 *
 * <p><b>El predicado vive en un solo método</b> a propósito: el día que D-22 obligue a añadir el
 * alcance del actor, hay uno que tocar y no tres.
 */
@Repository
public class JpaCommissionRateQueryRepository implements CommissionRateQueryRepository {

  private static final String COLUMNAS =
      """
      t.id AS id, t.role_id AS role_id, r.code AS role_code, r.name AS role_name,
      t.product_id AS product_id, p.code AS product_code, p.name AS product_name,
      t.user_id AS user_id, u.username AS username,
      u.first_name AS user_nombre, u.last_name AS user_apellido,
      t.percentage AS percentage, t.valid_from AS valid_from, t.valid_to AS valid_to,
      t.deleted_at AS deleted_at
      """;

  private static final String TABLAS =
      """
      commission_rates t
      LEFT JOIN roles    r ON r.id = t.role_id
      LEFT JOIN products p ON p.id = t.product_id
      LEFT JOIN users    u ON u.id = t.user_id
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
                + " ORDER BY t.valid_from DESC, t.id DESC OFFSET :salto LIMIT :tope",
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

  /**
   * `RN-CM-004`, escrita una vez.
   *
   * <p><b>El orden es la regla.</b> La persona pesa más que el producto, y por eso su criterio va
   * primero: una excepción de persona sin producto gana a una tarifa de producto sin persona.
   *
   * <p>El predicado admite las tarifas del grado más general —{@code product_id IS NULL}, {@code
   * user_id IS NULL}— junto a las específicas, y el {@code ORDER BY} decide cuál sobrevive al
   * {@code LIMIT 1}.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<RateRow> resolve(UUID roleId, UUID productId, UUID userId, LocalDate fecha) {
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT "
                    + COLUMNAS
                    + " FROM "
                    + TABLAS
                    + """
                     WHERE t.deleted_at IS NULL
                       AND t.role_id = :rol
                       AND t.valid_from <= CAST(:fecha AS date)
                       AND (t.valid_to IS NULL OR t.valid_to >= CAST(:fecha AS date))
                       AND (t.product_id = CAST(:producto AS uuid) OR t.product_id IS NULL)
                       AND (t.user_id    = CAST(:persona  AS uuid) OR t.user_id    IS NULL)
                     ORDER BY (t.user_id IS NOT NULL) DESC, (t.product_id IS NOT NULL) DESC
                     LIMIT 1
                    """,
                Tuple.class)
            .setParameter("rol", roleId)
            .setParameter("fecha", fecha.toString())
            .setParameter("producto", productId == null ? null : productId.toString())
            .setParameter("persona", userId == null ? null : userId.toString())
            .getResultList();

    return filas.stream().findFirst().map(JpaCommissionRateQueryRepository::comoFila);
  }

  private static Filtro predicado(RateFilters f) {
    Filtro filtro = new Filtro();
    filtro.igual("t.role_id", "rol", f.roleId());
    filtro.igual("t.product_id", "producto", f.productId());
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

  private static RateRow comoFila(Tuple fila) {
    return new RateRow(
        (UUID) fila.get("id"),
        (UUID) fila.get("role_id"),
        (String) fila.get("role_code"),
        (String) fila.get("role_name"),
        (UUID) fila.get("product_id"),
        (String) fila.get("product_code"),
        (String) fila.get("product_name"),
        (UUID) fila.get("user_id"),
        (String) fila.get("username"),
        nombreCompleto((String) fila.get("user_nombre"), (String) fila.get("user_apellido")),
        (BigDecimal) fila.get("percentage"),
        fecha(fila.get("valid_from")),
        fecha(fila.get("valid_to")),
        momento(fila.get("deleted_at")));
  }

  private static String nombreCompleto(String nombre, String apellido) {
    if (nombre == null && apellido == null) {
      return null;
    }
    String completo =
        ((nombre == null ? "" : nombre) + " " + (apellido == null ? "" : apellido)).trim();
    return completo.isEmpty() ? null : completo;
  }

  private static LocalDate fecha(Object valor) {
    if (valor == null) {
      return null;
    }
    if (valor instanceof LocalDate local) {
      return local;
    }
    return ((Date) valor).toLocalDate();
  }

  /**
   * El instante en UTC, venga como venga.
   *
   * <p><b>Los tres casos están porque el driver devuelve los tres</b>, según la consulta y la
   * versión: {@code OffsetDateTime}, {@code Instant} y {@code Timestamp}. Faltaba el del medio y el
   * síntoma fue un {@code ClassCastException} que salía como {@code 500} <b>solo</b> al pedir las
   * tarifas retiradas — porque es la única columna de instante que este listado proyecta, y solo se
   * lee cuando hay alguna retirada que devolver.
   */
  private static OffsetDateTime momento(Object valor) {
    if (valor == null) {
      return null;
    }
    if (valor instanceof OffsetDateTime instante) {
      return instante.withOffsetSameInstant(ZoneOffset.UTC);
    }
    if (valor instanceof java.time.Instant instante) {
      return instante.atOffset(ZoneOffset.UTC);
    }
    return ((Timestamp) valor).toInstant().atOffset(ZoneOffset.UTC);
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
