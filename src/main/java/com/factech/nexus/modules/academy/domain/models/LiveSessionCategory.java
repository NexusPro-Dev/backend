package com.factech.nexus.modules.academy.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Una categoría de las clases en vivo (`RF-AC-063` a `RF-AC-066`, `RN-AC-034`): nombre y orden.
 *
 * <p>Es {@link CourseCategory} sin color, icono ni portada: un rótulo de la agenda y no un cajón
 * del catálogo. <b>Está viva o retirada</b>, y retirarla no toca las clases que la tienen
 * (`RN-AC-018`). La unicidad del nombre entre las vivas la comprueba el caso de uso y la garantiza
 * el índice parcial.
 */
@Entity
@Table(name = "live_session_categories")
public class LiveSessionCategory {

  private static final int NOMBRE_MAXIMO = 100;

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  /** Exigido por JPA. */
  protected LiveSessionCategory() {}

  public static LiveSessionCategory create(
      UUID id, String name, Integer displayOrder, OffsetDateTime ahora) {
    LiveSessionCategory categoria = new LiveSessionCategory();
    categoria.id = id;
    categoria.name = verificarNombre(name);
    categoria.displayOrder = verificarOrden(displayOrder);
    categoria.createdAt = ahora;
    categoria.updatedAt = ahora;
    return categoria;
  }

  /**
   * Corrige nombre y orden y devuelve qué cambió. Ninguno se vacía: el nulo explícito se rechaza
   * como el valor inválido. {@code updatedAt} se mueve solo si algo cambió.
   */
  public Map<String, Object> update(
      Patchable<String> nuevoNombre, Patchable<Integer> nuevoOrden, OffsetDateTime ahora) {
    Map<String, Object> cambios = new LinkedHashMap<>();
    if (nuevoNombre.presente()) {
      String valor = verificarNombre(nuevoNombre.valor());
      if (!valor.equals(name)) {
        cambios.put("name", Map.of("before", name, "after", valor));
        name = valor;
      }
    }
    if (nuevoOrden.presente()) {
      int valor = verificarOrden(nuevoOrden.valor());
      if (valor != displayOrder) {
        cambios.put("display_order", Map.of("before", displayOrder, "after", valor));
        displayOrder = valor;
      }
    }
    if (!cambios.isEmpty()) {
      updatedAt = ahora;
    }
    return cambios;
  }

  /** Retira la categoría; devuelve si hubo cambio. Las clases que la tienen no se tocan. */
  public boolean delete(OffsetDateTime ahora) {
    if (deletedAt != null) {
      return false;
    }
    deletedAt = ahora;
    updatedAt = ahora;
    return true;
  }

  public boolean estaRetirada() {
    return deletedAt != null;
  }

  /** La misma instantánea para el alta y para el retiro. */
  public Map<String, Object> instantanea() {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("name", name);
    estado.put("display_order", displayOrder);
    return estado;
  }

  /** `VAL-001`: obligatorio, de 1 a 100 caracteres tras recortar. */
  public static String verificarNombre(String valor) {
    String recortado = valor == null ? "" : valor.trim();
    if (recortado.isEmpty() || recortado.length() > NOMBRE_MAXIMO) {
      String mensaje = "El nombre es obligatorio y no puede superar los 100 caracteres.";
      throw new ValidationException(
          "VAL-001", mensaje, List.of(new FieldError("name", "VAL-001", mensaje)));
    }
    return recortado;
  }

  /** `VAL-002`: obligatorio, entero mayor o igual que cero. */
  public static int verificarOrden(Integer valor) {
    if (valor == null || valor < 0) {
      String mensaje = "El orden es obligatorio y debe ser un entero mayor o igual que cero.";
      throw new ValidationException(
          "VAL-002", mensaje, List.of(new FieldError("displayOrder", "VAL-002", mensaje)));
    }
    return valor;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public OffsetDateTime getDeletedAt() {
    return deletedAt;
  }
}
