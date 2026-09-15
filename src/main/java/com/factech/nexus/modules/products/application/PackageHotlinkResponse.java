package com.factech.nexus.modules.products.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * El hotlink de un paquete (`RF-PM-026`): el vendedor y el paquete con su cuenta hecha, <b>sin
 * token</b>.
 *
 * <p><b>Compone lo que ya existe y no vuelve a decidir nada</b>: {@code seller} y {@code exchange}
 * son los del hotlink del producto, y cada línea lleva el producto en <b>la misma forma</b> —{@link
 * HotlinkResponse.ProductRef}, con su {@code rating}, su portada y su membresía— más su descuento y
 * su {@code priceInPackage}. <b>Sin {@code purchasePrice} ni {@code status}</b> en ningún nivel
 * (`RN-PM-043`): es la lectura pública, y lo que no se puede ofrecer no llega hasta aquí — responde
 * el mismo {@code 404}.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PackageHotlinkResponse(
    HotlinkResponse.SellerRef seller,
    // `package` es palabra reservada en Java; en el JSON se llama como la spec.
    @JsonProperty("package") PackageRef pkg) {

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record PackageRef(
      String code,
      String name,
      String description,
      HotlinkResponse.CurrencyRef currency,
      List<Item> items,
      BigDecimal listPrice,
      BigDecimal price,
      BigDecimal savings,
      ExchangeRef exchange) {}

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Item(
      HotlinkResponse.ProductRef product,
      PackageDetailResponse.DiscountRef discount,
      BigDecimal priceInPackage) {}
}
