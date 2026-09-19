package com.factech.nexus.modules.academy.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Una parte de un curso (`RF-AC-022`): título, descripciones, video de presentación, orden, estado
 * y portada, <b>dentro de un curso del que no se mueve</b> (`RN-AC-019`): {@code courseId} no tiene
 * mutador.
 *
 * <p>Hereda del curso la forma —título único dentro del padre, descripciones que se recortan y se
 * vacían en cualquier estado, video como enlace, nace {@code INACTIVO}— y no hereda el instructor
 * ni la dificultad, que son del curso. Activar exige una lección activa (`RN-AC-009`), y eso lo
 * comprueba el caso de uso con la cuenta que este agregado no tiene.
 */
@Entity
@Table(name = "course_modules")
public class CourseModule {

  private static final int TITULO_MAXIMO = 150;
  private static final int CORTA_MAXIMA = 300;
  private static final int LARGA_MAXIMA = 10_000;

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "course_id", nullable = false, updatable = false)
  private UUID courseId;

  @Column(name = "title", nullable = false, length = 150)
  private String title;

  @Column(name = "short_description", length = 300)
  private String shortDescription;

  @Column(name = "long_description")
  private String longDescription;

  @Column(name = "presentation_video_url", length = 500)
  private String presentationVideoUrl;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private CourseStatus status;

  @Column(name = "cover_image_id")
  private UUID coverImageId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  /** Exigido por JPA. */
  protected CourseModule() {}

  public static CourseModule create(
      UUID id,
      UUID courseId,
      String title,
      String shortDescription,
      String longDescription,
      String presentationVideoUrl,
      Integer displayOrder,
      OffsetDateTime ahora) {
    CourseModule modulo = new CourseModule();
    modulo.id = id;
    modulo.courseId = Objects.requireNonNull(courseId, "courseId");
    modulo.title = verificarTitulo(title, "VAL-002");
    modulo.shortDescription = verificarCorta(shortDescription, "VAL-004");
    modulo.longDescription = verificarLarga(longDescription, "VAL-004");
    modulo.presentationVideoUrl =
        VideoUrl.normalizar(presentationVideoUrl, "VAL-005", "presentationVideoUrl");
    modulo.displayOrder = verificarOrden(displayOrder, "VAL-003");
    modulo.status = CourseStatus.INACTIVO;
    modulo.createdAt = ahora;
    modulo.updatedAt = ahora;
    return modulo;
  }

  /**
   * Corrige los cinco campos y devuelve qué cambió (`RF-AC-023`); el nulo vacía descripciones y
   * video.
   */
  public Map<String, Object> update(
      Patchable<String> nuevoTitulo,
      Patchable<String> nuevaCorta,
      Patchable<String> nuevaLarga,
      Patchable<String> nuevoVideo,
      Patchable<Integer> nuevoOrden,
      OffsetDateTime ahora) {
    Map<String, Object> cambios = new LinkedHashMap<>();
    if (nuevoTitulo.presente() && nuevoTitulo.valor() != null) {
      String valor = verificarTitulo(nuevoTitulo.valor(), "VAL-002");
      if (!valor.equals(title)) {
        cambios.put("title", Map.of("before", title, "after", valor));
        title = valor;
      }
    }
    if (nuevaCorta.presente()) {
      String valor = verificarCorta(nuevaCorta.valor(), "VAL-004");
      if (!Objects.equals(valor, shortDescription)) {
        cambios.put(
            "short_description", Map.of("before", texto(shortDescription), "after", texto(valor)));
        shortDescription = valor;
      }
    }
    if (nuevaLarga.presente()) {
      String valor = verificarLarga(nuevaLarga.valor(), "VAL-004");
      if (!Objects.equals(valor, longDescription)) {
        cambios.put(
            "long_description", Map.of("before", texto(longDescription), "after", texto(valor)));
        longDescription = valor;
      }
    }
    if (nuevoVideo.presente()) {
      String valor = VideoUrl.normalizar(nuevoVideo.valor(), "VAL-004", "presentationVideoUrl");
      if (!Objects.equals(valor, presentationVideoUrl)) {
        cambios.put(
            "presentation_video_url",
            Map.of("before", texto(presentationVideoUrl), "after", texto(valor)));
        presentationVideoUrl = valor;
      }
    }
    if (nuevoOrden.presente() && nuevoOrden.valor() != null) {
      int valor = verificarOrden(nuevoOrden.valor(), "VAL-003");
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
   * Publica el módulo (`RF-AC-024`); la condición —una lección activa— la comprueba el caso de uso.
   */
  public boolean activate(OffsetDateTime ahora) {
    if (status == CourseStatus.ACTIVO) {
      return false;
    }
    status = CourseStatus.ACTIVO;
    updatedAt = ahora;
    return true;
  }

  public boolean deactivate(OffsetDateTime ahora) {
    if (status == CourseStatus.INACTIVO) {
      return false;
    }
    status = CourseStatus.INACTIVO;
    updatedAt = ahora;
    return true;
  }

  /** Retira el módulo (`RF-AC-025`, o el arrastre de `RF-AC-013`); nada más de la fila cambia. */
  public boolean delete(OffsetDateTime ahora) {
    if (deletedAt != null) {
      return false;
    }
    deletedAt = ahora;
    updatedAt = ahora;
    return true;
  }

  public boolean estaRetirado() {
    return deletedAt != null;
  }

  public Map<String, Object> instantanea() {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("course_id", courseId.toString());
    estado.put("title", title);
    estado.put("short_description", shortDescription);
    estado.put("long_description", longDescription);
    estado.put("presentation_video_url", presentationVideoUrl);
    estado.put("display_order", displayOrder);
    estado.put("status", status.name());
    estado.put("cover_image_id", coverImageId == null ? null : coverImageId.toString());
    return estado;
  }

  private static String verificarTitulo(String valor, String codigo) {
    String recortado = recortar(valor);
    if (recortado == null || recortado.length() > TITULO_MAXIMO) {
      throw rechazo(
          "title", codigo, "El título es obligatorio y no puede superar los 150 caracteres.");
    }
    return recortado;
  }

  private static String verificarCorta(String valor, String codigo) {
    String recortado = recortar(valor);
    if (recortado != null && recortado.length() > CORTA_MAXIMA) {
      throw rechazo(
          "shortDescription", codigo, "La descripción corta no puede exceder 300 caracteres.");
    }
    return recortado;
  }

  private static String verificarLarga(String valor, String codigo) {
    String recortado = recortar(valor);
    if (recortado != null && recortado.length() > LARGA_MAXIMA) {
      throw rechazo(
          "longDescription", codigo, "La descripción larga no puede exceder 10 000 caracteres.");
    }
    return recortado;
  }

  private static int verificarOrden(Integer valor, String codigo) {
    if (valor == null || valor < 0) {
      throw rechazo(
          "displayOrder",
          codigo,
          "El orden es obligatorio y debe ser un entero mayor o igual que cero.");
    }
    return valor;
  }

  private static ValidationException rechazo(String campo, String codigo, String mensaje) {
    return new ValidationException(
        codigo, mensaje, List.of(new FieldError(campo, codigo, mensaje)));
  }

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

  public UUID getCourseId() {
    return courseId;
  }

  public String getTitle() {
    return title;
  }

  public String getShortDescription() {
    return shortDescription;
  }

  public String getLongDescription() {
    return longDescription;
  }

  public String getPresentationVideoUrl() {
    return presentationVideoUrl;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public CourseStatus getStatus() {
    return status;
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
