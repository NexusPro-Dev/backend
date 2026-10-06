package com.factech.nexus.shared.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `RF-SP-071` · `T-02`: el cifrado compartido por la tienda de PayRetailers y el segundo factor.
 */
class AesGcmCipherTest {

  private static final String LLAVE = "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=";
  private static final String OTRA_LLAVE = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

  private final AesGcmCipher cifrado = AesGcmCipher.conLlave(LLAVE).orElseThrow();

  @Test
  @DisplayName("Ida y vuelta con el mismo dato asociado")
  void idaYVuelta() {
    UUID dueno = UUID.randomUUID();

    String cifrada = cifrado.encrypt("JBSWY3DPEHPK3PXP", dueno);

    assertThat(cifrada).startsWith("v1:").doesNotContain("JBSWY3DPEHPK3PXP");
    assertThat(cifrado.decrypt(cifrada, dueno)).isEqualTo("JBSWY3DPEHPK3PXP");
  }

  @Test
  @DisplayName("Lo cifrado para uno no se descifra como si fuera de otro")
  void datoAsociado() {
    String cifrada = cifrado.encrypt("secreto", UUID.randomUUID());

    assertThatThrownBy(() -> cifrado.decrypt(cifrada, UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("Otra llave no descifra")
  void otraLlave() {
    UUID dueno = UUID.randomUUID();
    String cifrada = cifrado.encrypt("secreto", dueno);

    AesGcmCipher otro = AesGcmCipher.conLlave(OTRA_LLAVE).orElseThrow();

    assertThatThrownBy(() -> otro.decrypt(cifrada, dueno))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("El nonce cambia en cada cifrado: el mismo texto da dos cifrados distintos")
  void nonceDistinto() {
    UUID dueno = UUID.randomUUID();

    assertThat(cifrado.encrypt("secreto", dueno)).isNotEqualTo(cifrado.encrypt("secreto", dueno));
  }

  @Test
  @DisplayName("Una llave ausente, que no es Base64 o que no mide 32 bytes no da cifrado")
  void llavesInvalidas() {
    assertThat(AesGcmCipher.conLlave(null)).isEmpty();
    assertThat(AesGcmCipher.conLlave(" ")).isEmpty();
    assertThat(AesGcmCipher.conLlave("no es base64 ***")).isEmpty();
    assertThat(AesGcmCipher.conLlave("YWJj")).isEmpty();
  }

  @Test
  @DisplayName("Un formato desconocido no se descifra")
  void formatoDesconocido() {
    assertThatThrownBy(() -> cifrado.decrypt("v2:abc", UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> cifrado.decrypt(null, UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
  }
}
