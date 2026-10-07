package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.application.CommissionBatchDetailResponse.CommissionLine;
import com.factech.nexus.modules.commissions.application.CommissionBatchItem.BatchCurrency;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchQueryRepository.OwnCommissionRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Una de mis comisiones, sin pasar por el lote (`RF-CM-026`).
 *
 * <p><b>{@code commission} es la del detalle de un lote</b> (`RF-CM-012`), sin copiar sus campos:
 * el día que la comisión gane uno, sale aquí también. <b>{@code batch}</b> dice en qué lote está y
 * en qué estado, que es si está por cobrar o cobrada. <b>{@code client}</b> es el de la venta, y
 * nulo en una {@code POR_AFFTRACK}, que no sale de ninguna.
 */
@Schema(name = "MyCommissionItem")
public record MyCommissionItem(
    CommissionLine commission, MyCommissionBatch batch, BatchCurrency currency, Client client) {

  public static MyCommissionItem from(OwnCommissionRow f) {
    return new MyCommissionItem(
        CommissionLine.from(f.commission()),
        new MyCommissionBatch(f.batchId(), f.batchCode(), f.batchStatus().name()),
        new BatchCurrency(f.currencyId(), f.currencyCode()),
        f.clientId() == null
            ? null
            : new Client(
                f.clientId(), f.clientUsername(), nombre(f.clientFirstName(), f.clientLastName())));
  }

  private static String nombre(String nombre, String apellido) {
    return ((nombre == null ? "" : nombre) + " " + (apellido == null ? "" : apellido)).trim();
  }

  /** El lote en que está la comisión, y su estado. */
  @Schema(name = "MyCommissionBatch")
  public record MyCommissionBatch(UUID id, String code, String status) {}

  /** A quién se le vendió. */
  @Schema(name = "MyCommissionClient")
  public record Client(UUID id, String username, String fullName) {}
}
