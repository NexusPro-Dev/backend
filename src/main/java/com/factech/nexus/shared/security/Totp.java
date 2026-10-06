package com.factech.nexus.shared.security;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Códigos de un solo uso por tiempo, RFC 6238 (`RF-SP-071`, `RN-SP-060`).
 *
 * <p><b>Seis dígitos, treinta segundos, HMAC-SHA1</b>: lo que toda app autenticadora entiende sin
 * que nadie configure nada. Escrito a mano y no con una librería (`071` · `plan.md` §1): son el
 * truncado dinámico de RFC 4226 §5.3 y un módulo, y los vectores de RFC 6238 Apéndice B lo
 * verifican entero.
 *
 * <p>Sin estado y sin Spring: el reloj lo pone quien llama, y con él las pruebas.
 */
public final class Totp {

  public static final int DIGITOS = 6;
  public static final long PERIODO_SEGUNDOS = 30;

  /** Bytes del secreto: 160 bits, lo que RFC 4226 §4 recomienda para SHA-1. */
  public static final int BYTES_DEL_SECRETO = 20;

  private static final int MODULO = 1_000_000;
  private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
  private static final SecureRandom AZAR = new SecureRandom();

  private Totp() {}

  /** Un secreto nuevo, en Base32 sin relleno: lo que lleva la URI y lo que se cifra. */
  public static String nuevoSecreto() {
    byte[] bytes = new byte[BYTES_DEL_SECRETO];
    AZAR.nextBytes(bytes);
    return base32(bytes);
  }

  /** El periodo al que pertenece un instante. */
  public static long periodo(Instant instante) {
    return Math.floorDiv(instante.getEpochSecond(), PERIODO_SEGUNDOS);
  }

  /** El código de un periodo, con sus ceros a la izquierda. */
  public static String codigo(String secretoBase32, long periodo) {
    byte[] hash = hmac(desdeBase32(secretoBase32), periodo);
    int desplazamiento = hash[hash.length - 1] & 0x0f;
    int binario =
        ((hash[desplazamiento] & 0x7f) << 24)
            | ((hash[desplazamiento + 1] & 0xff) << 16)
            | ((hash[desplazamiento + 2] & 0xff) << 8)
            | (hash[desplazamiento + 3] & 0xff);
    return String.format("%0" + DIGITOS + "d", binario % MODULO);
  }

  /**
   * El periodo en que casa el código, o vacío.
   *
   * <p>Se acepta el periodo actual y <b>uno a cada lado</b>, por desfase de reloj; y solo uno
   * <b>posterior al último usado</b>, para que un código no sirva dos veces ni siquiera dentro de
   * sus noventa segundos de vida (`RN-SP-060`). Se prueban los tres siempre y se comparan en tiempo
   * constante: cortar en el primero que casa diría, por el tiempo de respuesta, en cuál casó.
   */
  public static OptionalLong periodoQueCasa(
      String secretoBase32, String codigo, Instant ahora, Long ultimoUsado) {
    if (codigo == null
        || codigo.length() != DIGITOS
        || !codigo.chars().allMatch(Character::isDigit)) {
      return OptionalLong.empty();
    }
    long actual = periodo(ahora);
    long casado = Long.MIN_VALUE;
    for (long p = actual - 1; p <= actual + 1; p++) {
      boolean igual = MessageDigest.isEqual(codigo(secretoBase32, p).getBytes(), codigo.getBytes());
      if (igual && casado == Long.MIN_VALUE) {
        casado = p;
      }
    }
    if (casado == Long.MIN_VALUE || (ultimoUsado != null && casado <= ultimoUsado)) {
      return OptionalLong.empty();
    }
    return OptionalLong.of(casado);
  }

  /** La URI que la app autenticadora convierte en una entrada: emisor, cuenta y secreto. */
  public static String uri(String emisor, String cuenta, String secretoBase32) {
    String e = urlEncode(emisor);
    return "otpauth://totp/"
        + e
        + ":"
        + urlEncode(cuenta)
        + "?secret="
        + secretoBase32
        + "&issuer="
        + e
        + "&algorithm=SHA1&digits="
        + DIGITOS
        + "&period="
        + PERIODO_SEGUNDOS;
  }

  private static byte[] hmac(byte[] llave, long periodo) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(llave, "HmacSHA1"));
      return mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(periodo).array());
    } catch (GeneralSecurityException fallo) {
      throw new IllegalStateException("HmacSHA1 no está disponible en esta JVM.", fallo);
    }
  }

  static String base32(byte[] datos) {
    StringBuilder salida = new StringBuilder((datos.length * 8 + 4) / 5);
    int buffer = 0;
    int bits = 0;
    for (byte b : datos) {
      buffer = (buffer << 8) | (b & 0xff);
      bits += 8;
      while (bits >= 5) {
        salida.append(BASE32[(buffer >> (bits - 5)) & 0x1f]);
        bits -= 5;
      }
    }
    if (bits > 0) {
      salida.append(BASE32[(buffer << (5 - bits)) & 0x1f]);
    }
    return salida.toString();
  }

  static byte[] desdeBase32(String texto) {
    String limpio = texto.replace("=", "").replace(" ", "").toUpperCase();
    ByteBuffer salida = ByteBuffer.allocate(limpio.length() * 5 / 8);
    int buffer = 0;
    int bits = 0;
    for (char c : limpio.toCharArray()) {
      int valor = c >= 'A' && c <= 'Z' ? c - 'A' : c >= '2' && c <= '7' ? c - '2' + 26 : -1;
      if (valor < 0) {
        throw new IllegalArgumentException("El secreto no es Base32.");
      }
      buffer = (buffer << 5) | valor;
      bits += 5;
      if (bits >= 8) {
        salida.put((byte) (buffer >> (bits - 8)));
        bits -= 8;
      }
    }
    return salida.array();
  }

  private static String urlEncode(String valor) {
    return java.net.URLEncoder.encode(valor, java.nio.charset.StandardCharsets.UTF_8)
        .replace("+", "%20");
  }
}
