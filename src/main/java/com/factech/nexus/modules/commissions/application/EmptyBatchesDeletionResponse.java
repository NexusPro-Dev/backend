package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository.DeletedBatch;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Los lotes que borró el borrado de los vacíos (`RF-CM-027`, `RN-CM-052`), <b>por
 * identificador</b>, y cuántos fueron.
 */
@Schema(name = "EmptyBatchesDeletionResponse")
public record EmptyBatchesDeletionResponse(List<DeletedEmptyBatch> deleted, int deletedCount) {

  public static EmptyBatchesDeletionResponse de(List<DeletedBatch> borrados) {
    List<DeletedEmptyBatch> filas = borrados.stream().map(DeletedEmptyBatch::de).toList();
    return new EmptyBatchesDeletionResponse(filas, filas.size());
  }

  /**
   * Lo que era un lote borrado: el estado que tenía —{@code ABIERTO} o {@code PENDIENTE}— y su
   * periodo, con {@code periodEnd} nulo si estaba abierto.
   */
  @Schema(name = "DeletedEmptyBatch")
  public record DeletedEmptyBatch(
      UUID id,
      String code,
      UUID userId,
      UUID currencyId,
      String status,
      OffsetDateTime periodStart,
      OffsetDateTime periodEnd) {

    static DeletedEmptyBatch de(DeletedBatch lote) {
      return new DeletedEmptyBatch(
          lote.id(),
          lote.code(),
          lote.userId(),
          lote.currencyId(),
          lote.status(),
          lote.periodStart(),
          lote.periodEnd());
    }
  }
}
