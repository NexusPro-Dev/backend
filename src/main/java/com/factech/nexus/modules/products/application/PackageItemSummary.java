package com.factech.nexus.modules.products.application;

import com.factech.nexus.modules.products.domain.models.PackageOfferability;
import com.factech.nexus.modules.products.domain.models.PackagePricing;
import com.factech.nexus.modules.products.domain.models.PackageStatus;
import com.factech.nexus.modules.products.domain.models.ProductScope;
import com.factech.nexus.modules.products.domain.repository.ProductPackageQueryRepository.PackageDetail;
import com.factech.nexus.modules.products.domain.repository.ProductPackageQueryRepository.PackageRow;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Una fila del listado de paquetes (`RF-PM-018` §6.2): el paquete con su cuenta hecha y cuántos
 * productos tiene, sin las líneas.
 *
 * <p><b>{@code offerable} es columna y no filtro</b>, y viaja sin motivo: el motivo lo da el
 * detalle, que es donde se arregla. Los tres importes y la conversión salen de los mismos {@code
 * PackagePricing} y {@code ProductExchangeResolver} que el detalle, y por eso cuadran con él
 * (`CA-PM-269`).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PackageItemSummary(
    UUID id,
    String code,
    String name,
    String coverImageUrl,
    ProductResponse.CurrencyRef currency,
    ProductScope scope,
    PackageStatus status,
    LocalDate validFrom,
    LocalDate validTo,
    int itemCount,
    BigDecimal listPrice,
    BigDecimal price,
    BigDecimal savings,
    ExchangeRef exchange,
    boolean offerable,
    OffsetDateTime createdAt,
    @JsonInclude(JsonInclude.Include.NON_NULL) OffsetDateTime deletedAt) {

  public static PackageItemSummary from(
      PackageDetail detalle, ExchangeRef conversion, LocalDate hoy) {
    PackageRow paquete = detalle.paquete();
    PackagePricing cuenta = detalle.precio();
    PackageOfferability ofrecibilidad = detalle.ofrecibilidad(hoy);
    return new PackageItemSummary(
        paquete.id(),
        paquete.code(),
        paquete.name(),
        ProductImageUrls.de(paquete.coverImageId()),
        new ProductResponse.CurrencyRef(
            paquete.currencyId(), paquete.currencyCode(), paquete.currencyDecimalPlaces()),
        ProductScope.valueOf(paquete.scope()),
        paquete.estado(),
        paquete.validFrom(),
        paquete.validTo(),
        detalle.items().size(),
        cuenta.listPrice(),
        cuenta.price(),
        cuenta.savings(),
        conversion,
        ofrecibilidad.offerable(),
        enUtc(paquete.createdAt()),
        enUtc(paquete.deletedAt()));
  }

  private static OffsetDateTime enUtc(OffsetDateTime instante) {
    return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
