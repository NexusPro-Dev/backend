package com.factech.nexus.modules.movements.domain.repository;

import com.factech.nexus.modules.movements.application.CommissionableLines;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CommissionableLines} sobre sentencias nativas (`RF-CM-013` · `T-07`).
 *
 * <p><b>El predicado de «comisionable» vive en una sola constante</b> y lo comparten {@link #of} y
 * {@link #idsAfter}: el aviso y el barrido tienen que coincidir exactamente, o una línea devengaría
 * por un camino y no por el otro. Son las cuatro condiciones de `RN-CM-022` que son de `MV`; la
 * quinta —no tener desenlace— es de `CM` y la mira `CM`.
 */
@Repository
public class JpaCommissionableLines implements CommissionableLines {

  private static final String COMISIONABLE =
      """
      m.status = 'CONFIRMADA'
      AND d.seller_id IS NOT NULL
      AND m.movement_type_id = (SELECT t.id FROM movement_types t WHERE t.code = 'VENTA')
      """;

  private static final String DE =
      """
      SELECT d.id, d.movement_id, d.product_id, d.seller_id, d.unit_price, d.quantity,
             m.currency_id, m.occurred_at
        FROM movement_details d
        JOIN movements m ON m.id = d.movement_id
       WHERE d.id IN (:ids) AND
      """
          + COMISIONABLE;

  private static final String DESPUES_DE =
      """
      SELECT d.id
        FROM movement_details d
        JOIN movements m ON m.id = d.movement_id
       WHERE (CAST(:cursor AS uuid) IS NULL OR d.id > CAST(:cursor AS uuid)) AND
      """
          + COMISIONABLE
          + " ORDER BY d.id LIMIT :limite";

  private final EntityManager em;

  public JpaCommissionableLines(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<CommissionableLine> of(Collection<UUID> detailIds) {
    if (detailIds == null || detailIds.isEmpty()) {
      return List.of();
    }
    @SuppressWarnings("unchecked")
    List<Object[]> filas = em.createNativeQuery(DE).setParameter("ids", detailIds).getResultList();
    List<CommissionableLine> lineas = new ArrayList<>(filas.size());
    for (Object[] f : filas) {
      lineas.add(
          new CommissionableLine(
              (UUID) f[0],
              (UUID) f[1],
              (UUID) f[2],
              (UUID) f[3],
              (BigDecimal) f[4],
              ((Number) f[5]).intValue(),
              (UUID) f[6],
              instante(f[7])));
    }
    return lineas;
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> idsAfter(UUID cursor, int limit) {
    @SuppressWarnings("unchecked")
    List<UUID> ids =
        em.createNativeQuery(DESPUES_DE)
            .setParameter("cursor", cursor)
            .setParameter("limite", limit)
            .getResultList();
    return new ArrayList<>(ids);
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
