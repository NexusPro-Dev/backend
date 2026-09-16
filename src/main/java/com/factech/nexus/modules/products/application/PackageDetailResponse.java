package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.DiscountType;
import com.factech.nexus.modules.products.domain.models.PackageOfferability;
import com.factech.nexus.modules.products.domain.models.PackagePricing;
import com.factech.nexus.modules.products.domain.models.PackageStatus;
import com.factech.nexus.modules.products.domain.models.ProductScope;
import com.factech.nexus.modules.products.domain.models.ProductStatus;
import com.factech.nexus.modules.products.domain.models.ProductType;
import com.factech.nexus.modules.products.domain.repository.ProductPackageQueryRepository.PackageDetail;
import com.factech.nexus.modules.products.domain.repository.ProductPackageQueryRepository.PackageItemRow;
import com.factech.nexus.modules.products.domain.repository.ProductPackageQueryRepository.PackageRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * El paquete como lo ve administración (`RF-PM-017`, `RF-PM-019`), <b>con la cuenta hecha</b>.
 *
 * <p>Es la respuesta del alta y del detalle, y la que devuelven todas las escrituras del paquete:
 * quien acaba de tocarlo ve su precio nuevo sin volver a pedirlo. <b>Los tres importes viajan como
 * número</b> con los decimales de la moneda, como {@code price} del producto, y con la misma
 * advertencia: ningún total calculado en el navegador es el que se cobre.
 *
 * <p><b>{@code offerable} y {@code offerableReason} siempre presentes</b>: {@code true} con motivo
 * nulo; {@code false} con el <b>primer</b> motivo en el orden fijo de {@link PackageOfferability}.
 * <b>{@code purchasePrice} presente y nulo</b> cuando no se conoce: es la lectura de administración
 * (`RN-PM-043`), y las dos públicas no lo llevan.
 *
 * <p><b>{@code coverImageUrl} siempre presente</b> (`RN-PM-045`): la dirección de la portada del
 * paquete, o nula — y entonces el frontend pinta el icono de promoción y el color por omisión.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PackageDetailResponse(
    UUID id,
    String code,
    String name,
    String description,
    String coverImageUrl,
    ProductResponse.CurrencyRef currency,
    ProductScope scope,
    PackageStatus status,
    List<PackageItemResponse> items,
    BigDecimal listPrice,
    BigDecimal price,
    BigDecimal savings,
    ExchangeRef exchange,
    boolean offerable,
    String offerableReason,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) OffsetDateTime deletedAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) String deletionReason) {

  /** Una línea del paquete: el producto, su descuento y lo que vale dentro. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record PackageItemResponse(
      PackageProductRef product, DiscountRef discount, BigDecimal priceInPackage) {}

  /**
   * Lo que el detalle dice de cada producto. <b>Con {@code status} y {@code deleted}</b>: un
   * producto inactivo o retirado se devuelve igual, porque esta es la única pantalla desde la que
   * se arregla (`FA-002` de `RF-PM-019`).
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record PackageProductRef(
      UUID id,
      String code,
      String name,
      ProductType type,
      ProductStatus status,
      boolean deleted,
      BigDecimal price,
      BigDecimal purchasePrice) {}

  /**
   * Forma y valor, con la escala de su forma: dos decimales el porcentaje, los de la moneda el
   * fijo.
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record DiscountRef(DiscountType type, BigDecimal value) {}

  public static PackageDetailResponse from(
      PackageDetail detalle,
      PackagePricing cuenta,
      PackageOfferability ofrecibilidad,
      ExchangeRef conversion,
      String motivoDelRetiro) {
    PackageRow paquete = detalle.paquete();
    int decimales = paquete.currencyDecimalPlaces();
    List<PackageItemResponse> items =
        detalle.items().stream().map(fila -> linea(fila, cuenta, decimales)).toList();
    return new PackageDetailResponse(
        paquete.id(),
        paquete.code(),
        paquete.name(),
        paquete.description(),
        ProductImageUrls.de(paquete.coverImageId()),
        new ProductResponse.CurrencyRef(paquete.currencyId(), paquete.currencyCode(), decimales),
        ProductScope.valueOf(paquete.scope()),
        paquete.estado(),
        items,
        cuenta.listPrice(),
        cuenta.price(),
        cuenta.savings(),
        conversion,
        ofrecibilidad.offerable(),
        ofrecibilidad.reason(),
        enUtc(paquete.createdAt()),
        enUtc(paquete.updatedAt()),
        enUtc(paquete.deletedAt()),
        motivoDelRetiro);
  }

  private static PackageItemResponse linea(
      PackageItemRow fila, PackagePricing cuenta, int decimales) {
    return new PackageItemResponse(
        new PackageProductRef(
            fila.productId(),
            fila.productCode(),
            fila.productName(),
            ProductType.valueOf(fila.productType()),
            ProductStatus.valueOf(fila.productStatus()),
            fila.productDeletedAt() != null,
            ProductPrice.enLaEscalaDe(fila.productPrice(), decimales),
            fila.productPurchasePrice() == null
                ? null
                : ProductPrice.enLaEscalaDe(fila.productPurchasePrice(), decimales)),
        new DiscountRef(fila.descuento().getType(), fila.descuento().valorEnEscala(decimales)),
        cuenta.precioDe(fila.productId()));
  }

  private static OffsetDateTime enUtc(OffsetDateTime instante) {
    return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
