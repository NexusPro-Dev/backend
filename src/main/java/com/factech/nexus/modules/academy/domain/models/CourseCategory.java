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
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Una categoría del catálogo de cursos (`RF-AC-001`): un cajón con nombre, color, icono y orden.
 *
 * <p><b>No tiene estado</b> (`RN-AC-008`, `requirements/ac.md` §5.2.6): está viva o retirada, y un
 * cajón vacío sale vacío. Tampoco tiene código: no se teclea en ninguna venta ni se imprime en
 * ningún comprobante, y su identificador basta.
 *
 * <p><b>Se pinta siempre con color e icono</b> (`RN-AC-003`), y por eso los dos son obligatorios y
 * ninguno admite vaciarse: a diferencia del upgrade de `PM`, que exige el icono <i>porque</i> no
 * hay portada, aquí la portada es un adorno opcional y no hay regla cruzada que comprobar al
 * quitarla. El color lo normaliza {@link CategoryColor} antes de comparar y antes de escribir.
 *
 * <p><b>El orden es una posición, no una identidad</b> (`RN-AC-002`): entero mayor o igual que
 * cero, no único, y corregirlo no mueve a las demás. Es a la vez agregado y modelo persistente,
 * como todo en este sistema desde `SP`.
 */
@Entity
@Table(name = "course_categories")
public class CourseCategory {

