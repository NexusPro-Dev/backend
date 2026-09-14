package com.factech.nexus.modules.products.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Los bytes de la portada de un producto (`RN-PM-033`, `V90`).
 *
 * <p><b>Es el primer archivo que el sistema guarda, y no es una entidad.</b> Es el valor de {@code
 * products.cover_image_id} sacado a una tabla propia porque cinco megas no caben con dignidad en
 * una fila del catálogo. Por eso es <b>inmutable</b>: una fila no se modifica nunca. Reemplazar la
 * portada es <b>otra</b> {@code ProductImage} con otro identificador —para que la dirección pública
 * sea inmutable y se cachee un año— y la anterior se borra físicamente. No es una baja lógica del
 * Art. V.13: se corrige el valor de un campo de {@code Product}, y su auditoría conserva el antes y
 * el después del identificador.
 *
 * <p><b>NUNCA EN UNA LECTURA DEL CATÁLOGO.</b> Ninguna sentencia de {@code
 * JpaProductQueryRepository} une esta tabla ni selecciona {@code content}: el listado, el detalle,
 * la oferta y el hotlink devuelven {@code cover_image_id} convertido en una dirección, y la única
 * lectura que carga los bytes es la que los sirve (`RF-PM-016`). Un {@code JOIN} aquí «para traer
 * el tipo» arrastraría megas por fila.
 *
 * <p><b>El tipo lo deciden los bytes</b> ({@link ImageSignature}), y el tamaño lo comprueba el
 * dominio y lo repite el esquema ({@code ck_product_images_size}), para que ninguna otra ruta que
 * la subida pueda meter algo mayor.
 */
@Entity
@Table(name = "product_images")
public class ProductImage {

  /** Cinco megabytes exactos: el mismo número que {@code ck_product_images_size}. */
  public static final int TAMANO_MAXIMO = 5_242_880;

  /** El nombre de la parte del {@code multipart}, y el campo que nombran los tres rechazos. */
  public static final String CAMPO = "file";

  @Id private UUID id;

  @Column(name = "content_type", nullable = false, length = 30)
  private String contentType;

  @Column(name = "content", nullable = false)
  private byte[] content;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected ProductImage() {}

  /**
   * Construye la imagen a partir de los bytes recibidos, o rechaza (`RF-PM-014` §11).
   *
   * <p><b>El orden de los tres rechazos es deliberado</b> (`spec.md` §8): vacío (`VAL-002`),
   * después tamaño (`VAL-004`), después firma (`VAL-003`). El tamaño va antes que el tipo porque
   * leer la firma de un archivo de cincuenta megas para decir que no es una imagen es trabajo
   * tirado, y el mensaje de tamaño es el más útil para quien lo envió.
   *
   * <p>Se construye <b>antes</b> de tocar la base: los tres rechazos salen sin haber bloqueado
   * ninguna fila del catálogo.
   *
   * @param bytes el archivo tal cual llegó; se guarda sin copiar ni tratar
   * @param ahora instante de la subida, inyectado para que la prueba pueda fijarlo
   */
  public static ProductImage de(UUID id, byte[] bytes, OffsetDateTime ahora) {
    if (bytes == null || bytes.length == 0) {
      throw rechazo("VAL-002", "La imagen de portada es obligatoria.");
    }
    if (bytes.length > TAMANO_MAXIMO) {
      throw rechazo("VAL-004", "La portada no puede pesar más de 5 MB.");
    }
    ImageSignature firma =
        ImageSignature.de(bytes)
            .orElseThrow(
                () -> rechazo("VAL-003", "La portada debe ser una imagen JPEG, PNG o WebP."));

    ProductImage imagen = new ProductImage();
    imagen.id = id;
    imagen.contentType = firma.contentType();
    imagen.content = bytes;
    imagen.createdAt = ahora;
    return imagen;
  }

  private static ValidationException rechazo(String codigo, String mensaje) {
    return new ValidationException(
        codigo, mensaje, List.of(new FieldError(CAMPO, codigo, mensaje)));
  }

  public UUID getId() {
    return id;
  }

  /** El tipo real, detectado en los bytes: lo que se sirve. */
  public String getContentType() {
    return contentType;
  }

  public byte[] getContent() {
    return content;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
