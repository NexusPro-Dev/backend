package com.factech.nexus.modules.products.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El enlace de un producto (`RF-PM-001` · `T-43`, `RN-PM-048`, `RN-PM-049`).
 *
 * <p>Sin Spring, como {@link ProductTest} y por el mismo motivo: lo que se comprueba es que un
 * enlace mal formado <b>no pueda existir dentro del modelo</b>, venga del alta, de la corrección o
 * de una siembra. Aquí vive lo que hasta el 22-09-2026 se probaba sobre {@code video_url} en {@link
 * ProductTest}, y una cosa que allí no existía: <b>la resolución</b>, que es la regla que seis
 * lecturas van a usar y que por eso se prueba en sus cuatro casos.
 *
 * <p><b>Los códigos de validación se pasan por parámetro</b> y no los decide el modelo: son
 * `VAL-021`, `VAL-017`, `VAL-022` y `VAL-023` en el alta, y `VAL-016`, `VAL-009`, `VAL-017` y
 * `VAL-018` en la corrección: la misma comprobación con el número que cada especificación le dio,
 * igual que hacía el enlace del video entre el alta (`VAL-017`) y la corrección (`VAL-009`).
 */
class ProductLinkTest {

  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 9, 22, 12, 0, 0, 0, ZoneOffset.UTC);
  private static final UUID PRODUCTO = UUID.randomUUID();

  private static final String OBLIGATORIA = "VAL-021";
  private static final String DIRECCION = "VAL-017";
  private static final String IDENTIFICADOR = "VAL-022";
  private static final String CRUZADO = "VAL-023";

  // ---------------------------------------------------------------------------
  // Las cuatro validaciones
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`RN-PM-048` — la dirección se recorta y NO se normaliza nada más")
  void direccionRecortadaYTalCual() {
    // Ni minúsculas ni barra final: un identificador de video distingue
    // mayúsculas, y un enlace «arreglado» puede dejar de resolver. Es la misma
    // decisión que traía `video_url` desde el 14-09-2026.
    assertThat(enlace("  https://Vimeo.com/123456/  ", null).getUrl())
        .isEqualTo("https://Vimeo.com/123456/");
    assertThat(enlace("http://example.com", null).getUrl()).isEqualTo("http://example.com");
    assertThat(enlace("https://t.me/bot", "  cupon-15  ").getExternalId()).isEqualTo("cupon-15");
  }

  @Test
  @DisplayName("`VAL-021` — la dirección es obligatoria: ausente, nula o vacía se rechazan")
  void direccionObligatoria() {
    for (String vacio : new String[] {null, "", "   "}) {
      ValidationException fallo =
          catchThrowableOfType(() -> enlace(vacio, null), ValidationException.class);

      assertThat(fallo).as("debía rechazar «%s»", vacio).isNotNull();
      assertThat(fallo.errorCode()).isEqualTo(OBLIGATORIA);
      // El campo lleva el ÍNDICE: con dos enlaces en la petición, sin él habría
      // que probar los dos para saber cuál falló.
      assertThat(fallo.errors()).extracting(FieldError::field).containsExactly("links[0].url");
    }
  }

  @Test
  @DisplayName("`VAL-017` — la dirección sin forma de URL absoluta http(s) se rechaza")
  void direccionConFormaInvalida() {
    String[] malos = {
      "/videos/asesoria.mp4",
      "www.youtube.com/watch?v=x",
      "ftp://videos.example.com/x.mp4",
      "https://www.youtube.com/watch?v=dQw4 w9WgXcQ",
      "https://",
      "https://example.com/" + "a".repeat(481)
    };
    for (String malo : malos) {
      ValidationException fallo =
          catchThrowableOfType(() -> enlace(malo, null), ValidationException.class);

      assertThat(fallo).as("debía rechazar «%s»", malo).isNotNull();
      assertThat(fallo.errorCode()).isEqualTo(DIRECCION);
      assertThat(fallo.errors()).extracting(FieldError::field).containsExactly("links[0].url");
    }
    // Y quinientos exactos SÍ caben.
    assertThatCode(() -> enlace("https://example.com/" + "a".repeat(480), null))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("`VAL-022` — el identificador no admite espacios ni pasa de 100 caracteres")
  void identificadorConFormaInvalida() {
    for (String malo : new String[] {"cupon 15", "a".repeat(101)}) {
      ValidationException fallo =
          catchThrowableOfType(() -> enlace("https://t.me/bot", malo), ValidationException.class);

      assertThat(fallo).as("debía rechazar «%s»", malo).isNotNull();
      assertThat(fallo.errorCode()).isEqualTo(IDENTIFICADOR);
      assertThat(fallo.errors())
          .extracting(FieldError::field)
          .containsExactly("links[0].externalId");
    }
    // Cien exactos caben; y el vacío NO es un error: es no declararlo.
    assertThatCode(() -> enlace("https://t.me/bot", "a".repeat(100))).doesNotThrowAnyException();
    assertThat(enlace("https://t.me/bot", "   ").getExternalId()).isNull();
    assertThat(enlace("https://t.me/bot", null).getExternalId()).isNull();
  }

  @Test
  @DisplayName("`VAL-023` — con identificador, la dirección no admite `?` ni `#`; sin él, sí")
  void identificadorSobreDireccionConConsulta() {
    for (String direccion :
        new String[] {"https://www.youtube.com/watch?v=abc", "https://example.com/p#seccion"}) {
      ValidationException fallo =
          catchThrowableOfType(() -> enlace(direccion, "abc"), ValidationException.class);

      assertThat(fallo).as("debía rechazar «%s»", direccion).isNotNull();
      assertThat(fallo.errorCode()).isEqualTo(CRUZADO);
      assertThat(fallo.errors()).extracting(FieldError::field).containsExactly("links[0].url");

      // Es una restricción CRUZADA y no una prohibición sobre la dirección: la
      // misma, sin identificador, se admite — un video de YouTube es
      // exactamente eso.
      assertThatCode(() -> enlace(direccion, null)).doesNotThrowAnyException();
    }
  }

  @Test
  @DisplayName("el error nombra el enlace por su ÍNDICE dentro de la colección")
  void elErrorNombraElIndice() {
    ValidationException fallo =
        catchThrowableOfType(
            () ->
                ProductLink.create(
                    PRODUCTO,
                    ProductLinkType.CUPON_BOT,
                    "sin-esquema",
                    null,
                    AHORA,
                    OBLIGATORIA,
                    DIRECCION,
                    IDENTIFICADOR,
                    CRUZADO,
                    2),
            ValidationException.class);

    assertThat(fallo.errors()).extracting(FieldError::field).containsExactly("links[2].url");
  }

  // ---------------------------------------------------------------------------
  // La resolución (`RN-PM-049`), en sus cuatro casos
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("`RN-PM-049` — con identificador se pega como último segmento; sin él, tal cual")
  void resolucionEnSusCuatroCasos() {
    // Con identificador y sin barra final.
    assertThat(enlace("https://t.me/bot", "cupon-15").resolver())
        .isEqualTo("https://t.me/bot/cupon-15");
    // Con identificador y CON barra final: la barra no se duplica.
    assertThat(enlace("https://t.me/bot/", "cupon-15").resolver())
        .isEqualTo("https://t.me/bot/cupon-15");
    // Sin identificador y sin barra final: la dirección, intacta.
    assertThat(enlace("https://vimeo.com/123456", null).resolver())
        .isEqualTo("https://vimeo.com/123456");
    // Sin identificador y CON barra final: TAMBIÉN intacta — resolver no
    // normaliza, solo compone. Quitarle la barra aquí cambiaría un enlace que
    // nadie pidió cambiar.
    assertThat(enlace("https://vimeo.com/123456/", null).resolver())
        .isEqualTo("https://vimeo.com/123456/");
  }

  // ---------------------------------------------------------------------------
  // La instantánea y la comparación por valor
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("la instantánea lleva tipo, dirección e identificador, y el nulo se conserva")
  void laInstantanea() {
    assertThat(enlace("https://vimeo.com/1", null).instantanea())
        .containsExactly(
            org.assertj.core.api.Assertions.entry("type", "CUPON_BOT"),
            org.assertj.core.api.Assertions.entry("url", "https://vimeo.com/1"),
            org.assertj.core.api.Assertions.entry("external_id", null));
  }

  @Test
  @DisplayName(
      "`mismoValorQue` compara por valor y no por identidad: es lo que evita el falso diff")
  void comparacionPorValor() {
    ProductLink uno = enlace("https://t.me/bot", "cupon-15");

    assertThat(uno.mismoValorQue(enlace("https://t.me/bot", "cupon-15"))).isTrue();
    assertThat(uno.mismoValorQue(enlace("https://t.me/bot", "cupon-16"))).isFalse();
    assertThat(uno.mismoValorQue(enlace("https://t.me/otro", "cupon-15"))).isFalse();
    assertThat(uno.mismoValorQue(enlace("https://t.me/bot", null))).isFalse();
    assertThat(uno.mismoValorQue(null)).isFalse();
    assertThat(
            uno.mismoValorQue(
                ProductLink.create(
                    PRODUCTO,
                    ProductLinkType.VIDEO_PRESENTACION,
                    "https://t.me/bot",
                    "cupon-15",
                    AHORA,
                    OBLIGATORIA,
                    DIRECCION,
                    IDENTIFICADOR,
                    CRUZADO,
                    0)))
        .as("el tipo forma parte del valor")
        .isFalse();
  }

  @Test
  @DisplayName("el tipo sabe si es material de venta: el video sí, el cupón no (`RN-PM-050`)")
  void elTipoSabeDondeSePublica() {
    assertThat(ProductLinkType.VIDEO_PRESENTACION.esMaterialDeVenta()).isTrue();
    assertThat(ProductLinkType.CUPON_BOT.esMaterialDeVenta()).isFalse();
  }

  private static ProductLink enlace(String url, String externalId) {
    return ProductLink.create(
        PRODUCTO,
        ProductLinkType.CUPON_BOT,
        url,
        externalId,
        AHORA,
        OBLIGATORIA,
        DIRECCION,
        IDENTIFICADOR,
        CRUZADO,
        0);
  }
}
