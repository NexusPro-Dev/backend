package com.factech.nexus.modules.products.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `RF-PM-014` — lo que el sistema sabe de una imagen, que son unos veinte bytes de firmas.
 *
 * <p><b>Ningún archivo de imagen entra al repositorio</b>: los bytes se generan — una firma más
 * relleno hasta el tamaño deseado. Es todo lo que el detector mira, y es lo que hace que un archivo
 * de cinco megas que no es una imagen se rechace en el mismo tiempo que uno de doce bytes.
 */
public class ProductImageTest {

  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 9, 14, 12, 0, 0, 0, ZoneOffset.UTC);

  public static final byte[] JPEG =
      firma(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46);
  public static final byte[] PNG =
      firma(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00);
  public static final byte[] WEBP =
      firma(0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50, 0x56, 0x50);
  public static final byte[] GIF =
      firma(0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x00, 0x00, 0x00, 0x00);
  public static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes();
  public static final byte[] TEXTO = "esto no es una imagen aunque se llame foto.png".getBytes();

  @Test
  @DisplayName("`CA-PM-242` — el tipo lo deciden los bytes: JPEG, PNG y WebP por su firma")
  void lasTresFirmas() {
    assertThat(ImageSignature.de(JPEG)).contains(ImageSignature.JPEG);
    assertThat(ImageSignature.de(PNG)).contains(ImageSignature.PNG);
    assertThat(ImageSignature.de(WEBP)).contains(ImageSignature.WEBP);

    assertThat(ProductImage.de(UUID.randomUUID(), JPEG, AHORA).getContentType())
        .isEqualTo("image/jpeg");
    assertThat(ProductImage.de(UUID.randomUUID(), PNG, AHORA).getContentType())
        .isEqualTo("image/png");
    assertThat(ProductImage.de(UUID.randomUUID(), WEBP, AHORA).getContentType())
        .isEqualTo("image/webp");
  }

  @Test
  @DisplayName("`VAL-003` — GIF, SVG, texto y un solo byte se rechazan nombrando `file`")
  void firmasQueNoSon() {
    // `RIFF` sin `WEBP` en el octavo byte: un WAV, por ejemplo.
    byte[] riffQueNoEsWebp =
        firma(0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x57, 0x41, 0x56, 0x45);
    for (byte[] malo : new byte[][] {GIF, SVG, TEXTO, new byte[] {(byte) 0xFF}, riffQueNoEsWebp}) {
      assertThat(ImageSignature.de(malo)).isEmpty();
      ValidationException fallo =
          catchThrowableOfType(
              () -> ProductImage.de(UUID.randomUUID(), malo, AHORA), ValidationException.class);
      assertThat(fallo).isNotNull();
      assertThat(fallo.errorCode()).isEqualTo("VAL-003");
      assertThat(fallo.errors()).extracting(FieldError::field).containsExactly("file");
    }
  }

  @Test
  @DisplayName("`VAL-002` — sin bytes no hay imagen: nulo y vacío, antes que cualquier otra cosa")
  void sinBytes() {
    for (byte[] nada : new byte[][] {null, new byte[0]}) {
      ValidationException fallo =
          catchThrowableOfType(
              () -> ProductImage.de(UUID.randomUUID(), nada, AHORA), ValidationException.class);
      assertThat(fallo.errorCode()).isEqualTo("VAL-002");
      assertThat(fallo.errors()).extracting(FieldError::field).containsExactly("file");
    }
  }

  @Test
  @DisplayName("`CA-PM-243` — 5 242 880 bytes caben; 5 242 881 es VAL-004, y antes que el tipo")
  void elTope() {
    byte[] justo = relleno(PNG, ProductImage.TAMANO_MAXIMO);
    ProductImage imagen = ProductImage.de(UUID.randomUUID(), justo, AHORA);
    assertThat(imagen.getContent()).hasSize(ProductImage.TAMANO_MAXIMO);
    assertThat(imagen.getContent()).isSameAs(justo); // tal cual, sin copiar ni tratar

    byte[] unoDeMas = relleno(PNG, ProductImage.TAMANO_MAXIMO + 1);
    assertThat(
            catchThrowableOfType(
                    () -> ProductImage.de(UUID.randomUUID(), unoDeMas, AHORA),
                    ValidationException.class)
                .errorCode())
        .isEqualTo("VAL-004");

    // El tamaño se mira ANTES que el tipo: un texto enorme dice «demasiado
    // grande», que es el mensaje útil, y no «no es una imagen».
    byte[] textoEnorme = relleno(TEXTO, ProductImage.TAMANO_MAXIMO + 1);
    assertThat(
            catchThrowableOfType(
                    () -> ProductImage.de(UUID.randomUUID(), textoEnorme, AHORA),
                    ValidationException.class)
                .errorCode())
        .isEqualTo("VAL-004");
  }

  @Test
  @DisplayName("la imagen lleva su identificador, su tipo y cuándo se subió; nada más")
  void loQueGuarda() {
    UUID id = UUID.randomUUID();
    ProductImage imagen = ProductImage.de(id, JPEG, AHORA);
    assertThat(imagen.getId()).isEqualTo(id);
    assertThat(imagen.getCreatedAt()).isEqualTo(AHORA);
  }

  public static byte[] firma(int... bytes) {
    byte[] salida = new byte[bytes.length];
    for (int i = 0; i < bytes.length; i++) {
      salida[i] = (byte) bytes[i];
    }
    return salida;
  }

  public static byte[] relleno(byte[] cabecera, int tamano) {
    byte[] salida = Arrays.copyOf(cabecera, tamano);
    Arrays.fill(salida, cabecera.length, tamano, (byte) 0x2A);
    return salida;
  }
}
