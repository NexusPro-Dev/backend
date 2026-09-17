package com.factech.nexus.modules.products.domain.models;

import java.util.Arrays;
import java.util.Optional;

/**
 * Los tres tipos de imagen que el sistema admite como portada, y cómo se reconocen (`RN-PM-033`).
 *
 * <p><b>El tipo lo deciden los bytes, y es la decisión que más pesa de la portada.</b> La cabecera
 * {@code Content-Type} de la petición la escribe el cliente y se puede equivocar o mentir; los ocho
 * primeros bytes de un {@code PNG} no. Lo que se guarda en {@code product_images.content_type} es
 * lo que <b>esta</b> clase detectó, y es lo que `RF-PM-016` devuelve al servir la imagen — por eso
 * tiene que ser verdad.
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

  /** El {@code Content-Type} con el que se guarda y se sirve. */
  public String contentType() {
    return contentType;
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
