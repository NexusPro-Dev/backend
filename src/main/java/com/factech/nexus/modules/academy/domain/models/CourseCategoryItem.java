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
 * Un curso dentro de una categoría (`RF-AC-016`).
 *
 * <p><b>Es una asociación</b> (Art. V.13), como el producto dentro del paquete: la pareja es la
 * clave, se borra físicamente al desclasificar (`RF-AC-017`) y su baja se registra como {@code
 * ASSOCIATION} sin motivo. No lleva nada más que la pareja: <b>el orden es global</b> (`RN-AC-002`)
 * y no hay un orden del curso dentro de su categoría.
 */
@Entity
@Table(name = "course_category_items")
public class CourseCategoryItem {

  @EmbeddedId private CourseCategoryItemId id;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  /** Exigido por JPA. */
  protected CourseCategoryItem() {}

  public static CourseCategoryItem create(UUID courseId, UUID categoryId, OffsetDateTime ahora) {
    CourseCategoryItem fila = new CourseCategoryItem();
    fila.id = new CourseCategoryItemId(courseId, categoryId);
    fila.createdAt = ahora;
    return fila;
  }

  /**
   * La instantánea de auditoría, <b>con el nombre de la categoría</b>: el identificador solo no
   * dice nada a quien lea el registro después de que la renombren.
   */
  public Map<String, Object> instantanea(String nombreDeLaCategoria) {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("course_id", id.getCourseId().toString());
    estado.put("category_id", id.getCategoryId().toString());
    estado.put("category_name", nombreDeLaCategoria);
    return estado;
  }

  public CourseCategoryItemId getId() {
    return id;
  }

  public UUID getCourseId() {
    return id.getCourseId();
  }

  public UUID getCategoryId() {
    return id.getCategoryId();
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
