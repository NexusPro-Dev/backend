package com.factech.nexus.modules.academy.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Un servicio que abre un curso (`RF-AC-037`, `RN-AC-020`).
 *
 * <p><b>Es una asociación</b> (Art. V.13), como la clasificación: la pareja es la clave, se borra
 * físicamente al quitarla (`RF-AC-038`) y su baja se registra como {@code ASSOCIATION} sin motivo.
 * <b>Que el producto sea un {@code BOT} no lo sabe esta fila</b>: lo comprobó el caso de uso al
 * añadirla, contra el puerto de `PM`, y el tipo de un producto no cambia (`RN-PM-001`).
 */
@Entity
@Table(name = "course_products")
public class CourseProduct {

  @EmbeddedId private CourseProductId id;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  /** Exigido por JPA. */
  protected CourseProduct() {}

  public static CourseProduct create(UUID courseId, UUID productId, OffsetDateTime ahora) {
    CourseProduct fila = new CourseProduct();
    fila.id = new CourseProductId(courseId, productId);
    fila.createdAt = ahora;
    return fila;
  }

  /**
   * La instantánea de auditoría, <b>con el código del producto</b>: es como se nombra un producto
   * en todo el sistema, y no cambia (`RN-PM-013`).
   */
  public Map<String, Object> instantanea(String codigoDelProducto) {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("course_id", id.getCourseId().toString());
    estado.put("product_id", id.getProductId().toString());
    estado.put("product_code", codigoDelProducto);
    return estado;
  }

  public CourseProductId getId() {
    return id;
  }

  public UUID getCourseId() {
    return id.getCourseId();
  }

  public UUID getProductId() {
    return id.getProductId();
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
