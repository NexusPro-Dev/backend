package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.models.RateScope;
import com.factech.nexus.modules.commissions.domain.repository.CommissionRateQueryRepository.RateRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una fila del listado (`RF-CM-002`).
 *
 * <p>Lleva lo mismo que la respuesta del alta y ademas la <b>marca de retiro</b>: el listado si
 * puede devolver retiradas, marcadas, cuando se piden.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CommissionRateItem(
    UUID id,
    CommissionRateResponse.RoleRef role,
    CommissionRateResponse.ProductRef product,
    CommissionRateResponse.UserRef user,
    RateScope scope,
    BigDecimal percentage,
    LocalDate validFrom,
    LocalDate validTo,
    OffsetDateTime deletedAt) {

  public static CommissionRateItem from(RateRow fila) {
    CommissionRateResponse base = CommissionRateResponse.from(fila);
    return new CommissionRateItem(
        base.id(),
        base.role(),
        base.product(),
        base.user(),
        base.scope(),
        base.percentage(),
        base.validFrom(),
        base.validTo(),
        fila.deletedAt());
  }
}
