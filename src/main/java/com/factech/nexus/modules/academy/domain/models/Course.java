package com.factech.nexus.modules.academy.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.images.CambioDePortada;
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
 * Un curso (`RF-AC-008`): lo que la membresía abre.
 *
 * <p><b>Nace {@code INACTIVO}, vacío y sin código</b> (`RN-AC-008`, `requirements/ac.md` §5.2.6).
 * Lo que lleva es lo suyo: título, instructor, dificultad, dos descripciones, video de
 * introducción, orden, estado y portada. Las categorías, las recomendaciones, las membresías y los
 * módulos son filas de otras tablas que se dan y se quitan con su propia operación, y este agregado
 * no las conoce: lo que necesita saber de ellas —cuántas hay— se lo pasa quien lee.
 *
 * <p><b>El instructor es un identificador de `SP`</b> comprobado al asignar (`RN-AC-006`) y nunca
 * después: el curso sigue diciendo quién lo enseñó hasta que administración lo reasigne. <b>Las
 * descripciones y el video se vacían en cualquier estado</b>: `RN-AC-009` rige al activar, y lo que
 * después se vacía no desactiva — deja de ofrecerse (`RN-AC-015`).
 */
@Entity
@Table(name = "courses")
public class Course implements HasCover {

  private static final int TITULO_MAXIMO = 150;
  private static final int CORTA_MAXIMA = 300;
  private static final int LARGA_MAXIMA = 10_000;

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "title", nullable = false, length = 150)
  private String title;

  @Column(name = "instructor_id", nullable = false)
  private UUID instructorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "difficulty", nullable = false, length = 20)
  private CourseDifficulty difficulty;

  @Column(name = "short_description", length = 300)
  private String shortDescription;

  @Column(name = "long_description")
  private String longDescription;

  @Column(name = "intro_video_url", length = 500)
  private String introVideoUrl;

  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private CourseStatus status;

  /** La portada (`RN-AC-004`); nadie la escribe hasta `RF-AC-014`. */
  @Column(name = "cover_image_id")
  private UUID coverImageId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  /** Exigido por JPA. */
  protected Course() {}

  /**
   * Registra un curso, inactivo y vacío. La forma se comprueba aquí <b>además</b> de en el DTO: el
   * DTO devuelve los errores juntos (`CA-AC-037`), y el agregado es la red para cualquier otra vía.
   */
  public static Course create(
      UUID id,
      String title,
      UUID instructorId,
      CourseDifficulty difficulty,
      String shortDescription,
      String longDescription,
      String introVideoUrl,
      Integer displayOrder,
      OffsetDateTime ahora) {
    Course curso = new Course();
    curso.id = id;
    curso.title = verificarTitulo(title, "VAL-001");
    curso.instructorId = verificarInstructor(instructorId, "VAL-002");
    curso.difficulty = verificarDificultad(difficulty, "VAL-003");
    curso.shortDescription = verificarCorta(shortDescription, "VAL-005");
    curso.longDescription = verificarLarga(longDescription, "VAL-005");
    curso.introVideoUrl = VideoUrl.normalizar(introVideoUrl, "VAL-006", "introVideoUrl");
    curso.displayOrder = verificarOrden(displayOrder, "VAL-004");
    curso.status = CourseStatus.INACTIVO;
    curso.createdAt = ahora;
    curso.updatedAt = ahora;
    return curso;
  }

  /**
   * Corrige los siete campos y devuelve qué cambió (`RF-AC-011`).
   *
   * <p>Como la categoría: los ausentes no se tocan, el nulo explícito <b>vacía las descripciones y
   * el video</b> —los otros cuatro llegan aquí ya rechazados por el caso de uso— y {@code
   * updatedAt} se mueve solo si algo cambió. El instructor nuevo llega ya comprobado contra `SP`.
   */
  public Map<String, Object> update(
      Patchable<String> nuevoTitulo,
      Patchable<UUID> nuevoInstructor,
      Patchable<CourseDifficulty> nuevaDificultad,
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
    if (nuevoInstructor.presente() && nuevoInstructor.valor() != null) {
      UUID valor = nuevoInstructor.valor();
      if (!valor.equals(instructorId)) {
        cambios.put(
            "instructor_id", Map.of("before", instructorId.toString(), "after", valor.toString()));
        instructorId = valor;
      }
    }
    if (nuevaDificultad.presente() && nuevaDificultad.valor() != null) {
      CourseDifficulty valor = nuevaDificultad.valor();
      if (valor != difficulty) {
        cambios.put("difficulty", Map.of("before", difficulty.name(), "after", valor.name()));
        difficulty = valor;
      }
    }
    if (nuevaCorta.presente()) {
      String valor = verificarCorta(nuevaCorta.valor(), "VAL-006");
      if (!Objects.equals(valor, shortDescription)) {
        cambios.put(
            "short_description", Map.of("before", texto(shortDescription), "after", texto(valor)));
        shortDescription = valor;
      }
    }
    if (nuevaLarga.presente()) {
      String valor = verificarLarga(nuevaLarga.valor(), "VAL-006");
      if (!Objects.equals(valor, longDescription)) {
        cambios.put(
            "long_description", Map.of("before", texto(longDescription), "after", texto(valor)));
        longDescription = valor;
      }
    }
    if (nuevoVideo.presente()) {
      String valor = VideoUrl.normalizar(nuevoVideo.valor(), "VAL-006", "introVideoUrl");
      if (!Objects.equals(valor, introVideoUrl)) {
        cambios.put(
            "intro_video_url", Map.of("before", texto(introVideoUrl), "after", texto(valor)));
        introVideoUrl = valor;
      }
    }
    if (nuevoOrden.presente() && nuevoOrden.valor() != null) {
      int valor = verificarOrden(nuevoOrden.valor(), "VAL-005");
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
   * Publica el curso (`RF-AC-012`). <b>Las condiciones las comprueba el caso de uso</b>, juntas y
   * con la cuenta de módulos que este agregado no tiene; aquí solo se cambia el estado. Devuelve si
   * hubo cambio.
   */
  public boolean activate(OffsetDateTime ahora) {
    if (status == CourseStatus.ACTIVO) {
      return false;
    }
    status = CourseStatus.ACTIVO;
    updatedAt = ahora;
    return true;
  }

  /** Despublica sin condiciones (`RN-AC-009`): sale del aula y nada más cambia. */
  public boolean deactivate(OffsetDateTime ahora) {
    if (status == CourseStatus.INACTIVO) {
      return false;
    }
    status = CourseStatus.INACTIVO;
    updatedAt = ahora;
    return true;
  }

  /** Retira el curso (`RF-AC-013`); nada más de la fila cambia, ni el estado. */
  public boolean delete(OffsetDateTime ahora) {
    if (deletedAt != null) {
      return false;
    }
    deletedAt = ahora;
    updatedAt = ahora;
    return true;
  }

  /**
   * Pone la portada (`RN-AC-004`) y devuelve cuál había, para que se borre después de volcar, y el
   * diff. Siempre cambia: cada subida estrena identificador. Mueve {@code updatedAt}, como el
   * paquete de `PM`.
   */
  @Override
  public CambioDePortada asignarPortada(UUID nueva, OffsetDateTime ahora) {
    UUID anterior = coverImageId;
    coverImageId = nueva;
    updatedAt = ahora;
    return CambioDePortada.de(anterior, nueva);
  }

  public boolean estaRetirado() {
    return deletedAt != null;
  }

  public boolean tieneDescripcionCorta() {
    return shortDescription != null;
  }

  public boolean tieneDescripcionLarga() {
    return longDescription != null;
  }

  /**
   * La misma instantánea para la creación y para el retiro; el retiro le añade los identificadores
   * de sus relaciones y módulos. {@code instructor_id} va dentro (`CA-AC-040`).
   */
  public Map<String, Object> instantanea() {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("title", title);
    estado.put("instructor_id", instructorId.toString());
    estado.put("difficulty", difficulty.name());
    estado.put("short_description", shortDescription);
    estado.put("long_description", longDescription);
    estado.put("intro_video_url", introVideoUrl);
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

  private static UUID verificarInstructor(UUID valor, String codigo) {
    if (valor == null) {
      throw rechazo("instructorId", codigo, "El instructor es obligatorio.");
    }
    return valor;
  }

  private static CourseDifficulty verificarDificultad(CourseDifficulty valor, String codigo) {
    if (valor == null) {
      throw rechazo(
          "difficulty",
          codigo,
          "La dificultad es obligatoria y debe ser PRINCIPIANTE, INTERMEDIO o AVANZADO.");
    }
    return valor;
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

  public String getTitle() {
    return title;
  }

  public UUID getInstructorId() {
    return instructorId;
  }

  public CourseDifficulty getDifficulty() {
    return difficulty;
  }

  public String getShortDescription() {
    return shortDescription;
  }

  public String getLongDescription() {
    return longDescription;
  }

  public String getIntroVideoUrl() {
    return introVideoUrl;
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
