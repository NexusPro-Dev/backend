package com.factech.nexus.modules.movements.infrastructure;

import com.factech.nexus.modules.movements.domain.service.ShopSecrets;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM con la llave maestra {@code PAYRETAILERS_ENCRYPTION_KEY} —32 bytes en Base64—
 * (`deployment.md` §6.5.2). El texto cifrado es {@code v1:<base64(iv ‖ cifrado ‖ etiqueta)>}, con
 * un iv aleatorio de 12 bytes por cifrado y el país como dato asociado. <b>Cambiar la llave deja
 * ilegibles las claves guardadas</b>: hay que volver a fijarlas.
 */
@Component
public class AesGcmShopSecrets implements ShopSecrets {

  private static final Logger LOG = LoggerFactory.getLogger(AesGcmShopSecrets.class);
  private static final String VERSION = "v1:";
  private static final int IV = 12;
  private static final int ETIQUETA = 128;

  private final SecretKey llave;
  private final SecureRandom azar = new SecureRandom();

  public AesGcmShopSecrets(PayRetailersSettings ajustes) {
    this.llave = llave(ajustes.encryptionKey());
  }

  @Override
  public boolean ready() {
    return llave != null;
  }

  @Override
  public String encrypt(String plain, UUID countryId) {
    exigirLlave();
    try {
      byte[] iv = new byte[IV];
      azar.nextBytes(iv);
      Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
      cifrador.init(Cipher.ENCRYPT_MODE, llave, new GCMParameterSpec(ETIQUETA, iv));
      cifrador.updateAAD(asociado(countryId));
      byte[] cifrado = cifrador.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      byte[] todo = ByteBuffer.allocate(IV + cifrado.length).put(iv).put(cifrado).array();
      return VERSION + Base64.getEncoder().encodeToString(todo);
    } catch (GeneralSecurityException fallo) {
      throw new IllegalStateException("No se pudo cifrar la clave de la tienda.", fallo);
    }
  }

  @Override
  public String decrypt(String encrypted, UUID countryId) {
    exigirLlave();
    if (encrypted == null || !encrypted.startsWith(VERSION)) {
      throw new IllegalStateException("La clave de la tienda no tiene un formato conocido.");
    }
    try {
      byte[] todo = Base64.getDecoder().decode(encrypted.substring(VERSION.length()));
      Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
      cifrador.init(Cipher.DECRYPT_MODE, llave, new GCMParameterSpec(ETIQUETA, todo, 0, IV));
      cifrador.updateAAD(asociado(countryId));
      byte[] plano = cifrador.doFinal(todo, IV, todo.length - IV);
      return new String(plano, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException fallo) {
      // Sin la causa en el mensaje: no hay nada útil en ella y no se arriesga a filtrar nada.
      throw new IllegalStateException(
          "La clave de la tienda no se puede descifrar con la llave de este entorno.");
    }
  }

  private void exigirLlave() {
    if (llave == null) {
      throw new IllegalStateException("Falta PAYRETAILERS_ENCRYPTION_KEY.");
    }
  }

  private static byte[] asociado(UUID countryId) {
    return countryId.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static SecretKey llave(String base64) {
    if (base64 == null || base64.isBlank()) {
      return null;
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(base64.trim());
    } catch (IllegalArgumentException noEsBase64) {
      LOG.error("PAYRETAILERS_ENCRYPTION_KEY no es Base64: se ignora.");
      return null;
    }
    if (bytes.length != 32) {
      LOG.error(
          "PAYRETAILERS_ENCRYPTION_KEY tiene {} bytes y debe tener 32 (AES-256): se ignora.",
          bytes.length);
      return null;
    }
    return new SecretKeySpec(bytes, "AES");
  }
}
