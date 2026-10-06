package com.factech.nexus.shared.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM con un {@code UUID} como dato asociado, en el formato {@code
 * v1:<base64(nonce‖cifrado)>}.
 *
 * <p><b>La mecánica, sin llave propia.</b> La usan la clave de la tienda de PayRetailers ({@code
 * RN-MV-063}) y el secreto del segundo factor ({@code RF-SP-071}), cada una con su llave y su
 * mensaje de error. Dos copias del mismo cifrado serían dos sitios donde equivocarse con el nonce
 * —que en GCM <b>no puede repetirse nunca</b> con la misma llave—, y por eso vive aquí una vez.
 *
 * <p><b>El dato asociado ata el cifrado a su dueño</b>: lo cifrado para un país, o para una
 * persona, no se descifra como si fuera de otro. Una fila copiada a otra cuenta no sirve.
 */
public final class AesGcmCipher {

  private static final String VERSION = "v1:";
  private static final int IV = 12;
  private static final int ETIQUETA = 128;

  private final SecretKey llave;
  private final SecureRandom azar = new SecureRandom();

  private AesGcmCipher(SecretKey llave) {
    this.llave = llave;
  }

  /**
   * La llave en Base64, que debe dar 32 bytes. Vacío si falta, no es Base64 o no mide 32: quien
   * llama decide si eso es tolerable —la tienda de PayRetailers puede no estar contratada— o si
   * impide arrancar —el segundo factor no tiene forma segura de guardarse sin ella—.
   */
  public static Optional<AesGcmCipher> conLlave(String base64) {
    if (base64 == null || base64.isBlank()) {
      return Optional.empty();
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(base64.trim());
    } catch (IllegalArgumentException noEsBase64) {
      return Optional.empty();
    }
    if (bytes.length != 32) {
      return Optional.empty();
    }
    return Optional.of(new AesGcmCipher(new SecretKeySpec(bytes, "AES")));
  }

  public String encrypt(String plain, UUID asociado) {
    try {
      byte[] iv = new byte[IV];
      azar.nextBytes(iv);
      Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
      cifrador.init(Cipher.ENCRYPT_MODE, llave, new GCMParameterSpec(ETIQUETA, iv));
      cifrador.updateAAD(bytes(asociado));
      byte[] cifrado = cifrador.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      byte[] todo = ByteBuffer.allocate(IV + cifrado.length).put(iv).put(cifrado).array();
      return VERSION + Base64.getEncoder().encodeToString(todo);
    } catch (GeneralSecurityException fallo) {
      throw new IllegalStateException("No se pudo cifrar.", fallo);
    }
  }

  /**
   * Descifra, o lanza {@link IllegalStateException} <b>sin la causa</b>: no hay nada útil en ella y
   * no se arriesga a filtrar nada. Un formato desconocido, una llave distinta y un dato asociado
   * que no corresponde dan el mismo error.
   */
  public String decrypt(String encrypted, UUID asociado) {
    if (encrypted == null || !encrypted.startsWith(VERSION)) {
      throw new IllegalStateException("El cifrado no tiene un formato conocido.");
    }
    try {
      byte[] todo = Base64.getDecoder().decode(encrypted.substring(VERSION.length()));
      Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
      cifrador.init(Cipher.DECRYPT_MODE, llave, new GCMParameterSpec(ETIQUETA, todo, 0, IV));
      cifrador.updateAAD(bytes(asociado));
      byte[] plano = cifrador.doFinal(todo, IV, todo.length - IV);
      return new String(plano, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException fallo) {
      throw new IllegalStateException("No se puede descifrar con la llave de este entorno.");
    }
  }

  private static byte[] bytes(UUID asociado) {
    return asociado.toString().getBytes(StandardCharsets.UTF_8);
  }
}
