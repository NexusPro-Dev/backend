package com.factech.nexus.modules.academy.domain.models;

import com.factech.nexus.shared.error.BusinessRuleException;
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
 * Lo que se estudia (`RF-AC-028`): tipo, título, descripción, contenido, duración, orden, la
 * bandera de demostración y el estado, <b>dentro de un módulo del que no se mueve</b>
 * (`RN-AC-019`).
 *
 * <p><b>El tipo manda sobre el contenido</b> (`RN-AC-016`, {@link LessonContent}): en el alta se
 * valida el que llega contra el tipo que llega; en la corrección, <b>la pareja resultante antes de
 * aplicar nada</b>. <b>La instantánea de auditoría lleva la longitud del contenido y no el
 * texto</b> (`RF-AC-028` §14.1); solo la del retiro lo lleva entero ({@link
 * #instantaneaCompleta()}).
 *
 * <p><b>Activar exige contenido</b> (`RN-AC-009`), y es la única condición que este agregado sabe
 * comprobar solo: la lanza {@link #activate} como el {@code 409} de `RF-AC-030`.
 */
@Entity
@Table(name = "lessons")
public class Lesson {

  private static final int TITULO_MAXIMO = 150;
  private static final int DESCRIPCION_MAXIMA = 1000;
  static final String SIN_CONTENIDO =
      "La lección no tiene contenido: no se publica lo que está vacío.";

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "module_id", nullable = false, updatable = false)
  private UUID moduleId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 20)
  private LessonType type;

  @Column(name = "title", nullable = false, length = 150)
  private String title;

  @Column(name = "description")
  private String description;

  @Column(name = "content")
  private String content;

  @Column(name = "duration_seconds", nullable = false)
  private int durationSeconds;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Column(name = "open", nullable = false)
  private boolean open;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private CourseStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  /** Exigido por JPA. */
  protected Lesson() {}

  public static Lesson create(
      UUID id,
      UUID moduleId,
      LessonType type,
      String title,
      String description,
      String content,
      Integer durationSeconds,
      Integer displayOrder,
      Boolean open,
      OffsetDateTime ahora) {
    Lesson leccion = new Lesson();
    leccion.id = id;
    leccion.moduleId = Objects.requireNonNull(moduleId, "moduleId");
    leccion.type = verificarTipo(type, "VAL-002");
    leccion.title = verificarTitulo(title, "VAL-003");
    leccion.description = verificarDescripcion(description, "VAL-007");
    leccion.content = LessonContent.de(leccion.type, content, "VAL-006");
    leccion.durationSeconds = verificarDuracion(durationSeconds, "VAL-004");
    leccion.displayOrder = verificarOrden(displayOrder, "VAL-005");
    leccion.open = Boolean.TRUE.equals(open);
    leccion.status = CourseStatus.INACTIVO;
    leccion.createdAt = ahora;
    leccion.updatedAt = ahora;
    return leccion;
  }

  /**
   * Corrige los siete campos y devuelve qué cambió (`RF-AC-029`). <b>La pareja {@code (tipo,
   * contenido)} resultante se valida antes de aplicar nada</b>; el nulo explícito vacía la
   * descripción y el contenido; el diff del contenido lleva su longitud antes y después.
   */
  public Map<String, Object> update(
      Patchable<LessonType> nuevoTipo,
      Patchable<String> nuevoTitulo,
      Patchable<String> nuevaDescripcion,
      Patchable<String> nuevoContenido,
      Patchable<Integer> nuevaDuracion,
      Patchable<Integer> nuevoOrden,
      Patchable<Boolean> nuevaAbierta,
      OffsetDateTime ahora) {
    LessonType tipoResultante =
        nuevoTipo.presente() && nuevoTipo.valor() != null ? nuevoTipo.valor() : type;
    String contenidoCrudo = nuevoContenido.presente() ? nuevoContenido.valor() : content;
    // Antes de tocar nada: si el tipo resultante es VIDEO y el contenido
    // resultante no es una URL, se rechaza entero (`CA-AC-100`).
    String contenidoResultante = LessonContent.de(tipoResultante, contenidoCrudo, "VAL-004");

    Map<String, Object> cambios = new LinkedHashMap<>();
    if (tipoResultante != type) {
      cambios.put("type", Map.of("before", type.name(), "after", tipoResultante.name()));
      type = tipoResultante;
    }
    if (nuevoTitulo.presente() && nuevoTitulo.valor() != null) {
      String valor = verificarTitulo(nuevoTitulo.valor(), "VAL-002");
      if (!valor.equals(title)) {
        cambios.put("title", Map.of("before", title, "after", valor));
        title = valor;
      }
    }
    if (nuevaDescripcion.presente()) {
      String valor = verificarDescripcion(nuevaDescripcion.valor(), "VAL-003");
      if (!Objects.equals(valor, description)) {
        cambios.put("description", Map.of("before", texto(description), "after", texto(valor)));
        description = valor;
      }
    }
    if (!Objects.equals(contenidoResultante, content)) {
      cambios.put(
          "content_length",
          Map.of("before", longitud(content), "after", longitud(contenidoResultante)));
      content = contenidoResultante;
    }
    if (nuevaDuracion.presente() && nuevaDuracion.valor() != null) {
      int valor = verificarDuracion(nuevaDuracion.valor(), "VAL-002");
      if (valor != durationSeconds) {
        cambios.put("duration_seconds", Map.of("before", durationSeconds, "after", valor));
        durationSeconds = valor;
      }
    }
    if (nuevoOrden.presente() && nuevoOrden.valor() != null) {
      int valor = verificarOrden(nuevoOrden.valor(), "VAL-002");
      if (valor != displayOrder) {
        cambios.put("display_order", Map.of("before", displayOrder, "after", valor));
        displayOrder = valor;
      }
    }
    if (nuevaAbierta.presente() && nuevaAbierta.valor() != null) {
      boolean valor = nuevaAbierta.valor();
      if (valor != open) {
        cambios.put("open", Map.of("before", open, "after", valor));
        open = valor;
      }
    }
    if (!cambios.isEmpty()) {
      updatedAt = ahora;
    }
    return cambios;
  }

  /** Publica la lección (`RF-AC-030`); sin contenido, el {@code 409} de `EX-002`. */
  public boolean activate(OffsetDateTime ahora) {
    if (status == CourseStatus.ACTIVO) {
      return false;
    }
    if (content == null) {
      throw new BusinessRuleException(
          "EX-002", SIN_CONTENIDO, List.of(new FieldError("content", "EX-002", SIN_CONTENIDO)));
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

  public boolean tieneContenido() {
    return content != null;
  }

  /** La instantánea de creación y de cambios: {@code content_length} y no el texto. */
  public Map<String, Object> instantanea() {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("module_id", moduleId.toString());
    estado.put("type", type.name());
    estado.put("title", title);
    estado.put("description", description);
    estado.put("content_length", longitud(content));
    estado.put("duration_seconds", durationSeconds);
    estado.put("display_order", displayOrder);
    estado.put("open", open);
    estado.put("status", status.name());
    return estado;
  }

  /** La del retiro (`RF-AC-031`): la única que lleva el contenido entero. */
  public Map<String, Object> instantaneaCompleta() {
    Map<String, Object> estado = new LinkedHashMap<>(instantanea());
    estado.put("content", content);
    return estado;
  }

  private static LessonType verificarTipo(LessonType valor, String codigo) {
    if (valor == null) {
      throw rechazo("type", codigo, "El tipo es obligatorio y debe ser VIDEO o TEXTO.");
    }
    return valor;
  }

  private static String verificarTitulo(String valor, String codigo) {
    String recortado = recortar(valor);
    if (recortado == null || recortado.length() > TITULO_MAXIMO) {
      throw rechazo(
          "title", codigo, "El título es obligatorio y no puede superar los 150 caracteres.");
    }
    return recortado;
  }

  private static String verificarDescripcion(String valor, String codigo) {
    String recortado = recortar(valor);
    if (recortado != null && recortado.length() > DESCRIPCION_MAXIMA) {
      throw rechazo("description", codigo, "La descripción no puede exceder 1000 caracteres.");
    }
    return recortado;
  }

  private static int verificarDuracion(Integer valor, String codigo) {
    if (valor == null || valor <= 0) {
      throw rechazo(
          "durationSeconds",
          codigo,
          "La duración es obligatoria y debe ser un entero de segundos mayor que cero.");
    }
    return valor;
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

  private static int longitud(String valor) {
    return valor == null ? 0 : valor.length();
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

  public UUID getModuleId() {
    return moduleId;
  }

  public LessonType getType() {
    return type;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getContent() {
    return content;
  }

  public int getDurationSeconds() {
    return durationSeconds;
  }

  public int getDisplayOrder() {
    return displayOrder;
  }

  public boolean isOpen() {
    return open;
  }

  public CourseStatus getStatus() {
    return status;
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
