package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.domain.models.PackageItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Las filas de asociación paquete-producto (`RF-PM-023` a `RF-PM-025`).
 *
 * <p>La fila se lee y se escribe <b>bajo el bloqueo del paquete</b>, que toma el caso de uso con
 * {@link ProductPackageRepository#findAliveByIdForUpdate}: por eso aquí ninguna lectura bloquea. Y
 * la clave primaria es la red de la unicidad (`RN-PM-038`) si alguien quitara ese bloqueo — se
 * traduce igual, por si acaso.
 */
public interface PackageItemRepository {

  /** Inserta traduciendo {@code pk_product_package_items} al `409` de `EX-005`. */
  PackageItem save(PackageItem fila);

  Optional<PackageItem> find(UUID packageId, UUID productId);

  /** Las filas del paquete, en el orden en que entraron. */
  List<PackageItem> findByPackage(UUID packageId);

  /**
   * Lo que el caso de uso necesita de <b>las hermanas</b> para decidir en una sola lectura: si el
   * producto ya está (`EX-005`) y si el paquete ya tiene su upgrade (`RN-PM-046`, `EX-007`). El
   * código viaja para nombrarlo en el rechazo sin una lectura más.
   */
  List<Hermana> findSiblings(UUID packageId);

  void delete(PackageItem fila);

  /** Vacía los cambios pendientes traduciendo la clave, para quien escribe sin {@link #save}. */
  void flush();

  record Hermana(
      UUID productId,
      String productCode,
      String productType,
      UUID sourceMembershipId,
      BigDecimal price) {

    public boolean esUpgrade() {
      return "UPGRADE_MEMBRESIA".equals(productType);
    }
  }
}
