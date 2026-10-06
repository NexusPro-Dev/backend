package com.factech.nexus.modules.movements.domain.repository;

import com.factech.nexus.shared.pagination.BoundedCount;
import com.factech.nexus.shared.persistence.MinorUnits;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las lecturas de los movimientos de puntos (`RF-MV-055`, `RF-MV-056`).
 *
 * <p><b>Una consulta sobre {@code movements} de los dos tipos, sin {@code UNION}</b>: compra y
 * ajuste viven en la misma tabla y se distinguen por columnas que en el otro van vacías (`plan.md`
 * §1). <b>Los tipos van por su identificador literal</b> (`V58`, `V72`), que es el predicado de
 * {@code ix_movements_puntos} y {@code ix_movements_puntos_persona}: el planificador solo usa un
 * índice parcial cuyo predicado ve escrito.
 *
 * <p><b>El documento de identidad no se lee</b> (`RF-MV-056` `CA-MV-680`).
 */
@Repository
public class JpaPointsMovementQuery implements PointsMovementQuery {

  static final String TIPO_COMPRA = "01a0ef9c-6800-7001-9c4f-5e7ad7000015";
  static final String TIPO_AJUSTE = "01a0ef9c-6800-7025-9c4f-5e7ad7000016";

  private static final String COLUMNAS =
      """
      SELECT m.id AS id, m.code AS code,
             CASE WHEN m.movement_type_id = '%s' THEN 'COMPRA_PUNTOS' ELSE 'AJUSTE_PUNTOS' END
               AS tipo,
             m.status AS status, u.id AS sujeto, u.first_name AS suj_first,
             u.last_name AS suj_last, u.username AS username, u.email AS email,
             c.id AS moneda, c.code AS codigo_moneda, m.points_amount AS puntos,
             m.payable_amount AS importe, m.concept AS concepto,
             m.external_reference AS referencia, m.occurred_at AS ocurrio,
             m.confirmed_at AS confirmado, m.rejected_at AS rechazado,
             m.rejection_reason AS motivo, m.points_rate_id AS tasa, t.points_per_unit AS valor,
             EXISTS (SELECT 1 FROM points_adjustment_receipts x WHERE x.movement_id = m.id)
               AS comprobante,
             r.id AS registro, r.first_name AS reg_first, r.last_name AS reg_last
      """
          .formatted(TIPO_COMPRA);

  /** Quien lo registró y la tasa entran con {@code LEFT JOIN}: el ajuste no tiene tasa. */
  private static final String TABLAS =
      """
       FROM movements m
       JOIN users u ON u.id = m.user_id
       JOIN currencies c ON c.id = m.currency_id
       LEFT JOIN points_rates t ON t.id = m.points_rate_id
       LEFT JOIN users r ON r.id = m.recorded_by
      WHERE m.movement_type_id IN ('%s', '%s') AND\s"""
          .formatted(TIPO_COMPRA, TIPO_AJUSTE);

  private final EntityManager em;

