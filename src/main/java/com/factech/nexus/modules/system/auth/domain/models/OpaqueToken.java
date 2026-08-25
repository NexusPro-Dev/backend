package com.factech.nexus.modules.system.auth.domain.models;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generación y resumen del refresh token.
 *
 * <p><b>Es un valor opaco aleatorio y no un JWT</b> (`security.md` §5.2): no lleva información, de
 * modo que no hay nada que leer en él, y es revocable porque el servidor guarda su rastro.
 *
 * <p><b>Se resume con SHA-256 y no con Argon2</b>, al revés que una contraseña, y la diferencia es
 * la entropía. Una contraseña la elige una persona y tiene poca: hay que encarecer cada intento
 * para que un ataque por diccionario no sea viable. Este valor son 256 bits de aleatoriedad
 * criptográfica; no hay diccionario que probar, y un resumen lento solo penalizaría cada refresco
 * legítimo.
 *
 * <p>Lo que sí importa —y SHA-256 lo da— es que del resumen no se pueda volver al valor: quien lea
 * la tabla no puede usar lo que ve.
 */
public final class OpaqueToken {

  private static final SecureRandom ALEATORIO = new SecureRandom();

  /** 32 bytes = 256 bits. Adivinarlo no es un ataque, es un imposible. */
  private static final int BYTES = 32;

  private OpaqueToken() {}

  /** Un valor nuevo, en base64 sin relleno y seguro para URL. */
  public static String generar() {
    byte[] material = new byte[BYTES];
    ALEATORIO.nextBytes(material);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(material);
  }

  /** El resumen que se guarda. Es la única forma en que el token existe en el servidor. */
  public static String resumen(String token) {
    try {
      MessageDigest sha = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(sha.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException imposible) {
      // SHA-256 es obligatorio en toda implementación de la plataforma Java.
      throw new IllegalStateException("SHA-256 no disponible", imposible);
    }
  }
}
