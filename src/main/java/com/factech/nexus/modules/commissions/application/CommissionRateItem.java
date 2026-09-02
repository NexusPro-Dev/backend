package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.repository.CommissionRateQueryRepository.RateRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una fila del listado del catálogo (`RF-CM-002`).
 *
 * <p>Lleva lo mismo que la respuesta del alta y además la <b>marca de retiro</b>: el listado sí
 * puede devolver retiradas, marcadas, cuando se piden.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CommissionRateItem(
    UUID id,
    CommissionRateResponse.RoleRef role,
    BigDecimal percentage,
    long associatedProducts,
    OffsetDateTime deletedAt) {

  public static CommissionRateItem from(RateRow fila) {
    CommissionRateResponse base = CommissionRateResponse.from(fila);
    return new CommissionRateItem(
        base.id(), base.role(), base.percentage(), base.associatedProducts(), fila.deletedAt());
  }
}
