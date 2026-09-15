package com.factech.nexus.modules.products.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
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
 * <p><b>Una sentencia por lectura</b>, con {@code LEFT JOIN} a las filas de asociación y a sus
 * productos: la fila del paquete se repite una vez por producto y se desdobla aquí. Un paquete
 * vacío viene como una sola fila con las columnas del producto en nulo.
 */
@Repository
public class JpaProductPackageQueryRepository implements ProductPackageQueryRepository {

  /**
   * Las columnas del paquete y de cada fila de asociación con su producto. Es el bloque común a
   * todas las lecturas del submódulo; cada una añade su {@code WHERE} y su orden.
   */
  private static final String SELECT_PAQUETE_CON_FILAS =
      """
      SELECT k.id AS id, k.code AS code, k.name AS name, k.description AS description,
             k.currency_id AS c_id, c.code AS c_code, c.decimal_places AS c_decimales,
             k.status AS status, k.scope AS scope,
             k.created_at AS created_at, k.updated_at AS updated_at, k.deleted_at AS deleted_at,
             i.product_id AS p_id, p.code AS p_code, p.name AS p_name, p.type AS p_type,
             p.status AS p_status, p.deleted_at AS p_deleted_at,
             p.price AS p_price, p.purchase_price AS p_purchase_price,
             p.currency_id AS p_currency_id, p.source_membership_id AS p_source_membership_id,
             i.discount_type AS discount_type, i.discount_value AS discount_value,
             i.created_at AS i_created_at
        FROM product_packages k
        JOIN currencies c ON c.id = k.currency_id
        LEFT JOIN product_package_items i ON i.package_id = k.id
        LEFT JOIN products p ON p.id = i.product_id
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
                SELECT_PAQUETE_CON_FILAS + " WHERE k.id = :id" + ORDEN_DE_FILAS, Tuple.class)
            .setParameter("id", id)
            .getResultList();
    return desdoblar(filas).stream().findFirst();
  }

  /**
   * Agrupa las filas planas por paquete, <b>conservando el orden</b> en que llegaron: cada paquete
   * con sus filas de asociación, y sin ninguna cuando el {@code LEFT JOIN} dejó el producto en
   * nulo.
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

  private static PackageRow paquete(Tuple fila) {
    return new PackageRow(
        (UUID) fila.get("id"),
        (String) fila.get("code"),
        (String) fila.get("name"),
        (String) fila.get("description"),
        (UUID) fila.get("c_id"),
        (String) fila.get("c_code"),
        ((Number) fila.get("c_decimales")).intValue(),
        (String) fila.get("status"),
        (String) fila.get("scope"),
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
