package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.RateSource;
import com.factech.nexus.modules.commissions.domain.service.AfftrackTierPicker.Tier;
import com.factech.nexus.shared.persistence.MinorUnits;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de {@link AfftrackSettlementRepository}.
 *
 * <p><b>Las líneas se leen con un {@code JOIN} de lectura a {@code movement_details} y {@code
 * movements}</b>, el precedente de las lecturas de la liquidación (`RF-CM-010`, `RF-CM-014`): es
 * una pregunta de `CM` sobre datos que ya existen. Qué productos son FTD <b>no</b> se decide aquí:
 * llega de `PM` como conjunto (`ProductCatalog.ftdProductIds`).
 */
@Repository
public class JpaAfftrackSettlementRepository implements AfftrackSettlementRepository {

  private final EntityManager em;

  public JpaAfftrackSettlementRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public List<FtdLine> newFtdLines(Collection<UUID> ftd, OffsetDateTime corte) {
    if (ftd.isEmpty()) {
      return List.of();
    }
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        em.createNativeQuery(
                """
                SELECT d.id, d.product_id, d.seller_id, d.delivered_at
                  FROM movement_details d
                  JOIN movements m ON m.id = d.movement_id
                 WHERE m.status = 'CONFIRMADA'
                   AND m.movement_type_id = (SELECT t.id FROM movement_types t WHERE t.code = 'VENTA')
                   AND d.seller_id IS NOT NULL
                   AND d.delivery_status = 'ENTREGADA'
                   AND d.delivered_at < :corte
                   AND d.product_id IN (:ftd)
                   AND NOT EXISTS (SELECT 1 FROM afftrack_ftds f
                                    WHERE f.movement_detail_id = d.id AND f.user_id = d.seller_id)
                 ORDER BY d.delivered_at, d.id
                """)
            .setParameter("corte", corte)
            .setParameter("ftd", ftd)
            .getResultList();
    List<FtdLine> lineas = new ArrayList<>(filas.size());
    for (Object[] f : filas) {
      lineas.add(new FtdLine((UUID) f[0], (UUID) f[1], (UUID) f[2], CommissionRows.momento(f[3])));
    }
    return lineas;
  }

  @Override
  public Map<PersonProduct, Integer> carriedOut(Collection<UUID> ftd) {
    if (ftd.isEmpty()) {
      return Map.of();
    }
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        em.createNativeQuery(
                """
                SELECT DISTINCT ON (s.user_id, s.product_id) s.user_id, s.product_id, s.carried_out
                  FROM afftrack_settlements s
                 WHERE s.product_id IN (:ftd)
                 ORDER BY s.user_id, s.product_id, s.created_at DESC, s.id DESC
                """)
            .setParameter("ftd", ftd)
            .getResultList();
    Map<PersonProduct, Integer> remanentes = new LinkedHashMap<>();
    for (Object[] f : filas) {
      int remanente = ((Number) f[2]).intValue();
      if (remanente > 0) {
        remanentes.put(new PersonProduct((UUID) f[0], (UUID) f[1]), remanente);
      }
    }
    return remanentes;
  }

  @Override
  public Map<PersonProduct, List<Tier>> scales(
      Collection<UUID> personas, Collection<UUID> productos, LocalDate dia) {
    Map<PersonProduct, List<Tier>> escalas = new HashMap<>();
    if (personas.isEmpty() || productos.isEmpty()) {
      return escalas;
    }
    // 1. Las de persona vigentes ese día: el MISMO predicado que el listado (`CA-CM-236`).
    @SuppressWarnings("unchecked")
    List<Object[]> propias =
        em.createNativeQuery(
                "SELECT u.user_id, u.product_id, u.id, u.threshold, u.amount_per_ftd"
                    + " FROM user_afftrack_rates u"
                    + " WHERE u.user_id IN (:personas) AND u.product_id IN (:productos) AND "
                    + AfftrackSql.VIGENTE_EN)
            .setParameter("personas", personas)
            .setParameter("productos", productos)
            .setParameter("dia", dia.toString())
            .getResultList();
    for (Object[] f : propias) {
      escalas
          .computeIfAbsent(new PersonProduct((UUID) f[0], (UUID) f[1]), k -> new ArrayList<>())
          .add(escalon(f, RateSource.PERSONALIZADA));
    }
    // 2. Las de su rol vendedor, solo para quien no tiene escala propia: SUSTITUYE, no se
    //    mezcla (`RN-CM-039`). El rol se lee de `user_roles.role_type`, la copia que `SP` ata
    //    por clave compuesta a `roles`, igual que `SellerRoleCatalog`.
    @SuppressWarnings("unchecked")
    List<Object[]> deRol =
        em.createNativeQuery(
                """
                SELECT ur.user_id, a.product_id, a.id, a.threshold, a.amount_per_ftd
                  FROM user_roles ur
                  JOIN afftrack_rates a ON a.role_id = ur.role_id
                 WHERE ur.user_id IN (:personas) AND ur.role_type = 'VENDEDOR'
                   AND a.product_id IN (:productos) AND a.deleted_at IS NULL
                """)
            .setParameter("personas", personas)
            .setParameter("productos", productos)
            .getResultList();
    Map<PersonProduct, List<Tier>> porRol = new HashMap<>();
    for (Object[] f : deRol) {
      porRol
          .computeIfAbsent(new PersonProduct((UUID) f[0], (UUID) f[1]), k -> new ArrayList<>())
          .add(escalon(f, RateSource.ROL));
    }
    porRol.forEach(escalas::putIfAbsent);
    return escalas;
  }

