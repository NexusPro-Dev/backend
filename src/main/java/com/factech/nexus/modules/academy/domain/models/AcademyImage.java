package com.factech.nexus.modules.academy.domain.models;

import com.factech.nexus.shared.images.ImageSignature;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Los bytes de una portada de academia —de una categoría, un curso o un módulo— (`RN-AC-004`,
 * `V44`).
 *
 * <p>{@code ProductImage} en el módulo que escribe la tabla (`ac.md` §5.2.3): <b>inmutable</b>, una
 * subida estrena identificador y la reemplazada se borra. <b>Sin columna que diga de quién es</b>:
 * lo dice quien la señala, y una imagen es portada de una sola fila (`uq_*_cover_image`).
 *
 * <p><b>NUNCA EN UNA LECTURA DEL CATÁLOGO.</b> Ninguna sentencia de `AC` une esta tabla ni lee
 * {@code content}: las lecturas devuelven {@code cover_image_id} convertido en una dirección, y la
 * única que carga los bytes es la que los sirve (`RF-AC-032`).
 */
@Entity
@Table(name = "academy_images")
public class AcademyImage {

  @Id private UUID id;

  @Column(name = "content_type", nullable = false, length = 30)
  private String contentType;

  @Column(name = "content", nullable = false)
  private byte[] content;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected AcademyImage() {}

  /**
   * La imagen a partir de los bytes recibidos, o el rechazo, con los tres {@code VAL} de {@link
   * ImageSignature#validar}: <b>los mismos de `PM`, código a código</b>. Se construye antes de
   * tocar la base.
   */
  public static AcademyImage de(UUID id, byte[] bytes, OffsetDateTime ahora) {
    ImageSignature firma = ImageSignature.validar(bytes);
    AcademyImage imagen = new AcademyImage();
    imagen.id = id;
    imagen.contentType = firma.contentType();
    imagen.content = bytes;
    imagen.createdAt = ahora;
    return imagen;
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