  public JpaPointsMovementQuery(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<PointsMovementRow> find(
      PointsMovementFilter filtro, String orden, int offset, int limit) {
    Filtro f = filtro(filtro);
    Query consulta =
        em.createNativeQuery(
            COLUMNAS
                + TABLAS
                + f.sql()
                // `orden` sale de la lista blanca de `PointsMovementSortField`, nunca de fuera.
                + " ORDER BY "
                + orden
                + " OFFSET :salto LIMIT :tope",
            Tuple.class);
    f.enlazar(consulta);
    consulta.setParameter("salto", offset).setParameter("tope", limit);
    @SuppressWarnings("unchecked")
    List<Tuple> filas = consulta.getResultList();
    return filas.stream().map(JpaPointsMovementQuery::fila).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public BoundedCount count(PointsMovementFilter filtro, int techo) {
    Filtro f = filtro(filtro);
    Query consulta =
        em.createNativeQuery(
            "SELECT count(*) FROM (SELECT 1 " + TABLAS + f.sql() + " LIMIT :techo) n");
    f.enlazar(consulta);
    Object contado = consulta.setParameter("techo", (long) techo + 1).getSingleResult();
    return BoundedCount.de(((Number) contado).longValue(), techo);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PointsMovementRow> findOne(UUID movementId, UUID owner) {
    Filtro f = new Filtro();
    f.condicion("m.id = :id", "id", movementId);
    if (owner != null) {
      f.condicion("m.user_id = :duenio", "duenio", owner);
    }
    Query consulta = em.createNativeQuery(COLUMNAS + TABLAS + f.sql(), Tuple.class);
    f.enlazar(consulta);
    @SuppressWarnings("unchecked")
    List<Tuple> filas = consulta.getResultList();
    return filas.stream().findFirst().map(JpaPointsMovementQuery::fila);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ReceiptInfoRow> findReceiptInfo(UUID movementId) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT file_name AS nombre, content_type AS tipo, size_bytes AS tamano,
                       sha256 AS resumen, uploaded_at AS subido
                  FROM points_adjustment_receipts
                 WHERE movement_id = :id
                """,
                Tuple.class)
            .setParameter("id", movementId)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            t ->
                new ReceiptInfoRow(
                    (String) t.get("nombre"),
                    (String) t.get("tipo"),
                    ((Number) t.get("tamano")).longValue(),
                    ((String) t.get("resumen")).trim(),
                    instante(t.get("subido"))));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ReceiptFileRow> findReceiptFile(UUID movementId, UUID owner) {
    Filtro f = new Filtro();
    f.condicion("x.movement_id = :id", "id", movementId);
    if (owner != null) {
      f.condicion("m.user_id = :duenio", "duenio", owner);
    }
    Query consulta =
        em.createNativeQuery(
            """
            SELECT x.file_name AS nombre, x.content_type AS tipo, x.content AS contenido
              FROM points_adjustment_receipts x
              JOIN movements m ON m.id = x.movement_id
             WHERE\s"""
                + f.sql(),
            Tuple.class);
    f.enlazar(consulta);
    @SuppressWarnings("unchecked")
    List<Tuple> filas = consulta.getResultList();
    return filas.stream()
        .findFirst()
        .map(
            t ->
                new ReceiptFileRow(
                    (String) t.get("nombre"), (String) t.get("tipo"), (byte[]) t.get("contenido")));
  }

  /** El alcance en la sentencia, y los filtros de los demás listados. */
  private static Filtro filtro(PointsMovementFilter p) {
    Filtro f = new Filtro();
    if (p.userId() != null) {
      f.condicion("m.user_id = :persona", "persona", p.userId());
    }
    if (COMPRA.equals(p.type())) {
      f.sinParametro("m.movement_type_id = '" + TIPO_COMPRA + "'");
    } else if (AJUSTE.equals(p.type())) {
      f.sinParametro("m.movement_type_id = '" + TIPO_AJUSTE + "'");
    }
    if (p.status() != null) {
      f.condicion("m.status = :estado", "estado", p.status());
    }
    if (p.currencyId() != null) {
      f.condicion("m.currency_id = :moneda", "moneda", p.currencyId());
    }
    if (p.from() != null) {
      f.condicion("m.occurred_at >= :desde", "desde", p.from());
    }
    if (p.to() != null) {
      f.condicion("m.occurred_at < :hasta", "hasta", p.to());
    }
    if ("SUMA".equals(p.sign())) {
      f.sinParametro("m.points_amount > 0");
    } else if ("RESTA".equals(p.sign())) {
      f.sinParametro("m.points_amount < 0");
    }
    if (p.search() != null && !p.search().isBlank()) {
      // Sin acentos ni mayúsculas, con la función de la base. La persona solo en administración,
      // y nunca su documento (`RF-MV-056` `CA-MV-680`).
      StringBuilder o =
          new StringBuilder("(")
              .append(como("m.code"))
              .append(" OR ")
              .append(como("coalesce(m.concept, '')"))
              .append(" OR ")
              .append(como("coalesce(m.external_reference, '')"));
      if (p.administracion()) {
        o.append(" OR ")
            .append(como("u.username"))
            .append(" OR ")
            .append(como("u.email"))
            .append(" OR ")
            .append(como("u.first_name || ' ' || u.last_name"));
      }
      f.condicion(
          o.append(")").toString(),
          "termino",
          "%"
              + p.search()
                  .toLowerCase(Locale.ROOT)
                  .replace("\\", "\\\\")
                  .replace("%", "\\%")
                  .replace("_", "\\_")
              + "%");
    }
    return f;
  }

  private static String como(String expresion) {
    return "f_unaccent(lower(" + expresion + ")) LIKE f_unaccent(:termino) ESCAPE '\\'";
  }

  private static PointsMovementRow fila(Tuple t) {
    boolean compra = "COMPRA_PUNTOS".equals(t.get("tipo"));
    return new PointsMovementRow(
        (UUID) t.get("id"),
        (String) t.get("code"),
        (String) t.get("tipo"),
        (String) t.get("status"),
        (UUID) t.get("sujeto"),
        (String) t.get("suj_first"),
        (String) t.get("suj_last"),
        (String) t.get("username"),
        (String) t.get("email"),
        (UUID) t.get("moneda"),
        ((String) t.get("codigo_moneda")).trim(),
        MinorUnits.fromMinor(t.get("puntos")),
        // El ajuste no lleva dinero: sus importes de cabecera son cero (`RN-MV-076`).
        compra ? MinorUnits.fromMinor(t.get("importe")) : null,
        (String) t.get("concepto"),
        (String) t.get("referencia"),
        instante(t.get("ocurrio")),
        instante(t.get("confirmado")),
        instante(t.get("rechazado")),
        (String) t.get("motivo"),
        (UUID) t.get("tasa"),
        // `points_per_unit` es una tasa, no un importe: no va en centésimas.
        (BigDecimal) t.get("valor"),
        Boolean.TRUE.equals(t.get("comprobante")),
        (UUID) t.get("registro"),
        (String) t.get("reg_first"),
        (String) t.get("reg_last"));
  }

  /** El instante, venga como venga del controlador JDBC (como {@code JpaMovementRepository}). */
  private static OffsetDateTime instante(Object valor) {
    return switch (valor) {
      case null -> null;
      case OffsetDateTime momento -> momento;
      case Instant momento -> momento.atOffset(ZoneOffset.UTC);
      case Timestamp marca -> marca.toInstant().atOffset(ZoneOffset.UTC);
      default ->
          throw new IllegalStateException(
              "Tipo temporal inesperado en la proyección: " + valor.getClass());
    };
  }

  /** Las condiciones y sus parámetros, como el {@code Filtro} de {@code JpaMovementRepository}. */
  private static final class Filtro {

    private final StringBuilder donde = new StringBuilder("1 = 1");
    private final Map<String, Object> parametros = new LinkedHashMap<>();

    void condicion(String sql, String nombre, Object valor) {
      donde.append(" AND ").append(sql);
      parametros.put(nombre, valor);
    }

    void sinParametro(String sql) {
      donde.append(" AND ").append(sql);
    }

    String sql() {
      return donde.toString();
    }

    void enlazar(Query consulta) {
      parametros.forEach(consulta::setParameter);
    }
  }
}
