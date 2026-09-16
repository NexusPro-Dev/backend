package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.application.ListPackagesRequest;
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

  /**
   * La página de paquetes (`RF-PM-018`), <b>sin sus filas</b>: solo la tabla y la moneda. El orden
   * llega resuelto por {@code PackageSortField}, y el de precio ordena por una subconsulta porque
   * el precio no está en ninguna columna.
   */
  List<PackageRow> search(ListPackagesRequest filtros, String ordenamiento, int offset, int limit);

  long count(ListPackagesRequest filtros);

  /**
   * <b>Todas</b> las filas de asociación de esos paquetes con su producto, en <b>una</b> sentencia
   * (`CA-PM-274`): es lo que impide que la página cueste una consulta por paquete.
   */
  List<PackageItemRow> findItemsOf(List<UUID> packageIds);

  /**
   * El paquete publicado por su código (`RF-PM-026`): <b>activo, vivo y de alcance `HOTLINK` o
   * `AMBOS`</b>, comparado sin distinguir mayúsculas, con sus productos como {@link
   * ProductQueryRepository.ProductRow} —la misma fila y la misma forma que el hotlink del producto,
   * `rating` incluido— más forma y valor de su descuento, <b>en una sentencia</b>. Vacío si el
   * paquete no cumple; la ofrecibilidad <b>no</b> se decide aquí, sino en Java, para que
   * `RN-PM-039` viva en un solo sitio.
   */
  Optional<PublishedPackage> findPublishedByCode(String code);

  /**
   * Los paquetes de la oferta (`RF-PM-007` v0.13.0): <b>activos, vivos y de alcance `TIENDA` o
   * `AMBOS`</b>, con sus productos en la forma de la oferta, por fecha de alta, <b>en una
   * sentencia</b>. Ni la ofrecibilidad ni el origen se deciden aquí: los decide el caso de uso
   * sobre las filas, para que `RN-PM-039` y `RN-PM-044` vivan en un solo sitio.
   */
  List<PublishedPackage> findOfferable();

  /** Una línea del paquete publicado: el producto en su forma pública y su descuento. */
  /**
   * Una línea del paquete publicado: el producto en su forma pública y su descuento. {@code
   * retirado} viaja aparte porque la proyección pública deja {@code deletedAt} nulo a propósito.
   */
  record PublishedItem(
      ProductQueryRepository.ProductRow producto, DiscountValue descuento, boolean retirado) {

    public PackagePricing.Linea linea() {
      return new PackagePricing.Linea(producto.id(), producto.price(), descuento);
    }

    public PackageOfferability.Producto paraOfrecibilidad() {
      return new PackageOfferability.Producto(
          producto.code(), "ACTIVO".equals(producto.status()), retirado);
    }
  }

  /** El paquete publicado con sus líneas, tal como salen de la sentencia. */
  record PublishedPackage(PackageRow paquete, List<PublishedItem> items) {

    public PackagePricing precio() {
      return PackagePricing.calcular(
          paquete.currencyDecimalPlaces(), items.stream().map(PublishedItem::linea).toList());
    }

    public PackageOfferability ofrecibilidad() {
      return PackageOfferability.decidir(
          paquete.estado(),
          paquete.retirado(),
          paquete.tieneDescripcion(),
          items.stream().map(PublishedItem::paraOfrecibilidad).toList());
    }
  }

  /**
   * El paquete, con su moneda resuelta.
   *
   * <p>{@code coverImageId} es el identificador y nada más (`RN-PM-045`): las lecturas lo
   * convierten en una dirección, y {@code product_images} no se une nunca.
   */
  record PackageRow(
      UUID id,
      String code,
      String name,
      String description,
      UUID coverImageId,
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
