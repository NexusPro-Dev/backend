package com.factech.nexus.modules.products.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * La clave del enlace: la pareja producto-tipo (`RN-PM-048`).
 *
 * <p>Es la misma forma que {@link PackageItemId} y por el mismo motivo: la unicidad «uno por tipo»
 * <b>es</b> la clave primaria, no un índice aparte. Un identificador propio permitiría dos filas
 * del mismo tipo sin que el esquema dijera nada.
 */
@Embeddable
public class ProductLinkId implements Serializable {

  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 30, updatable = false)
  private ProductLinkType type;

  protected ProductLinkId() {}

  public ProductLinkId(UUID productId, ProductLinkType type) {
    this.productId = productId;
    this.type = type;
  }

  public UUID getProductId() {
    return productId;
  }

  public ProductLinkType getType() {
    return type;
  }

  @Override
  public boolean equals(Object otro) {
    return otro instanceof ProductLinkId id
        && Objects.equals(productId, id.productId)
        && type == id.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(productId, type);
  }
}
