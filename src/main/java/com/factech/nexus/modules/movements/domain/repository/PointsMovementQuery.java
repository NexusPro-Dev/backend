package com.factech.nexus.modules.movements.domain.repository;

import com.factech.nexus.shared.pagination.BoundedCount;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Las lecturas de los movimientos de puntos —compras y ajustes— (`RF-MV-055`, `RF-MV-056`), y la
 * del comprobante de un ajuste. <b>Solo lee</b>: por eso es un puerto aparte de {@link
 * MovementRepository} (`RF-MV-055` `plan.md` §1).
 */
public interface PointsMovementQuery {

  String COMPRA = "COMPRA_PUNTOS";
  String AJUSTE = "AJUSTE_PUNTOS";

  /** Una venta pagada con puntos (0.2.0): no es un tipo de movimiento, es una forma de leerla. */
  String GASTO = "GASTO_PUNTOS";

  /**
   * Una página, en el orden ya resuelto contra la lista blanca de {@code PointsMovementSortField}.
   */
  List<PointsMovementRow> find(PointsMovementFilter filtro, String orden, int offset, int limit);

  BoundedCount count(PointsMovementFilter filtro, int techo);

  /**
   * Uno, o vacío si no existe o no es de puntos. Con {@code owner}, también vacío si es de otra
   * persona: ajeno es inexistente (`RF-MV-055` `EX-002`).
   */
  Optional<PointsMovementRow> findOne(UUID movementId, UUID owner);

  /** Las líneas de la venta de un gasto, en su orden. */
  List<LineRow> findLines(UUID movementId);

  /** Los datos del comprobante de un ajuste, sin el archivo. */
  Optional<ReceiptInfoRow> findReceiptInfo(UUID movementId);

  /** El archivo del comprobante. Con {@code owner}, vacío si el ajuste es de otra persona. */
  Optional<ReceiptFileRow> findReceiptFile(UUID movementId, UUID owner);

  /**
   * Los filtros. Nulo es «sin filtro». {@code userId} es el alcance —el actor en la lista propia—,
   * y {@code administracion} abre la búsqueda por la persona y quién hizo el ajuste.
   */
  record PointsMovementFilter(
      UUID userId,
      boolean administracion,
      String type,
      String status,
      UUID currencyId,
      OffsetDateTime from,
      OffsetDateTime to,
      String sign,
      String search) {}

  /** Una compra o un ajuste, con lo que piden la fila y el detalle. */
  record PointsMovementRow(
      UUID id,
      String code,
      String type,
      String status,
      UUID userId,
      String userFirstName,
      String userLastName,
      String username,
      String email,
      UUID currencyId,
      String currencyCode,
      BigDecimal points,
      BigDecimal amount,
      String concept,
      String externalReference,
      OffsetDateTime occurredAt,
      OffsetDateTime confirmedAt,
      OffsetDateTime rejectedAt,
      String rejectionReason,
      UUID pointsRateId,
      BigDecimal pointsPerUnit,
      boolean hasReceipt,
      UUID recordedBy,
      String recordedByFirstName,
      String recordedByLastName) {}

  record LineRow(String productName, int quantity, BigDecimal amount) {}

  record ReceiptInfoRow(
      String fileName,
      String contentType,
      long sizeBytes,
      String sha256,
      OffsetDateTime uploadedAt) {}

  record ReceiptFileRow(String fileName, String contentType, byte[] content) {}
}
