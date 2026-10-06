package com.factech.nexus.modules.movements.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.shared.error.ValidationException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** `RN-MV-077`: qué archivo se admite como comprobante de un ajuste, y cómo se guarda. */
class PointsReceiptTest {

  private static final byte[] PDF = "%PDF-1.4 resto".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
  private static final byte[] JPG = {
    (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xDB, 0x00, 0x43, 0x00, 0x08, 0x06
  };
  private static final byte[] WEBP = {
    0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50
  };

  @Test
  @DisplayName("Los tres tipos se reconocen por sus bytes, no por el nombre")
  void tipos() {
    assertThat(PointsReceipt.de("x.png", PDF).contentType()).isEqualTo("application/pdf");
    assertThat(PointsReceipt.de("x.pdf", PNG).contentType()).isEqualTo("image/png");
    assertThat(PointsReceipt.de("x.gif", JPG).contentType()).isEqualTo("image/jpeg");
  }

  @Test
  @DisplayName("WebP, texto y un PDF truncado se rechazan con VAL-003")
  void rechazados() {
    for (byte[] malo :
        new byte[][] {WEBP, "hola".getBytes(StandardCharsets.UTF_8), "%PD".getBytes()}) {
      assertThatThrownBy(() -> PointsReceipt.de("a.pdf", malo))
          .isInstanceOf(ValidationException.class)
          .hasFieldOrPropertyWithValue("errorCode", "VAL-003");
    }
  }

  @Test
  @DisplayName("Vacío es VAL-002 y más de 5 MB es VAL-004, antes de mirar la firma")
  void vacioYTamano() {
    assertThatThrownBy(() -> PointsReceipt.de("a.pdf", new byte[0]))
        .hasFieldOrPropertyWithValue("errorCode", "VAL-002");
    byte[] grande = Arrays.copyOf("texto".getBytes(), PointsReceipt.TAMANO_MAXIMO + 1);
    assertThatThrownBy(() -> PointsReceipt.de("a.pdf", grande))
        .hasFieldOrPropertyWithValue("errorCode", "VAL-004");
    assertThat(
            PointsReceipt.de("a.pdf", Arrays.copyOf(PDF, PointsReceipt.TAMANO_MAXIMO)).sizeBytes())
        .isEqualTo(PointsReceipt.TAMANO_MAXIMO);
  }

  @Test
  @DisplayName("El nombre pierde la ruta y los caracteres de control; vacío, uno por omisión")
  void nombre() {
    assertThat(PointsReceipt.de("C:\\a\\b/recibo.pdf", PDF).fileName()).isEqualTo("recibo.pdf");
    assertThat(PointsReceipt.de("re\u0000ci\nbo.png", PNG).fileName()).isEqualTo("recibo.png");
    assertThat(PointsReceipt.de(null, JPG).fileName()).isEqualTo("comprobante.jpg");
    assertThat(PointsReceipt.de("   ", PNG).fileName()).isEqualTo("comprobante.png");
    assertThat(PointsReceipt.de("x".repeat(400) + ".pdf", PDF).fileName()).hasSize(255);
  }

  @Test
  @DisplayName("El resumen es el SHA-256 en hexadecimal, y depende solo del contenido")
  void resumen() {
    PointsReceipt uno = PointsReceipt.de("a.pdf", PDF);
    assertThat(uno.sha256()).hasSize(64).matches("[0-9a-f]+");
    assertThat(PointsReceipt.de("b.pdf", PDF).sha256()).isEqualTo(uno.sha256());
    assertThat(PointsReceipt.de("a.png", PNG).sha256()).isNotEqualTo(uno.sha256());
  }
}
