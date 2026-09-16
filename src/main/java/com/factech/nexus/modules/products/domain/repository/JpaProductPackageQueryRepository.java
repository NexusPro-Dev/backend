package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.application.ListPackagesRequest;
import com.factech.nexus.modules.products.domain.models.DiscountType;
import com.factech.nexus.modules.products.domain.models.DiscountValue;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ProductPackageQueryRepository} sobre SQL nativo.
 *
 * <p><b>Dos bloques de columnas y no seis sentencias</b>: el del paquete ({@link
 * #COLUMNAS_PAQUETE}) y el de la fila con su producto ({@link #COLUMNAS_FILA}). El detalle los
 * junta con {@code LEFT JOIN} y desdobla la repetición aquí; el listado trae primero la página de
 * paquetes y después <b>todas</b> sus filas en una sentencia, y las agrupa en Java. Un paquete
 * vacío viene, en el detalle, como una sola fila con las columnas del producto en nulo.
 */
@Repository
public class JpaProductPackageQueryRepository implements ProductPackageQueryRepository {

  private static final String COLUMNAS_PAQUETE =
      """
      k.id AS id, k.code AS code, k.name AS name, k.description AS description,
      k.cover_image_id AS cover_image_id,
      k.currency_id AS c_id, c.code AS c_code, c.decimal_places AS c_decimales,
      k.status AS status, k.scope AS scope,
      k.valid_from AS valid_from, k.valid_to AS valid_to,
      k.created_at AS created_at, k.updated_at AS updated_at, k.deleted_at AS deleted_at
      """;

  private static final String COLUMNAS_FILA =
      """
      i.package_id AS k_id, i.product_id AS p_id, p.code AS p_code, p.name AS p_name,
      p.type AS p_type, p.status AS p_status, p.deleted_at AS p_deleted_at,
      p.price AS p_price, p.purchase_price AS p_purchase_price,
      p.currency_id AS p_currency_id, p.source_membership_id AS p_source_membership_id,
      i.discount_type AS discount_type, i.discount_value AS discount_value,
      i.created_at AS i_created_at
      """;

  /** Las filas en el orden en que entraron al paquete, con el producto como desempate estable. */
  private static final String ORDEN_DE_FILAS = " ORDER BY i.created_at, i.product_id";

  private final EntityManager em;

  public JpaProductPackageQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PackageDetail> findDetail(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    // Sin `k.deleted_at IS NULL`: el retirado se devuelve marcado (`FA-003`).
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT "
                    + COLUMNAS_PAQUETE
                    + ", "
                    + COLUMNAS_FILA
                    + """
                      FROM product_packages k
                      JOIN currencies c ON c.id = k.currency_id
                      LEFT JOIN product_package_items i ON i.package_id = k.id
                      LEFT JOIN products p ON p.id = i.product_id
                     WHERE k.id = :id
                    """
                    + ORDEN_DE_FILAS,
                Tuple.class)
            .setParameter("id", id)
            .getResultList();
    return desdoblar(filas).stream().findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public List<PackageRow> search(
      ListPackagesRequest filtros, String ordenamiento, int offset, int limit) {
    Query consulta =
        em.createNativeQuery(
            "SELECT "
                + COLUMNAS_PAQUETE
                + " FROM product_packages k JOIN currencies c ON c.id = k.currency_id WHERE "
                + predicado(filtros)
                + " ORDER BY "
                + ordenamiento,
            Tuple.class);
    enlazar(consulta, filtros);
    List<Tuple> filas = consulta.setFirstResult(offset).setMaxResults(limit).getResultList();
    return filas.stream().map(JpaProductPackageQueryRepository::paquete).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long count(ListPackagesRequest filtros) {
    Query consulta =
        em.createNativeQuery("SELECT count(*) FROM product_packages k WHERE " + predicado(filtros));
    enlazar(consulta, filtros);
    return ((Number) consulta.getSingleResult()).longValue();
  }

  @Override
  @Transactional(readOnly = true)
  public List<PackageItemRow> findItemsOf(List<UUID> packageIds) {
    if (packageIds == null || packageIds.isEmpty()) {
      return List.of();
    }
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT "
                    + COLUMNAS_FILA
                    + """
                      FROM product_package_items i
                      JOIN products p ON p.id = i.product_id
                     WHERE i.package_id IN (:ids)
                     ORDER BY i.package_id, i.created_at, i.product_id
                    """,
                Tuple.class)
            .setParameter("ids", packageIds)
            .getResultList();
    return filas.stream().map(fila -> item(fila, (UUID) fila.get("k_id"))).toList();
  }

  /**
   * LA SEXTA COPIA del SELECT de productos —catálogo, detalle, oferta, hotlink, disponibles y
   * esta—, con las columnas del paquete delante con prefijo {@code k_} para no chocar con las del
   * producto, que conservan los alias que {@link JpaProductQueryRepository#fila} espera. <b>Sin
   * {@code purchase_price}</b>: son las dos lecturas públicas (`RN-PM-043`). {@code LEFT JOIN} a
   * las filas: un paquete con menos de dos productos tiene que VOLVER para que la ofrecibilidad
   * diga que no — el mismo 404 en el hotlink, ausencia en la oferta— y no desaparecer en el WHERE.
   */
  private static final String SELECT_PUBLICADO =
      """
      SELECT k.id AS k_id, k.code AS k_code, k.name AS k_name,
             k.description AS k_description,
             k.cover_image_id AS k_cover_image_id,
             k.currency_id AS k_c_id, kc.code AS k_c_code,
             kc.decimal_places AS k_c_decimales,
             k.status AS k_status, k.scope AS k_scope,
             k.valid_from AS k_valid_from, k.valid_to AS k_valid_to,
             k.created_at AS k_created_at, k.updated_at AS k_updated_at,
             k.deleted_at AS k_deleted_at,
             i.discount_type AS discount_type, i.discount_value AS discount_value,
             p.id AS id, p.code AS code, p.type AS type, p.name AS name,
             p.description AS description, p.icon AS icon, p.video_url AS video_url,
             p.cover_image_id AS cover_image_id,
             p.target_membership_id AS m_id, m.code AS m_code, m.name AS m_name,
             m.level AS m_level, m.color AS m_color,
             p.source_membership_id AS s_id, s.code AS s_code, s.name AS s_name,
             s.level AS s_level, s.color AS s_color,
             p.price AS price,
             p.currency_id AS c_id, c.code AS c_code,
             c.decimal_places AS c_decimales,
             p.validity_days AS validity_days, p.scope AS scope,
             p.implementation AS implementation, p.status AS status,
             p.created_at AS created_at, p.deleted_at AS deleted_at,
             r.rating_avg AS rating_avg, r.rating_count AS rating_count
        FROM product_packages k
        JOIN currencies kc ON kc.id = k.currency_id
        LEFT JOIN product_package_items i ON i.package_id = k.id
        LEFT JOIN products p ON p.id = i.product_id
        LEFT JOIN memberships m ON m.id = p.target_membership_id
        LEFT JOIN memberships s ON s.id = p.source_membership_id
        LEFT JOIN currencies  c ON c.id = p.currency_id
        LEFT JOIN LATERAL (
            SELECT avg(pc.rating) AS rating_avg, count(*) AS rating_count
              FROM product_comments pc
             WHERE pc.product_id = p.id AND pc.deleted_at IS NULL
        ) r ON true
""";

  @Override
  @Transactional(readOnly = true)
  public Optional<PublishedPackage> findPublishedByCode(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    List<Tuple> filas =
        em.createNativeQuery(
                SELECT_PUBLICADO
                    + """
                     WHERE upper(k.code) = upper(:codigo)
                       AND k.status = 'ACTIVO'
                       AND k.deleted_at IS NULL
                       AND k.scope IN ('HOTLINK', 'AMBOS')
                     ORDER BY i.created_at, i.product_id
                    """,
                Tuple.class)
            .setParameter("codigo", code)
            .getResultList();
    return publicados(filas).stream().findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public List<PublishedPackage> findOfferable() {
    List<Tuple> filas =
        em.createNativeQuery(
                SELECT_PUBLICADO
                    + """
                     WHERE k.status = 'ACTIVO'
                       AND k.deleted_at IS NULL
                       AND k.scope IN ('TIENDA', 'AMBOS')
                     ORDER BY k.created_at, k.id, i.created_at, i.product_id
                    """,
                Tuple.class)
            .getResultList();
    return publicados(filas);
  }

  /**
   * Agrupa las filas planas por paquete conservando el orden, con el producto en su forma pública.
   */
  private static List<PublishedPackage> publicados(List<Tuple> filas) {
    List<PublishedPackage> paquetes = new ArrayList<>();
    UUID actual = null;
    List<PublishedItem> items = null;
    for (Tuple fila : filas) {
      UUID id = (UUID) fila.get("k_id");
      if (!id.equals(actual)) {
        items = new ArrayList<>();
        paquetes.add(
            new PublishedPackage(
                new PackageRow(
                    id,
                    (String) fila.get("k_code"),
                    (String) fila.get("k_name"),
                    (String) fila.get("k_description"),
                    (UUID) fila.get("k_cover_image_id"),
                    (UUID) fila.get("k_c_id"),
                    (String) fila.get("k_c_code"),
                    ((Number) fila.get("k_c_decimales")).intValue(),
                    (String) fila.get("k_status"),
                    (String) fila.get("k_scope"),
                    fecha(fila.get("k_valid_from")),
                    fecha(fila.get("k_valid_to")),
                    momento(fila.get("k_created_at")),
                    momento(fila.get("k_updated_at")),
                    momento(fila.get("k_deleted_at"))),
                items));
        actual = id;
      }
      if (fila.get("id") != null) {
        items.add(
            new PublishedItem(
                JpaProductQueryRepository.fila(fila),
                DiscountValue.leido(
                    DiscountType.valueOf((String) fila.get("discount_type")),
                    (BigDecimal) fila.get("discount_value")),
                fila.get("deleted_at") != null));
      }
    }
    return paquetes;
  }

  /**
   * Agrupa las filas planas del detalle por paquete, <b>conservando el orden</b> en que llegaron:
   * cada paquete con sus filas de asociación, y sin ninguna cuando el {@code LEFT JOIN} dejó el
   * producto en nulo.
   */
  static List<PackageDetail> desdoblar(List<Tuple> filas) {
    List<PackageDetail> paquetes = new ArrayList<>();
    UUID actual = null;
    List<PackageItemRow> items = null;
    for (Tuple fila : filas) {
      UUID id = (UUID) fila.get("id");
      if (!id.equals(actual)) {
        items = new ArrayList<>();
        paquetes.add(new PackageDetail(paquete(fila), items));
        actual = id;
      }
      if (fila.get("p_id") != null) {
        items.add(item(fila, id));
      }
    }
    return paquetes;
  }

  /**
   * Los cinco filtros, todos sobre columnas de {@code product_packages}. Ninguno multiplica la fila
   * —un paquete tiene una moneda, no colecciones—, de modo que el conteo cuenta paquetes.
   */
  private static String predicado(ListPackagesRequest filtros) {
    StringBuilder donde = new StringBuilder();
    donde.append(filtros.incluirEliminados() ? "1 = 1" : "k.deleted_at IS NULL");
    if (filtros.status() != null) {
      donde.append(" AND k.status = :estado");
    }
    if (filtros.scope() != null) {
      donde.append(" AND k.scope = :alcance");
    }
    if (filtros.currencyId() != null) {
      donde.append(" AND k.currency_id = :moneda");
    }
    if (filtros.q() != null) {
      donde.append(" AND f_unaccent(lower(k.name)) LIKE f_unaccent(lower(:termino)) ESCAPE '\\'");
    }
    return donde.toString();
  }

  private static void enlazar(Query consulta, ListPackagesRequest filtros) {
    if (filtros.status() != null) {
      consulta.setParameter("estado", filtros.status());
    }
    if (filtros.scope() != null) {
      consulta.setParameter("alcance", filtros.scope());
    }
    if (filtros.currencyId() != null) {
      consulta.setParameter("moneda", filtros.currencyId());
    }
    if (filtros.q() != null) {
      consulta.setParameter("termino", "%" + escapar(filtros.q()) + "%");
    }
  }

  /** Escapa lo que {@code LIKE} interpreta; la barra va primero. */
  private static String escapar(String termino) {
    return termino.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static PackageRow paquete(Tuple fila) {
    return new PackageRow(
        (UUID) fila.get("id"),
        (String) fila.get("code"),
        (String) fila.get("name"),
        (String) fila.get("description"),
        (UUID) fila.get("cover_image_id"),
        (UUID) fila.get("c_id"),
        (String) fila.get("c_code"),
        ((Number) fila.get("c_decimales")).intValue(),
        (String) fila.get("status"),
        (String) fila.get("scope"),
        fecha(fila.get("valid_from")),
        fecha(fila.get("valid_to")),
        momento(fila.get("created_at")),
        momento(fila.get("updated_at")),
        momento(fila.get("deleted_at")));
  }

  private static PackageItemRow item(Tuple fila, UUID packageId) {
    return new PackageItemRow(
        packageId,
        (UUID) fila.get("p_id"),
        (String) fila.get("p_code"),
        (String) fila.get("p_name"),
        (String) fila.get("p_type"),
        (String) fila.get("p_status"),
        momento(fila.get("p_deleted_at")),
        (BigDecimal) fila.get("p_price"),
        (BigDecimal) fila.get("p_purchase_price"),
        (UUID) fila.get("p_currency_id"),
        (UUID) fila.get("p_source_membership_id"),
        (String) fila.get("discount_type"),
        (BigDecimal) fila.get("discount_value"),
        momento(fila.get("i_created_at")));
  }

  /** Una columna {@code date}: el conector la entrega como {@code java.sql.Date} o ya local. */
  private static LocalDate fecha(Object valor) {
    return switch (valor) {
      case null -> null;
      case LocalDate local -> local;
      case java.sql.Date sql -> sql.toLocalDate();
      default -> throw new IllegalStateException("Fecha inesperada: " + valor.getClass());
    };
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
