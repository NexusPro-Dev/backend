package com.factech.nexus.shared.images;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Los tres tipos de imagen que el sistema admite como portada, y cómo se reconocen (`RN-PM-033`).
 *
 * <p><b>El tipo lo deciden los bytes, y es la decisión que más pesa de la portada.</b> La cabecera
 * {@code Content-Type} de la petición la escribe el cliente y se puede equivocar o mentir; los ocho
 * primeros bytes de un {@code PNG} no. Lo que se guarda en {@code product_images.content_type} y en
 * {@code academy_images.content_type} es lo que <b>esta</b> clase detectó, y es lo que `RF-PM-016`
 * y `RF-AC-032` devuelven al servir la imagen — por eso tiene que ser verdad.
 *
 * <p><b>Vive en {@code shared/} desde el 25-09-2026</b> (`RF-AC-006`): nació en `PM` y la
 * necesitaron las portadas de academia. Mudarla y no copiarla es lo que garantiza que las dos
 * tablas admitan exactamente lo mismo.
 *
 * <p><b>Es todo el conocimiento que el sistema tiene sobre imágenes, y vive aquí a propósito.</b>
 * Unos veinte bytes de firmas, y ninguna biblioteca que decodifique: no hay nada que decodificar
 * porque la imagen no se trata (`requirements/pm.md` §5.2.9). El esquema tampoco lo sabe —no hay
 * {@code CHECK} sobre la firma— para que el día que se añada un formato se toque un solo sitio y la
 * restricción de {@code content_type}.
 *
 * <p><b>{@code SVG} queda fuera a propósito</b>: puede llevar código, y servirlo <i>inline</i>
 * desde el propio origen del sistema sería servir código de quien lo subió. {@code GIF} queda fuera
 * porque una portada animada es una decisión de diseño que nadie tomó.
 */
public enum ImageSignature {
  JPEG("image/jpeg", new int[] {0xFF, 0xD8, 0xFF}),
  PNG("image/png", new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
  /**
   * {@code RIFF????WEBP}: los cuatro bytes del medio son el tamaño del archivo y no forman parte de
   * la firma, de modo que se comparan por separado.
   */
  WEBP("image/webp", new int[] {0x52, 0x49, 0x46, 0x46}, new int[] {0x57, 0x45, 0x42, 0x50});

  private final String contentType;
  private final int[] cabecera;
  private final int[] enOcho;

  ImageSignature(String contentType, int[] cabecera) {
    this(contentType, cabecera, new int[0]);
  }

  ImageSignature(String contentType, int[] cabecera, int[] enOcho) {
    this.contentType = contentType;
    this.cabecera = cabecera;
    this.enOcho = enOcho;
  }

  /**
   * Cinco megabytes exactos: el mismo número que {@code ck_product_images_size} y {@code
   * ck_academy_images_size}. Subir el tope es esta constante y esas dos restricciones.
   */
  public static final int TAMANO_MAXIMO = 5_242_880;

  /** El nombre de la parte del {@code multipart}, y el campo que nombran los tres rechazos. */
  public static final String CAMPO = "file";

  /** El {@code Content-Type} con el que se guarda y se sirve. */
  public String contentType() {
    return contentType;
  }

  /**
   * La firma de un archivo que se admite como portada, o el rechazo (`RF-PM-014` §11, `RF-AC-006`
   * §11): vacío (`VAL-002`), después tamaño (`VAL-004`), después firma (`VAL-003`).
   *
   * <p><b>El orden es deliberado</b>: leer la firma de un archivo de cincuenta megas para decir que
   * no es una imagen es trabajo tirado, y el mensaje de tamaño es el más útil para quien lo envió.
   * <b>Vive aquí desde el 25-09-2026</b> porque las portadas de `PM` y las de `AC` son la misma
   * regla sobre dos tablas, y dos copias acabarían admitiendo cosas distintas.
   */
  public static ImageSignature validar(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      throw rechazo("VAL-002", "La imagen de portada es obligatoria.");
    }
    if (bytes.length > TAMANO_MAXIMO) {
      throw rechazo("VAL-004", "La portada no puede pesar más de 5 MB.");
    }
    return de(bytes)
        .orElseThrow(() -> rechazo("VAL-003", "La portada debe ser una imagen JPEG, PNG o WebP."));
  }

  private static ValidationException rechazo(String codigo, String mensaje) {
    return new ValidationException(
        codigo, mensaje, List.of(new FieldError(CAMPO, codigo, mensaje)));
  }

  /**
   * Reconoce la firma de los bytes, o vacío si no es ninguna de las tres.
   *
   * <p>Solo mira los primeros doce bytes como máximo: no hay ninguna razón para leer más, y así un
   * archivo de cinco megas que no es una imagen se rechaza en el mismo tiempo que uno de doce
   * bytes.
   */
  public static Optional<ImageSignature> de(byte[] bytes) {
    if (bytes == null) {
      return Optional.empty();
    }
    return Arrays.stream(values()).filter(firma -> firma.casa(bytes)).findFirst();
  }

  private boolean casa(byte[] bytes) {
    return empiezaPor(bytes, 0, cabecera) && empiezaPor(bytes, 8, enOcho);
  }

  private static boolean empiezaPor(byte[] bytes, int desde, int[] esperado) {
    if (bytes.length < desde + esperado.length) {
      return false;
    }
    for (int i = 0; i < esperado.length; i++) {
      if ((bytes[desde + i] & 0xFF) != esperado[i]) {
        return false;
      }
    }
    return true;
  }
}
