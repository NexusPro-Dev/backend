package com.factech.nexus.modules.movements.infrastructure;

import com.factech.nexus.modules.movements.domain.service.ShopSecrets;
import com.factech.nexus.shared.crypto.AesGcmCipher;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM con la llave maestra {@code PAYRETAILERS_ENCRYPTION_KEY} —32 bytes en Base64—
 * (`deployment.md` §6.5.2). El texto cifrado es {@code v1:<base64(iv ‖ cifrado ‖ etiqueta)>}, con
 * un iv aleatorio de 12 bytes por cifrado y el país como dato asociado. <b>Cambiar la llave deja
 * ilegibles las claves guardadas</b>: hay que volver a fijarlas.
 *
 * <p>La mecánica vive en {@link AesGcmCipher} desde el 06-10-2026, compartida con el secreto del
 * segundo factor (`RF-SP-071`); aquí quedan la llave, la tolerancia a que falte y los mensajes.
 */
@Component
public class AesGcmShopSecrets implements ShopSecrets {

  private static final Logger LOG = LoggerFactory.getLogger(AesGcmShopSecrets.class);

  /** Nulo si la llave falta o no vale: la pasarela local puede no estar contratada. */
  private final AesGcmCipher cifrado;

  public AesGcmShopSecrets(PayRetailersSettings ajustes) {
    String llave = ajustes.encryptionKey();
    this.cifrado = AesGcmCipher.conLlave(llave).orElse(null);
    if (cifrado == null && llave != null && !llave.isBlank()) {
      LOG.error("PAYRETAILERS_ENCRYPTION_KEY no es Base64 de 32 bytes (AES-256): se ignora.");
    }
  }

  @Override
  public boolean ready() {
    return cifrado != null;
  }

  @Override
  public String encrypt(String plain, UUID countryId) {
    exigirLlave();
    try {
      return cifrado.encrypt(plain, countryId);
    } catch (IllegalStateException fallo) {
      throw new IllegalStateException("No se pudo cifrar la clave de la tienda.", fallo);
    }
  }

  @Override
  public String decrypt(String encrypted, UUID countryId) {
    exigirLlave();
    try {
      return cifrado.decrypt(encrypted, countryId);
    } catch (IllegalStateException fallo) {
      // Sin la causa en el mensaje: no hay nada útil en ella y no se arriesga a filtrar nada.
      throw new IllegalStateException(
          "La clave de la tienda no se puede descifrar con la llave de este entorno.");
    }
  }

  private void exigirLlave() {
    if (cifrado == null) {
      throw new IllegalStateException("Falta PAYRETAILERS_ENCRYPTION_KEY.");
    }
  }
}
