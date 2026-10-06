package com.factech.nexus.modules.movements.domain.repository;

import com.factech.nexus.modules.movements.domain.models.PointsReceipt;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** {@link PointsReceiptRepository} sobre {@code points_adjustment_receipts} (`V77`). */
@Repository
public class JpaPointsReceiptRepository implements PointsReceiptRepository {

  private final EntityManager em;

  public JpaPointsReceiptRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public boolean lockAdjustment(UUID movementId) {
    return !em.createNativeQuery(
            "SELECT m.id FROM movements m WHERE m.id = :id AND m.movement_type_id = '"
                + JpaPointsMovementQuery.TIPO_AJUSTE
                + "' FOR UPDATE")
        .setParameter("id", movementId)
        .getResultList()
        .isEmpty();
  }

  @Override
  public void upsert(
      UUID movementId, PointsReceipt comprobante, UUID uploadedBy, OffsetDateTime at) {
    em.createNativeQuery(
            """
            INSERT INTO points_adjustment_receipts
                   (movement_id, file_name, content_type, size_bytes, sha256, content,
                    uploaded_by, uploaded_at)
            VALUES (:id, :nombre, :tipo, :tamano, :resumen, :contenido, :quien, :cuando)
            ON CONFLICT (movement_id) DO UPDATE
               SET file_name = EXCLUDED.file_name, content_type = EXCLUDED.content_type,
                   size_bytes = EXCLUDED.size_bytes, sha256 = EXCLUDED.sha256,
                   content = EXCLUDED.content, uploaded_by = EXCLUDED.uploaded_by,
                   uploaded_at = EXCLUDED.uploaded_at
            """)
        .setParameter("id", movementId)
        .setParameter("nombre", comprobante.fileName())
        .setParameter("tipo", comprobante.contentType())
        .setParameter("tamano", comprobante.sizeBytes())
        .setParameter("resumen", comprobante.sha256())
        .setParameter("contenido", comprobante.content())
        .setParameter("quien", uploadedBy)
        .setParameter("cuando", at)
        .executeUpdate();
  }
}
