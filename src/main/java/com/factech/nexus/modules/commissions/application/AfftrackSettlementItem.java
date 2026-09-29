package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.repository.AfftrackSettlementQueryRepository.SettlementRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lo que un cierre hizo con los FTD de una persona y un producto (`RF-CM-021`).
 *
 * <p>{@code carriedIn + newFtds − paidFtds = carriedOut}, y el esquema lo garantiza. {@code tier} y
 * {@code amount} son nulos si no alcanzó ningún escalón, y viajan igual.
 */
@Schema(name = "AfftrackSettlement")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AfftrackSettlementItem(
    UUID id,
    Closing closing,
    UserAfftrackRateResponse.UserRef user,
    AfftrackRateResponse.ProductRef product,
    int carriedIn,
    int newFtds,
    int ownFtds,
    int networkFtds,
    int paidFtds,
    int carriedOut,
    Tier tier,
    BigDecimal amount) {

  @Schema(name = "AfftrackSettlementClosing")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Closing(UUID id, OffsetDateTime closedAt) {}

  /** El escalón que se pagó, tal como se pagó. */
  @Schema(name = "AfftrackSettlementTier")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Tier(int threshold, BigDecimal amountPerFtd, String source) {}

  public static AfftrackSettlementItem from(SettlementRow f) {
    return new AfftrackSettlementItem(
        f.id(),
        new Closing(f.closingId(), f.closedAt()),
        new UserAfftrackRateResponse.UserRef(f.userId(), f.username(), f.fullName()),
        new AfftrackRateResponse.ProductRef(
            f.productId(),
            f.productCode(),
            f.productName(),
            new AfftrackRateResponse.CurrencyRef(
                f.currencyId(), f.currencyCode(), f.currencyDecimalPlaces())),
        f.carriedIn(),
        f.newFtds(),
        f.ownFtds(),
        f.networkFtds(),
        f.paidFtds(),
        f.carriedOut(),
        f.source() == null ? null : new Tier(f.paidFtds(), f.amountPerFtd(), f.source()),
        f.amount());
  }
}
