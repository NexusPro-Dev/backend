package com.factech.nexus.modules.academy.interfaces;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.web.multipart.MultipartFile;

/**
 * La parte {@code file} de una subida de portada, convertida en bytes: lo único que las portadas de
 * academia necesitan de HTTP. El resto del módulo recibe bytes y no sabe que hubo una petición.
 *
 * <p><b>Nulo si la parte no vino</b>, y eso es `VAL-002` con el sobre de errores del sistema —por
 * eso los controladores la piden con {@code required = false}—, no el `400` genérico de Spring. Un
 * fallo al leer el cuerpo es de transporte y sube como no controlado.
 */
final class CoverPart {

  private CoverPart() {}

  static byte[] bytesDe(MultipartFile file) {
    if (file == null) {
      return null;
    }
    try {
      return file.getBytes();
    } catch (IOException fallo) {
      throw new UncheckedIOException("No se pudo leer la imagen de portada de la petición.", fallo);
    }
  }
}
