package com.factech.nexus.modules.products.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Un paquete en la oferta (`RF-PM-007` v0.13.0): el que hoy se puede ofrecer <b>a esta persona</b>,
 * con la cuenta hecha y cada producto en <b>la forma de la oferta</b>, reutilizada tal cual.
 *
 * <p><b>Sin {@code purchasePrice} en ningún nivel</b> (`RN-PM-043`): {@link OfferItem} no lo tiene
 * y este tampoco. Lo que no se puede ofrecer no aparece y nada lo dice; el detalle administrativo
 * lo nombra.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OfferPackageItem(
    String code,
    String name,
    String description,
    String coverImageUrl,
    LocalDate validFrom,
    LocalDate validTo,
    ProductResponse.CurrencyRef currency,
    List<Line> items,
    BigDecimal listPrice,
    BigDecimal price,
    BigDecimal savings,
    ExchangeRef exchange) {

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Line(
      OfferItem product, PackageDetailResponse.DiscountRef discount, BigDecimal priceInPackage) {}
}
