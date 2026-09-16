package com.factech.nexus.modules.products.domain.models;

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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Un paquete de productos (`RF-PM-017`): varios productos vendidos juntos, cada uno con su
 * descuento.
 *
 * <p><b>No tiene precio</b> (`RN-PM-036`). Su precio es la suma de sus productos con su descuento y
 * lo calcula {@link PackagePricing} en cada lectura; aquí no hay columna ni campo, y es deliberado:
 * una copia guardada que se quedara atrás no fallaría, mentiría.
 *
 * <p><b>Sí tiene moneda, y es inmutable</b> (`RN-PM-035`): es la unidad en la que se suma, y solo
 * se le asocian productos en ella. Cambiarla convertiría los descuentos fijos en otra cosa.
 *
 * <p><b>Hereda la forma del producto</b> (`RN-PM-041`): código normalizado e inmutable, nombre
 * recortado, descripción vacía → nula, nace {@link PackageStatus#INACTIVO}, y retiro lógico. Es a
 * la vez agregado y modelo persistente, como {@link Product}.
 */
@Entity
@Table(name = "product_packages")
public class ProductPackage {

  private static final Pattern PATRON_CODIGO = Pattern.compile("^[A-Z][A-Z0-9_]*$");

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "code", nullable = false, length = 50, updatable = false)
  private String code;

  @Column(name = "name", nullable = false, length = 150)
  private String name;

  @Column(name = "description")
  private String description;

  /**
   * La portada del paquete: la fila de {@code product_images} cuyos bytes se sirven sin token
   * (`RN-PM-045`, `V11`) — la misma tabla y la misma ruta que la del producto.
   *
   * <p><b>Identificador y no asociación</b>, por lo mismo que en {@link Product}. Nulo significa
   * «no tiene portada», y entonces el frontend pinta <b>el icono de promoción y el color por
   * omisión del sistema</b>: el paquete no declara ninguno de los dos, y por eso aquí no hay
   * ninguna regla como `RN-PM-034` — quitar la portada nunca se rechaza.
   */
  @Column(name = "cover_image_id")
  private UUID coverImageId;

  @Column(name = "currency_id", nullable = false, updatable = false)
  private UUID currencyId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PackageStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "scope", nullable = false, length = 20)
  private ProductScope scope;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  /** Exigido por JPA. */
  protected ProductPackage() {}

  /**
   * Registra un paquete, siempre inactivo y vacío.
   *
   * <p>El estado no se recibe (`RN-PM-041`) y los productos tampoco: entran uno a uno por
   * `RF-PM-023`, donde cada uno tiene sus siete verificaciones (`spec.md` §14.1).
   */
  public static ProductPackage create(
      UUID id,
      String code,
      String name,
      String description,
      UUID currencyId,
      ProductScope scope,
      OffsetDateTime ahora) {
    ProductPackage paquete = new ProductPackage();
    paquete.id = id;
    paquete.code = normalizarCodigo(code);
    paquete.name = recortar(name);
    paquete.description = recortar(description);
    paquete.currencyId = currencyId;
    paquete.scope = scope;
    paquete.status = PackageStatus.INACTIVO;
    paquete.createdAt = ahora;
    paquete.updatedAt = ahora;
    return paquete;
  }

  /**
   * Corrige nombre, descripción y alcance, y devuelve qué cambió (`RF-PM-020`).
   *
   * <p>Como {@link Product#update}: el diff lo devuelve quien aplica el cambio, los ausentes no se
   * tocan, el nulo explícito vacía la descripción y solo la descripción, y {@code updatedAt} se
   * mueve únicamente si algo cambió. <b>Código y moneda no tienen mutador</b>: el caso de uso los
   * rechaza antes de llegar aquí (`EX-003`).
   */
  public Map<String, Object> update(
      Patchable<String> nuevoNombre,
      Patchable<String> nuevaDescripcion,
      Patchable<ProductScope> nuevoAlcance,
      OffsetDateTime ahora) {
    Map<String, Object> cambios = new LinkedHashMap<>();
    if (nuevoNombre.presente() && nuevoNombre.valor() != null) {
      String valor = recortar(nuevoNombre.valor());
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
    if (nuevoAlcance.presente() && nuevoAlcance.valor() != null) {
      ProductScope valor = nuevoAlcance.valor();
      if (valor != scope) {
        cambios.put("scope", Map.of("before", scope.name(), "after", valor.name()));
        scope = valor;
      }
    }
    if (!cambios.isEmpty()) {
      updatedAt = ahora;
    }
    return cambios;
  }

  /**
   * Publica el paquete (`RF-PM-021`). Devuelve si hubo cambio y no lanza si ya estaba activo.
   *
   * <p><b>Aquí no se mira descripción ni cuántos productos</b>: la segunda condición mira a otras
   * filas, y el agregado solo conoce la suya. Las dos viven en el caso de uso, juntas.
   */
  public boolean activate(OffsetDateTime ahora) {
    return cambiarEstado(PackageStatus.ACTIVO, ahora);
  }

  public boolean deactivate(OffsetDateTime ahora) {
    return cambiarEstado(PackageStatus.INACTIVO, ahora);
  }

  private boolean cambiarEstado(PackageStatus destino, OffsetDateTime ahora) {
    if (status == destino) {
      return false;
    }
    status = destino;
    updatedAt = ahora;
    return true;
  }

  /** Retira el paquete (`RF-PM-022`). Devuelve si hubo cambio; las filas de asociación quedan. */
  public boolean delete(OffsetDateTime ahora) {
    if (deletedAt != null) {
      return false;
    }
    deletedAt = ahora;
    updatedAt = ahora;
    return true;
  }

  /**
   * Pone o reemplaza la portada (`RF-PM-028`).
   *
   * <p><b>Sin condición de estado ni de contenido</b>: subir una portada nunca deja al paquete peor
   * de lo que estaba, y un paquete inactivo, vacío o sin descripción la admite igual. Siempre hay
   * cambio, porque cada subida estrena identificador.
   */
  public CambioDePortada asignarPortada(UUID nueva, OffsetDateTime ahora) {
    UUID anterior = coverImageId;
    coverImageId = nueva;
    updatedAt = ahora;
    return CambioDePortada.de(anterior, nueva);
  }

  /**
   * Quita la portada (`RF-PM-029`), <b>y nunca se rechaza</b>.
   *
   * <p>Es {@link Product#quitarPortada} sin el paso de regla: el paquete no declara icono ni color,
   * de modo que no puede quedarse sin nada con qué pintarse (`RN-PM-045`). Sin portada, devuelve un
   * diff vacío y no toca {@code updatedAt}: «quítala» sobre un paquete sin portada ya ha conseguido
   * lo que quería.
   */
  public CambioDePortada quitarPortada(OffsetDateTime ahora) {
    if (coverImageId == null) {
      return CambioDePortada.ninguno();
    }
    UUID anterior = coverImageId;
    coverImageId = null;
    updatedAt = ahora;
    return CambioDePortada.de(anterior, null);
  }

  public boolean estaRetirado() {
    return deletedAt != null;
  }

  public boolean tieneDescripcion() {
    return description != null && !description.isBlank();
  }

  /** La misma instantánea para la creación y para el retiro, por lo mismo que en el producto. */
  public Map<String, Object> instantanea() {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("code", code);
    estado.put("name", name);
    estado.put("description", description);
    estado.put("cover_image_id", coverImageId == null ? null : coverImageId.toString());
    estado.put("currency_id", currencyId.toString());
    estado.put("status", status.name());
    estado.put("scope", scope.name());
    return estado;
  }

  private static String texto(String valor) {
    return valor == null ? "" : valor;
  }

  private static String normalizarCodigo(String valor) {
    String normalizado = valor == null ? null : valor.trim().toUpperCase(Locale.ROOT);
    if (normalizado == null || !PATRON_CODIGO.matcher(normalizado).matches()) {
      String mensaje =
          "El código es obligatorio y debe empezar por una letra mayúscula y contener solo letras"
              + " mayúsculas, dígitos y guion bajo.";
      throw new ValidationException(
          "VAL-001", mensaje, List.of(new FieldError("code", "VAL-001", mensaje)));
    }
    return normalizado;
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

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public UUID getCoverImageId() {
    return coverImageId;
  }

  public UUID getCurrencyId() {
    return currencyId;
  }

  public PackageStatus getStatus() {
    return status;
  }

  public ProductScope getScope() {
    return scope;
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
