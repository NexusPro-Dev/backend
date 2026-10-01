package com.factech.nexus.modules.commissions.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Lo que pasó con cada lote de un pago de varios (`RF-CM-025`, `RN-CM-049`): <b>una fila por lote,
 * en el orden pedido</b>, y cuántos se pagaron y cuántos no.
 */
@Schema(name = "CommissionBatchesPaymentResponse")
public record CommissionBatchesPaymentResponse(
    List<BatchPaymentResult> results, int paidCount, int notPaidCount) {

  public static CommissionBatchesPaymentResponse de(List<BatchPaymentResult> resultados) {
    int pagados = (int) resultados.stream().filter(BatchPaymentResult::paid).count();
    return new CommissionBatchesPaymentResponse(resultados, pagados, resultados.size() - pagados);
  }

  /**
   * Un lote. <b>Pagado</b>: su código, el importe abonado —redondeado a la moneda— y el movimiento
   * del abono. <b>No pagado</b>: el código y el mensaje que daría pagarlo solo (`RF-CM-011`), en
   * {@code reasonCode} y {@code reason}.
   */
  @Schema(name = "CommissionBatchPaymentResult")
  public record BatchPaymentResult(
      UUID batchId,
      boolean paid,
      String code,
      BigDecimal paidAmount,
      UUID movementId,
      String reasonCode,
      String reason) {

    public static BatchPaymentResult pagado(CommissionBatchDetailResponse lote) {
      return new BatchPaymentResult(
          lote.id(), true, lote.code(), lote.paidAmount(), lote.movementId(), null, null);
    }

    public static BatchPaymentResult noPagado(UUID lote, String codigo, String motivo) {
      return new BatchPaymentResult(lote, false, null, null, null, codigo, motivo);
    }
  }
}
