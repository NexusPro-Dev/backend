package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.repository.UserAfftrackRateQueryRepository.UserAfftrackRateRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un escalón de persona en el listado: la forma de la respuesta y, si está retirado, desde cuándo.
 */
@Schema(name = "UserAfftrackRateItem")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserAfftrackRateItem(
    UUID id,
    UserAfftrackRateResponse.UserRef user,
    AfftrackRateResponse.ProductRef product,
    int threshold,
    BigDecimal amountPerFtd,
    BigDecimal amountAtThreshold,
    LocalDate validFrom,
    LocalDate validTo,
    OffsetDateTime deletedAt) {

  public static UserAfftrackRateItem from(UserAfftrackRateRow fila) {
    UserAfftrackRateResponse base = UserAfftrackRateResponse.from(fila);
    return new UserAfftrackRateItem(
        base.id(),
        base.user(),
        base.product(),
        base.threshold(),
        base.amountPerFtd(),
        base.amountAtThreshold(),
        base.validFrom(),
        base.validTo(),
        fila.deletedAt());
  }
}