  private static Tier escalon(Object[] f, RateSource fuente) {
    return new Tier((UUID) f[2], fuente, ((Number) f[3]).intValue(), MinorUnits.fromMinor(f[4]));
  }

  @Override
  public void insertSettlement(NewSettlement s) {
    em.createNativeQuery(
            """
            INSERT INTO afftrack_settlements
                (id, closing_id, user_id, product_id, carried_in, new_ftds, paid_ftds,
                 carried_out, source, threshold_rate_id, created_at)
            VALUES (:id, :cierre, :persona, :producto, :traia, :nuevos, :pagados,
                    :quedan, CAST(:fuente AS varchar), CAST(:escalon AS uuid), :at)
            """)
        .setParameter("id", s.id())
        .setParameter("cierre", s.closingId())
        .setParameter("persona", s.userId())
        .setParameter("producto", s.productId())
        .setParameter("traia", s.carriedIn())
        .setParameter("nuevos", s.newFtds())
        .setParameter("pagados", s.paidFtds())
        .setParameter("quedan", s.carriedOut())
        .setParameter("fuente", s.source())
        .setParameter(
            "escalon", s.thresholdRateId() == null ? null : s.thresholdRateId().toString())
        .setParameter("at", s.at())
        .executeUpdate();
  }

  @Override
  public void insertFtds(UUID settlementId, List<CountedFtd> ftds, OffsetDateTime at) {
    for (CountedFtd ftd : ftds) {
      em.createNativeQuery(
              """
              INSERT INTO afftrack_ftds (movement_detail_id, user_id, chain_level, settlement_id,
                                         created_at)
              SELECT :linea, s.user_id, :nivel, s.id, :at
                FROM afftrack_settlements s WHERE s.id = :liquidacion
              """)
          .setParameter("linea", ftd.detailId())
          .setParameter("nivel", ftd.chainLevel())
          .setParameter("liquidacion", settlementId)
          .setParameter("at", at)
          .executeUpdate();
    }
  }

  @Override
  public void insertCommission(NewAfftrackCommission c) {
    em.createNativeQuery(
            """
            INSERT INTO commissions
                (id, batch_id, commission_kind, afftrack_settlement_id, user_id, source, rate_id,
                 resolved_on, rate_type, fixed_amount, quantity, commission_amount, accrued_at,
                 created_at)
            VALUES (:id, :lote, 'POR_AFFTRACK', :liquidacion, :persona, :fuente, :escalon,
                    :dia, 'FIJO', :valor, :cantidad, :importe, :at, :at)
            """)
        .setParameter("id", c.id())
        .setParameter("lote", c.batchId())
        .setParameter("liquidacion", c.settlementId())
        .setParameter("persona", c.userId())
        .setParameter("fuente", c.source())
        .setParameter("escalon", c.rateId())
        .setParameter("dia", c.resolvedOn())
        .setParameter("valor", MinorUnits.toMinor(c.amountPerFtd()))
        .setParameter("cantidad", c.quantity())
        .setParameter("importe", MinorUnits.toMinor(c.amount()))
        .setParameter("at", c.accruedAt())
        .executeUpdate();
  }
}
