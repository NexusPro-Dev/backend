package com.factech.nexus.modules.movements.domain.models;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.images.ImageSignature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * El comprobante de un ajuste de puntos, validado (`RN-MV-077`, `RF-MV-057`): un PDF, PNG o JPG de
 * hasta 5 MB, <b>reconocido por sus primeros bytes</b> y nunca por el nombre ni por el tipo que
 * declare el cliente.
 *
 * <p>Se valida <b>antes de tocar la base</b>, en el orden de {@link ImageSignature#validar}: vacío
 * (`VAL-002`), tamaño (`VAL-004`) y firma (`VAL-003`) —leer la firma de cincuenta megas para decir
 * que no es un PDF es trabajo tirado—. Las firmas de PNG y JPEG son las de {@link ImageSignature}:
 * escritas una vez. WebP no se admite aquí.
 */
public final class PointsReceipt {

  /** Cinco megabytes: {@code ck_points_adjustment_receipts_size} dice lo mismo. */
  public static final int TAMANO_MAXIMO = ImageSignature.TAMANO_MAXIMO;

  /** La parte del {@code multipart} y el campo que nombran los rechazos. */
  public static final String CAMPO = "file";

  static final int NOMBRE_MAXIMO = 255;

  private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

  private final String fileName;
  private final String contentType;
  private final byte[] content;
  private final String sha256;

  private PointsReceipt(String fileName, String contentType, byte[] content, String sha256) {
    this.fileName = fileName;
    this.contentType = contentType;
    this.content = content;
    this.sha256 = sha256;
  }

  /** El comprobante, o el rechazo del archivo. {@code nombre} puede ser nulo. */
  public static PointsReceipt de(String nombre, byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      throw rechazo("VAL-002", "El comprobante no puede estar vacío.");
    }
    if (bytes.length > TAMANO_MAXIMO) {
      throw rechazo("VAL-004", "El comprobante no puede pesar más de 5 MB.");
    }
    String tipo = tipoDe(bytes);
    if (tipo == null) {
      throw rechazo("VAL-003", "El comprobante debe ser un PDF, un PNG o un JPG.");
    }
    return new PointsReceipt(nombreLimpio(nombre, tipo), tipo, bytes.clone(), resumen(bytes));
  }

  /** El archivo es obligatorio en el camino que solo adjunta (`RF-MV-057` `VAL-001`). */
  public static ValidationException ausente() {
    return rechazo("VAL-001", "El comprobante es obligatorio.");
  }

  /** El tipo que dicen los bytes, o nulo si no es ninguno de los tres. */
  static String tipoDe(byte[] bytes) {
    if (bytes.length >= PDF.length && Arrays.equals(bytes, 0, PDF.length, PDF, 0, PDF.length)) {
      return "application/pdf";
    }
    return ImageSignature.de(bytes)
        .filter(firma -> firma == ImageSignature.PNG || firma == ImageSignature.JPEG)
        .map(ImageSignature::contentType)
        .orElse(null);
  }

  /**
   * Solo el nombre: sin ruta —ni {@code /} ni {@code \}—, sin caracteres de control y acotado. Sin
   * nada que quede, uno por omisión con la extensión de su tipo.
   */
  static String nombreLimpio(String nombre, String tipo) {
    String limpio = nombre == null ? "" : nombre;
    int barra = Math.max(limpio.lastIndexOf('/'), limpio.lastIndexOf('\\'));
    limpio = limpio.substring(barra + 1).replaceAll("\\p{Cntrl}", "").strip();
    if (limpio.isEmpty()) {
      limpio = "comprobante" + extension(tipo);
    }
    return limpio.length() <= NOMBRE_MAXIMO ? limpio : limpio.substring(0, NOMBRE_MAXIMO);
  }

  private static String extension(String tipo) {
    return switch (tipo) {
      case "application/pdf" -> ".pdf";
      case "image/png" -> ".png";
      default -> ".jpg";
    };
  }

  private static String resumen(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("La JVM no trae SHA-256.", e);
    }
  }

  private static ValidationException rechazo(String codigo, String mensaje) {
    return new ValidationException(
        codigo, mensaje, List.of(new FieldError(CAMPO, codigo, mensaje)));
  }

  public String fileName() {
    return fileName;
  }

  public String contentType() {
    return contentType;
  }

  public byte[] content() {
    return content.clone();
  }

  public int sizeBytes() {
    return content.length;
  }

  public String sha256() {
    return sha256;
  }
}
