package com.factech.nexus.modules.commissions.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Mi avance de FTD en el periodo abierto (`RF-CM-030`): lo que el próximo cierre me liquidaría si
 * corriera ahora (`RN-CM-056`). {@code periodStart} es nulo sin cierres y {@code nextClosingAt} con
 * el cierre programado apagado.
 */
@Schema(name = "AfftrackProgress")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AfftrackProgressResponse(
    OffsetDateTime periodStart, OffsetDateTime nextClosingAt, List<Product> products) {

  /**
   * Un producto FTD. {@code carriedIn + newFtds = available}, {@code ownFtds + networkFtds =
   * newFtds}; sin escalón alcanzado {@code remainder = available}.
   */
  @Schema(name = "AfftrackProgressProduct")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Product(
      AfftrackRateResponse.ProductRef product,
      int carriedIn,
      int ownFtds,
      int networkFtds,
      int newFtds,
      int available,
      ReachedTier reachedTier,
      int remainder,
      NextTier nextTier) {}

  /** El escalón que se pagaría hoy; {@code estimatedAmount = threshold × amountPerFtd}. */
  @Schema(name = "AfftrackProgressReachedTier")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record ReachedTier(
      int threshold, BigDecimal amountPerFtd, String source, BigDecimal estimatedAmount) {}

  /** El escalón siguiente y cuántos FTD faltan para alcanzarlo. */
  @Schema(name = "AfftrackProgressNextTier")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record NextTier(int threshold, BigDecimal amountPerFtd, String source, int missing) {}
}
