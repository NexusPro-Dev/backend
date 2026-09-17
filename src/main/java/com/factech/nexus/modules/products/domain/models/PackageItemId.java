package com.factech.nexus.modules.products.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** La clave de la asociación: la pareja paquete-producto (`RN-PM-038`). */
@Embeddable
public class PackageItemId implements Serializable {

  @Column(name = "package_id", nullable = false, updatable = false)
  private UUID packageId;

  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  protected PackageItemId() {}

  public PackageItemId(UUID packageId, UUID productId) {
    this.packageId = packageId;
    this.productId = productId;
  }

  public UUID getPackageId() {
    return packageId;
  }

  public UUID getProductId() {
    return productId;
  }

  @Override
  public boolean equals(Object otro) {
    return otro instanceof PackageItemId id
        && Objects.equals(packageId, id.packageId)
        && Objects.equals(productId, id.productId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(packageId, productId);
  }
}
