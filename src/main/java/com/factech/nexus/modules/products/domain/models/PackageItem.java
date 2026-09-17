package com.factech.nexus.modules.products.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Un producto dentro de un paquete, con su descuento (`RF-PM-023`).
 *
 * <p><b>Es una asociación</b> (Art. V.13): la pareja es la clave, se borra físicamente al
 * desasociar y su baja se registra como {@code ASSOCIATION} sin motivo (`RN-PM-042`). El descuento
 * es <b>del paquete</b>: el mismo producto en otro paquete lleva otro.
 *
 * <p><b>No guarda el precio del producto</b>: el precio dentro del paquete se calcula con el de hoy
 * (`RN-PM-036`). Lo único que lleva el precio es la {@link #instantanea instantánea} de auditoría,
 * porque es el único sitio donde queda escrito contra qué precio se aprobó el descuento.
 */
@Entity
@Table(name = "product_package_items")
public class PackageItem {

  @EmbeddedId private PackageItemId id;

  @Embedded private DiscountValue discount;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** Exigido por JPA. */
  protected PackageItem() {}

  public static PackageItem create(
      UUID packageId, UUID productId, DiscountValue discount, OffsetDateTime ahora) {
    PackageItem fila = new PackageItem();
    fila.id = new PackageItemId(packageId, productId);
    fila.discount = discount;
    fila.createdAt = ahora;
    fila.updatedAt = ahora;
    return fila;
  }

  /**
   * Corrige forma y valor juntos (`RF-PM-024`) y devuelve el diff, con {@code type} y {@code value}
   * cada uno con su antes y su después. Vacío si nada cambió de valor.
   */
  public Map<String, Object> corregir(DiscountValue nuevo, OffsetDateTime ahora) {
    Map<String, Object> cambios = new LinkedHashMap<>();
    if (discount.mismoValorQue(nuevo)) {
      return cambios;
    }
    cambios.put(
        "type", Map.of("before", discount.getType().name(), "after", nuevo.getType().name()));
    cambios.put(
        "value",
        Map.of(
            "before", discount.getValue().toPlainString(),
            "after", nuevo.getValue().toPlainString()));
    discount = nuevo;
    updatedAt = ahora;
    return cambios;
  }

  /**
   * La instantánea de auditoría, <b>con el precio del producto en ese instante</b>: es lo único que
   * deja escrito contra qué precio se aprobó el descuento.
   */
  public Map<String, Object> instantanea(BigDecimal precioDelProducto) {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("package_id", id.getPackageId().toString());
    estado.put("product_id", id.getProductId().toString());
    estado.put("discount_type", discount.getType().name());
    estado.put("discount_value", discount.getValue().toPlainString());
    estado.put(
        "product_price", precioDelProducto == null ? null : precioDelProducto.toPlainString());
    return estado;
  }

  public PackageItemId getId() {
    return id;
  }

  public UUID getPackageId() {
    return id.getPackageId();
  }

  public UUID getProductId() {
    return id.getProductId();
  }

  public DiscountValue getDiscount() {
    return discount;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
