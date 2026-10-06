package com.factech.nexus.modules.movements.domain.repository;

import com.factech.nexus.modules.movements.domain.models.PointsReceipt;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Escribir el comprobante de un ajuste de puntos (`RF-MV-057`, `RN-MV-077`). */
public interface PointsReceiptRepository {

  /**
   * Bloquea la fila del movimiento si es un {@code AJUSTE_PUNTOS} ({@code SELECT … FOR UPDATE}) y
   * dice si lo es. Dos reemplazos simultáneos auditarían el mismo «anterior» sin él (`plan.md` §1).
   */
  boolean lockAdjustment(UUID movementId);

  /** Inserta el comprobante o reemplaza el que había: uno por ajuste. */
  void upsert(UUID movementId, PointsReceipt comprobante, UUID uploadedBy, OffsetDateTime at);
}
