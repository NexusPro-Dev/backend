package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.AccrualOutcome;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * {@link CommissionAccrualQueryRepository} sobre SQL nativo, con la línea y su venta unidas en la
 * misma sentencia: el mismo precedente que {@code JpaCommissionBatchQueryRepository}. <b>La venta y
 * el producto se filtran en la línea</b>, no se copian en {@code commission_accruals}: serían datos
 * que no deciden nada, guardados solo para filtrar.
 */
@Repository
public class JpaCommissionAccrualQueryRepository implements CommissionAccrualQueryRepository {

  private static final String DESDE =
      """
        FROM commission_accruals a
        JOIN movement_details d ON d.id = a.movement_detail_id
        JOIN movements m ON m.id = d.movement_id
       WHERE (CAST(:desenlace AS varchar) IS NULL OR a.outcome = CAST(:desenlace AS varchar))
         AND (CAST(:venta AS uuid) IS NULL OR d.movement_id = CAST(:venta AS uuid))
         AND (CAST(:producto AS uuid) IS NULL OR d.product_id = CAST(:producto AS uuid))
         AND (CAST(:desde AS timestamptz) IS NULL OR a.updated_at >= CAST(:desde AS timestamptz))
         AND (CAST(:hasta AS timestamptz) IS NULL OR a.updated_at <= CAST(:hasta AS timestamptz))
      """;

  private final EntityManager em;

  public JpaCommissionAccrualQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public List<AccrualRow> search(AccrualFilter filtro, int offset, int limit) {
    Query consulta =
        em.createNativeQuery(
            """
            SELECT a.movement_detail_id, d.movement_id, m.code, d.product_id, d.product_name,
                   d.seller_id, a.outcome, a.reason, a.attempts, a.created_at, a.updated_at
            """
                + DESDE
                + " ORDER BY a.updated_at DESC, a.movement_detail_id OFFSET :offset LIMIT :limite");
    parametros(consulta, filtro);
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        consulta.setParameter("offset", offset).setParameter("limite", limit).getResultList();
    List<AccrualRow> resultado = new ArrayList<>(filas.size());
    for (Object[] f : filas) {
      resultado.add(
          new AccrualRow(
              (UUID) f[0],
              (UUID) f[1],
              (String) f[2],
              (UUID) f[3],
              (String) f[4],
              (UUID) f[5],
              AccrualOutcome.valueOf((String) f[6]),
              (String) f[7],
              ((Number) f[8]).intValue(),
              instante(f[9]),
              instante(f[10])));
    }
    return resultado;
  }

  @Override
  public long count(AccrualFilter filtro) {
    Query consulta = em.createNativeQuery("SELECT count(*)" + DESDE);
    parametros(consulta, filtro);
    return ((Number) consulta.getSingleResult()).longValue();
  }

  private static void parametros(Query consulta, AccrualFilter filtro) {
    consulta
        .setParameter("desenlace", filtro.outcome() == null ? null : filtro.outcome().name())
        .setParameter("venta", filtro.movementId())
        .setParameter("producto", filtro.productId())
        .setParameter("desde", filtro.from())
        .setParameter("hasta", filtro.to());
  }

  private static OffsetDateTime instante(Object valor) {
    if (valor instanceof OffsetDateTime odt) {
      return odt;
    }
    if (valor instanceof java.time.Instant i) {
      return i.atOffset(java.time.ZoneOffset.UTC);
    }
    if (valor instanceof java.sql.Timestamp t) {
      return t.toInstant().atOffset(java.time.ZoneOffset.UTC);
    }
    throw new IllegalStateException("Tipo temporal inesperado: " + valor.getClass());
  }
}
