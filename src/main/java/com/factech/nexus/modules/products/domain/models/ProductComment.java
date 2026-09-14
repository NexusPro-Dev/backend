package com.factech.nexus.modules.products.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * La reseña de una persona sobre un producto (`requirements/pm.md` §5.2.7, §10.4).
 *
 * <h2>{@code userId} es el AUTOR, no el actor</h2>
 *
 * <p>El Art. V.7 prohíbe duplicar en la tabla <b>quién hizo cada cambio</b>; eso sigue viviendo en
 * la auditoría. Aquí {@code userId} es de quién es la opinión, y sin él la fila no significa nada —
 * como {@code user_commission_rates.user_id}. Coincide con el actor de cada cambio porque
 * `RN-PM-027` lo obliga, no porque la columna lo diga.
 *
 * <h2>Sin {@code @ManyToOne} a nada</h2>
 *
 * <p>Guarda los dos identificadores, como {@link Product} guarda los de sus membresías. Un
 * {@code @ManyToOne} a {@code User} rompería la regla de ArchUnit que impide a {@code
 * modules/products} importar entidades de {@code modules/system}, y uno a {@code Product} cargaría
 * la entidad entera para escribir un identificador.
 *
 * <h2>Lo que valida aquí y lo que valida el esquema</h2>
 *
 * <p>La puntuación entre uno y cinco y el texto de uno a mil caracteres <b>tras recortar</b>
 * (`RN-PM-025`) se comprueban aquí con los códigos de `spec.md` §11, y el esquema lleva los dos
 * {@code CHECK} como red. Miden lo mismo porque el texto se guarda <b>ya recortado</b>: si el
 * {@code CHECK} midiera sobre el crudo, mil espacios y una letra pasarían aquí y morderían allí.
 */
@Entity
@Table(name = "product_comments")
public class ProductComment {

  public static final int PUNTUACION_MINIMA = 1;
  public static final int PUNTUACION_MAXIMA = 5;
  public static final int LONGITUD_MAXIMA = 1000;

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "rating", nullable = false)
  private short rating;

  @Column(name = "comment", nullable = false)
  private String comment;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  protected ProductComment() {}

  /**
   * El alta (`RF-PM-009`). Valida la puntuación y el texto <b>juntos</b>: quien se equivocó en dos
   * corrige una vez.
   */
  public static ProductComment create(
      UUID id, UUID productId, UUID userId, Integer rating, String comment, OffsetDateTime ahora) {

    List<FieldError> problemas = new ArrayList<>();
    verificarPuntuacion(rating, problemas);
    String texto = recortar(comment);
    verificarTexto(texto, problemas);
    lanzarSiHay(problemas);

    ProductComment resena = new ProductComment();
    resena.id = Objects.requireNonNull(id);
    resena.productId = Objects.requireNonNull(productId);
    resena.userId = Objects.requireNonNull(userId);
    resena.rating = rating.shortValue();
    resena.comment = texto;
    resena.createdAt = ahora;
    resena.updatedAt = ahora;
    return resena;
  }

  /**
   * La corrección (`RF-PM-010`): lo que no viene no cambia, y <b>ningún campo admite el nulo
   * explícito</b> — los dos son obligatorios en la columna, de modo que los tres estados de un
   * campo se reducen a dos y el nulo es un error, no una orden.
   *
   * @return el mapa de cambios {@code {campo: {before, after}}}, <b>vacío si nada cambió</b>. En
   *     ese caso {@code updatedAt} no avanza: una corrección que no corrige nada no es una
   *     corrección (`FA-003`)
   */
  public Map<String, Object> corregir(
      Patchable<Integer> nuevaPuntuacion, Patchable<String> nuevoTexto, OffsetDateTime ahora) {

    List<FieldError> problemas = new ArrayList<>();
    Integer puntuacion = null;
    String texto = null;

    if (nuevaPuntuacion.presente()) {
      puntuacion = nuevaPuntuacion.valor();
      verificarPuntuacion(puntuacion, problemas);
    }
    if (nuevoTexto.presente()) {
      texto = recortar(nuevoTexto.valor());
      verificarTexto(texto, problemas);
    }
    lanzarSiHay(problemas);

    Map<String, Object> cambios = new LinkedHashMap<>();
    if (puntuacion != null && puntuacion.shortValue() != rating) {
      cambios.put("rating", Map.of("before", (int) rating, "after", puntuacion));
      rating = puntuacion.shortValue();
    }
    if (texto != null && !texto.equals(comment)) {
      cambios.put("comment", Map.of("before", comment, "after", texto));
      comment = texto;
    }
    if (!cambios.isEmpty()) {
      updatedAt = ahora;
    }
    return cambios;
  }

  /**
   * El retiro (`RF-PM-011`). Marca y <b>no toca nada más</b>: la instantánea y la fila deben decir
   * lo mismo.
   */
  public void retirar(OffsetDateTime ahora) {
    deletedAt = ahora;
  }

  /** `RN-PM-027`: el permiso habilita, esto autoriza. */
  public boolean esDe(UUID actor) {
    return userId.equals(actor);
  }

  public boolean estaRetirada() {
    return deletedAt != null;
  }

  /** Lo que viaja al registro de eliminación y al de creación. */
  public Map<String, Object> instantanea() {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("product_id", productId.toString());
    estado.put("user_id", userId.toString());
    estado.put("rating", (int) rating);
    estado.put("comment", comment);
    estado.put("created_at", createdAt == null ? null : createdAt.toString());
    estado.put("updated_at", updatedAt == null ? null : updatedAt.toString());
    estado.put("deleted_at", deletedAt == null ? null : deletedAt.toString());
    return estado;
  }

  // ---------------------------------------------------------------------------

  private static void verificarPuntuacion(Integer valor, List<FieldError> problemas) {
    if (valor == null) {
      problemas.add(new FieldError("rating", "VAL-002", "La puntuación es obligatoria."));
      return;
    }
    if (valor < PUNTUACION_MINIMA || valor > PUNTUACION_MAXIMA) {
      problemas.add(
          new FieldError("rating", "VAL-003", "La puntuación debe ser un entero entre 1 y 5."));
    }
  }

  private static void verificarTexto(String valor, List<FieldError> problemas) {
    if (valor == null || valor.isEmpty()) {
      problemas.add(new FieldError("comment", "VAL-004", "El texto de la reseña es obligatorio."));
      return;
    }
    if (valor.length() > LONGITUD_MAXIMA) {
      problemas.add(
          new FieldError(
              "comment",
              "VAL-005",
              "El texto de la reseña no puede superar los " + LONGITUD_MAXIMA + " caracteres."));
    }
  }

  private static void lanzarSiHay(List<FieldError> problemas) {
    if (!problemas.isEmpty()) {
      throw new ValidationException(
          problemas.get(0).code(), problemas.get(0).message(), List.copyOf(problemas));
    }
  }

  private static String recortar(String valor) {
    return valor == null ? null : valor.strip();
  }

  // ---------------------------------------------------------------------------

  public UUID getId() {
    return id;
  }

  public UUID getProductId() {
    return productId;
  }

  public UUID getUserId() {
    return userId;
  }

  public int getRating() {
    return rating;
  }

  public String getComment() {
    return comment;
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
