package com.factech.nexus.modules.system.auth.infrastructure;

import com.factech.nexus.shared.crypto.AesGcmCipher;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Cifra y descifra el secreto TOTP de cada persona (`RF-SP-071`; `requirements/sp.md` §10.22).
 *
 * <p>AES-256-GCM con {@code MFA_ENCRYPTION_KEY} y <b>el identificador de la persona como dato
 * asociado</b>: una fila copiada a otra cuenta no descifra.
 *
 * <p><b>Sin llave el contexto no arranca</b>, al contrario que la tienda de PayRetailers, que se
 * tolera vacía porque la pasarela puede no estar contratada. Aquí no hay forma segura de guardar el
 * factor de nadie, y un despliegue que lo descubriera al primer intento de activación fallaría ante
 * la persona en lugar de ante quien despliega.
 */
@Component
public class MfaSecrets {

  private final AesGcmCipher cifrado;

  public MfaSecrets(MfaSettings ajustes) {
    this.cifrado =
        AesGcmCipher.conLlave(ajustes.encryptionKey())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "MFA_ENCRYPTION_KEY falta o no es Base64 de 32 bytes (AES-256). "
                            + "Sin ella no se puede guardar el segundo factor de nadie."));
  }

  public String cifrar(String secretoBase32, UUID userId) {
    return cifrado.encrypt(secretoBase32, userId);
  }

  public String descifrar(String cifrado, UUID userId) {
    return this.cifrado.decrypt(cifrado, userId);
  }
}