  private static final Pattern PATRON_ICONO = Pattern.compile("^[a-z][a-z0-9-]*$");
  private static final int NOMBRE_MAXIMO = 150;
  private static final int ICONO_MAXIMO = 50;

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "name", nullable = false, length = 150)
  private String name;

  @Column(name = "description")
  private String description;

  @Column(name = "color", nullable = false, length = 6)
  private String color;

  @Column(name = "icon", nullable = false, length = 50)
  private String icon;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  /**
   * La portada (`RN-AC-004`): la fila de {@code academy_images} cuyos bytes se sirven sin token.
   * Nulo significa «no tiene», y entonces el frontend pinta color e icono. <b>Nadie la escribe
   * hasta `RF-AC-006`</b>: la columna nace en `V18` para que la forma de la respuesta sea la
   * definitiva desde el primer día.
   */
  @Column(name = "cover_image_id")
  private UUID coverImageId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  /** Exigido por JPA. */
  protected CourseCategory() {}

  /**
   * Registra una categoría, viva, sin portada y sin cursos.
   *
   * <p>La forma de cada campo se comprueba aquí <b>además</b> de en el DTO: el DTO devuelve los
   * errores juntos (`CA-AC-004`), y el agregado es la red para cualquier otra vía de construcción.
   */
  public static CourseCategory create(
      UUID id,
      String name,
      String description,
      String color,
      String icon,
      Integer displayOrder,
      OffsetDateTime ahora) {
    CourseCategory categoria = new CourseCategory();
    categoria.id = id;
    categoria.name = verificarNombre(name);
    categoria.description = recortar(description);
    categoria.color = new CategoryColor(color).value();
    categoria.icon = verificarIcono(icon);
    categoria.displayOrder = verificarOrden(displayOrder);
    categoria.createdAt = ahora;
    categoria.updatedAt = ahora;
    return categoria;
  }

  /**
   * Corrige nombre, descripción, color, icono y orden, y devuelve qué cambió (`RF-AC-004`).
   *
   * <p>Como en `PM`: el diff lo devuelve quien aplica el cambio, los ausentes no se tocan, el nulo
   * explícito <b>vacía solo la descripción</b> —los otros cuatro llegan aquí ya rechazados por el
   * caso de uso—, y {@code updatedAt} se mueve únicamente si algo cambió. <b>El color se normaliza
   * antes de comparar</b>: {@code 1e88e5} sobre {@code 1E88E5} no es un cambio, y no se audita.
   */
  public Map<String, Object> update(
      Patchable<String> nuevoNombre,
      Patchable<String> nuevaDescripcion,
      Patchable<String> nuevoColor,
      Patchable<String> nuevoIcono,
      Patchable<Integer> nuevoOrden,
      OffsetDateTime ahora) {
    Map<String, Object> cambios = new LinkedHashMap<>();
    if (nuevoNombre.presente() && nuevoNombre.valor() != null) {
      String valor = verificarNombre(nuevoNombre.valor());
      if (!Objects.equals(valor, name)) {
        cambios.put("name", Map.of("before", texto(name), "after", texto(valor)));
        name = valor;
      }
    }
    if (nuevaDescripcion.presente()) {
      String valor = recortar(nuevaDescripcion.valor());
      if (!Objects.equals(valor, description)) {
        cambios.put("description", Map.of("before", texto(description), "after", texto(valor)));
        description = valor;
      }
    }
    if (nuevoColor.presente() && nuevoColor.valor() != null) {
      String valor = new CategoryColor(nuevoColor.valor()).value();
      if (!valor.equals(color)) {
        cambios.put("color", Map.of("before", color, "after", valor));
        color = valor;
      }
    }
    if (nuevoIcono.presente() && nuevoIcono.valor() != null) {
      String valor = verificarIcono(nuevoIcono.valor());
      if (!valor.equals(icon)) {
        cambios.put("icon", Map.of("before", icon, "after", valor));
        icon = valor;
      }
    }
    if (nuevoOrden.presente() && nuevoOrden.valor() != null) {
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

  /**
   * Retira la categoría (`RF-AC-005`). Devuelve si hubo cambio; nada más de la fila cambia, y sus
   * clasificaciones permanecen (`RN-AC-018`).
   */
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

  /**
   * La misma instantánea para la creación y para el retiro: el registro de creación y el de
   * eliminación describen la misma categoría con las mismas claves. El retiro le añade los cursos.
   */
  public Map<String, Object> instantanea() {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("name", name);
    estado.put("description", description);
    estado.put("color", color);
    estado.put("icon", icon);
    estado.put("display_order", displayOrder);
    estado.put("cover_image_id", coverImageId == null ? null : coverImageId.toString());
    return estado;
  }

  private static String verificarNombre(String valor) {
    String recortado = recortar(valor);
    if (recortado == null || recortado.length() > NOMBRE_MAXIMO) {
      String mensaje = "El nombre es obligatorio y no puede superar los 150 caracteres.";
      throw new ValidationException(
          "VAL-001", mensaje, List.of(new FieldError("name", "VAL-001", mensaje)));
    }
    return recortado;
  }

  private static String verificarIcono(String valor) {
    String recortado = recortar(valor);
    if (recortado == null
        || recortado.length() > ICONO_MAXIMO
        || !PATRON_ICONO.matcher(recortado).matches()) {
      String mensaje =
          "El icono es obligatorio, solo admite minúsculas, dígitos y guion medio, debe empezar por"
              + " letra y no puede exceder 50 caracteres.";
      throw new ValidationException(
          "VAL-003", mensaje, List.of(new FieldError("icon", "VAL-003", mensaje)));
    }
    return recortado;
  }

  private static int verificarOrden(Integer valor) {
    if (valor == null || valor < 0) {
      String mensaje = "El orden es obligatorio y debe ser un entero mayor o igual que cero.";
      throw new ValidationException(
          "VAL-004", mensaje, List.of(new FieldError("displayOrder", "VAL-004", mensaje)));
    }
    return valor;
  }

  /** El nulo se audita como cadena vacía: {@code Map.of} no admite nulos. */
  private static String texto(String valor) {
    return valor == null ? "" : valor;
  }

  private static String recortar(String valor) {
    if (valor == null) {
      return null;
    }
    String recortado = valor.trim();
    return recortado.isEmpty() ? null : recortado;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getColor() {
    return color;
  }

  public String getIcon() {
    return icon;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public UUID getCoverImageId() {
    return coverImageId;
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
