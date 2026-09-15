package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.domain.models.DiscountType;
import com.factech.nexus.modules.products.domain.models.DiscountValue;
import com.factech.nexus.modules.products.domain.models.PackageOfferability;
import com.factech.nexus.modules.products.domain.models.PackagePricing;
import com.factech.nexus.modules.products.domain.models.PackageStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Las lecturas del paquete (`RF-PM-017` a `RF-PM-019`).
 *
 * <p>Proyecciones planas y sin entidad, como {@link ProductQueryRepository}: la lectura no necesita
 * el agregado, y traer {@code ProductPackage} obligaría a resolver sus productos con sentencias
 * propias. <b>El precio no viene de aquí</b> (`RN-PM-036`): las filas traen el precio de catálogo
 * de cada producto y su descuento, y {@link PackagePricing} hace la cuenta en Java.
 */
public interface ProductPackageQueryRepository {

  /**
   * El paquete con sus filas de asociación y el producto de cada una, <b>en una sentencia</b>.
   * Retirado incluido: un retirado se devuelve marcado, no como inexistente.
   */
  Optional<PackageDetail> findDetail(UUID id);

  /** El paquete, con su moneda resuelta. */
  record PackageRow(
      UUID id,
      String code,
      String name,
      String description,
      UUID currencyId,
      String currencyCode,
      int currencyDecimalPlaces,
      String status,
      String scope,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime deletedAt) {

    public boolean retirado() {
      return deletedAt != null;
    }

    public boolean tieneDescripcion() {
      return description != null && !description.isBlank();
    }

    public PackageStatus estado() {
      return PackageStatus.valueOf(status);
    }
  }

  /**
   * Una fila de asociación con lo que la cuenta y la respuesta necesitan de su producto.
   *
   * <p>{@code productPurchasePrice} <b>solo lo selecciona la lectura de administración</b>
   * (`RN-PM-043`): la oferta y el hotlink lo dejan nulo, como {@code ProductRow}.
   */
  record PackageItemRow(
      UUID packageId,
      UUID productId,
      String productCode,
      String productName,
      String productType,
      String productStatus,
      OffsetDateTime productDeletedAt,
      BigDecimal productPrice,
      BigDecimal productPurchasePrice,
      UUID productCurrencyId,
      UUID productSourceMembershipId,
      String discountType,
      BigDecimal discountValue,
      OffsetDateTime createdAt) {

    public DiscountValue descuento() {
      return DiscountValue.leido(DiscountType.valueOf(discountType), discountValue);
    }

    public PackagePricing.Linea linea() {
      return new PackagePricing.Linea(productId, productPrice, descuento());
    }

    public PackageOfferability.Producto paraOfrecibilidad() {
      return new PackageOfferability.Producto(
          productCode, "ACTIVO".equals(productStatus), productDeletedAt != null);
    }
  }

  /** El paquete y sus filas, tal como salen de la sentencia. */
  record PackageDetail(PackageRow paquete, List<PackageItemRow> items) {

    public PackagePricing precio() {
      return PackagePricing.calcular(
          paquete.currencyDecimalPlaces(), items.stream().map(PackageItemRow::linea).toList());
    }

    public PackageOfferability ofrecibilidad() {
      return PackageOfferability.decidir(
          paquete.estado(),
          paquete.retirado(),
          paquete.tieneDescripcion(),
          items.stream().map(PackageItemRow::paraOfrecibilidad).toList());
    }
  }
}
