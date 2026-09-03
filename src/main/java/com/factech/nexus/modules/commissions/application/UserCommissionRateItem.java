package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import com.factech.nexus.modules.commissions.domain.repository.UserCommissionRateQueryRepository.UserRateRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una fila del listado de tasas personalizadas (`RF-CM-002`).
 *
 * <p>Lleva lo mismo que la respuesta del alta y además la <b>marca de retiro</b>: el listado sí
 * puede devolver retiradas, marcadas, cuando se piden.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserCommissionRateItem(
    UUID id,
    UserCommissionRateResponse.UserRef user,
    CommissionRateType rateType,
    BigDecimal percentage,
    BigDecimal fixedAmount,
    LocalDate validFrom,
    LocalDate validTo,
    OffsetDateTime deletedAt) {

  public static UserCommissionRateItem from(UserRateRow fila) {
    UserCommissionRateResponse base = UserCommissionRateResponse.from(fila);
    return new UserCommissionRateItem(
        base.id(),
        base.user(),
        base.rateType(),
        base.percentage(),
        base.fixedAmount(),
        base.validFrom(),
        base.validTo(),
        fila.deletedAt());
  }
}
