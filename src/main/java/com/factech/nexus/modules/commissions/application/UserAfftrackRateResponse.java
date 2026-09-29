package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.repository.UserAfftrackRateQueryRepository.UserAfftrackRateRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un escalón afftrack de persona tal como sale de la API (`RF-CM-019`): la persona, el producto con
 * su moneda, el escalón, cuánto paga al alcanzarse y su vigencia. {@code validTo} nulo es
 * «indefinidamente», y viaja siempre.
 */
@Schema(name = "UserAfftrackRate")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserAfftrackRateResponse(
    UUID id,
    UserRef user,
    AfftrackRateResponse.ProductRef product,
    int threshold,
    BigDecimal amountPerFtd,
    BigDecimal amountAtThreshold,
    LocalDate validFrom,
    LocalDate validTo,
    OffsetDateTime createdAt) {

  @Schema(name = "UserAfftrackRateUser")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record UserRef(UUID id, String username, String fullName) {}

  public static UserAfftrackRateResponse from(UserAfftrackRateRow fila) {
    return new UserAfftrackRateResponse(
        fila.id(),
        new UserRef(fila.userId(), fila.username(), fila.fullName()),
        new AfftrackRateResponse.ProductRef(
            fila.productId(),
            fila.productCode(),
            fila.productName(),
            new AfftrackRateResponse.CurrencyRef(
                fila.currencyId(), fila.currencyCode(), fila.currencyDecimalPlaces())),
        fila.threshold(),
        fila.amountPerFtd(),
        fila.amountAtThreshold(),
        fila.validFrom(),
        fila.validTo(),
        fila.createdAt());
  }
}
