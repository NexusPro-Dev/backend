package com.factech.nexus.modules.movements.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * <b>Las líneas de venta que comisionan</b>, publicadas para `CM` (`RN-MV-049`; D-25).
 *
 * <p>Una línea es <b>comisionable</b> cuando su movimiento es una {@code VENTA} {@code CONFIRMADA}
 * y ella tiene vendedor (`RN-CM-022`, `RN-MV-035`). `CM` la devenga al recibir {@link
 * CommissionableLinesEvent} y la busca aquí cuando el aviso no le llegó (`RN-CM-034`). <b>`MV` no
 * sabe qué es una comisión</b>: publica qué se vendió, a quién se atribuye y cuándo, y nada más.
 */
public interface CommissionableLines {

  /**
   * De esas líneas, las que <b>hoy</b> son comisionables. Las demás —no confirmadas, sin vendedor,
   * de otro tipo de movimiento, inexistentes— se omiten sin error: quien pregunta relee el estado,
   * no confía en el aviso.
   */
  List<CommissionableLine> of(Collection<UUID> detailIds);

  /**
   * Los identificadores de las líneas comisionables <b>posteriores</b> a {@code cursor}, en orden
   * de identificador, para recorrerlas por tandas sin página. Un cursor nulo empieza por el
   * principio.
   */
  List<UUID> idsAfter(UUID cursor, int limit);

  /** Lo que `CM` necesita para devengar una línea (`RN-CM-023`, `RN-CM-024`). */
  record CommissionableLine(
      UUID detailId,
      UUID movementId,
      UUID productId,
      UUID sellerId,
      BigDecimal unitPrice,
      int quantity,
      UUID currencyId,
      OffsetDateTime occurredAt) {}
}
