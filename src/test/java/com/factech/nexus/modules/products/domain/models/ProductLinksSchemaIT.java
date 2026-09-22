package com.factech.nexus.modules.products.domain.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factech.nexus.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * `RF-PM-001` · `T-42` — lo que `product_links` impide por esquema, y la columna que se fue.
 *
 * <h2>Por qué se prueba aquí y no por el endpoint</h2>
 *
 * <p>El dominio comprueba las mismas cuatro reglas en {@code ProductLink}, y el endpoint las
 * devuelve como {@code 400}. Eso deja sin probar <b>la otra puerta</b>: una siembra, una migración
 * futura o un {@code UPDATE} a mano escriben en la tabla <b>sin pasar por Java</b>. Si las
 * restricciones no estuvieran, nada de eso fallaría — hasta que alguien leyera un enlace roto que
 * responde {@code 200}.
 *
 * <p>Por eso los {@code INSERT} de aquí son <b>directos</b>, y por eso se comprueba también el caso
 * que SÍ entra: una restricción de más —por ejemplo, prohibir {@code ?} en toda dirección en vez de
 * solo con identificador— dejaría fuera un video de YouTube, y ninguna prueba que solo mire
 * rechazos lo notaría.
 */
class ProductLinksSchemaIT extends IntegrationTestBase {

  private static final String USD = "01a03336-6d00-7001-9c4f-5e7ad3000001";

  @Autowired private JdbcTemplate jdbc;

  private UUID producto;

  @BeforeEach
  void sembrar() {
    limpiar();
    producto = producto("PL_BOT");
  }

  @AfterEach
  void vaciar() {
    limpiar();
  }

  @Test
  @DisplayName("`V35` — `products.video_url` ya no existe: el enlace vive en su tabla")
  void laColumnaSeFue() {
    assertThat(cuantasColumnas("products", "video_url"))
        .as("la columna debía irse con V35, no quedarse en paralelo")
        .isZero();
    assertThat(cuantasColumnas("product_links", "url")).isOne();
    assertThat(cuantasColumnas("product_links", "external_id")).isOne();
    // Sin `deleted_at`: quitar un enlace lo BORRA (`RN-PM-048`). Una columna
    // de borrado lógico aquí obligaría a filtrarla en las seis lecturas.
    assertThat(cuantasColumnas("product_links", "deleted_at")).isZero();
  }

  @Test
  @DisplayName("`RN-PM-048` — la clave es la pareja: dos enlaces del mismo tipo no caben")
  void unoPorTipo() {
    enlace(producto, "VIDEO_PRESENTACION", "https://vimeo.com/1", null);

    assertThatThrownBy(() -> enlace(producto, "VIDEO_PRESENTACION", "https://vimeo.com/2", null))
        .isInstanceOf(DataIntegrityViolationException.class);

    // Y el OTRO tipo sí: el único es por pareja, no por producto.
    assertThatCode(() -> enlace(producto, "CUPON_BOT", "https://t.me/bot", null))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("`ck_product_links_type` — un tipo fuera del dominio no entra")
  void elTipoEstaEnUnCheck() {
    assertThatThrownBy(() -> enlace(producto, "MANUAL_PDF", "https://example.com/m.pdf", null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("`ck_product_links_url_format` — la dirección es obligatoria y con forma")
  void laDireccionTieneForma() {
    for (String malo : new String[] {"/relativa", "ftp://x.com/v", "https://con espacio"}) {
      assertThatThrownBy(() -> enlace(producto, "VIDEO_PRESENTACION", malo, null))
          .as("debía rechazar «%s»", malo)
          .isInstanceOf(DataIntegrityViolationException.class);
    }
    // NOT NULL, al revés que la columna que reemplaza: «no tener» es NO TENER
    // FILA, no tener una fila con la dirección vacía.
    assertThatThrownBy(() -> enlace(producto, "VIDEO_PRESENTACION", null, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("`ck_product_links_id_sin_consulta` — con identificador, la dirección no lleva `?`")
  void elCruzadoVivEnElEsquema() {
    assertThatThrownBy(
            () -> enlace(producto, "VIDEO_PRESENTACION", "https://youtube.com/w?v=a", "a"))
        .isInstanceOf(DataIntegrityViolationException.class);

    // La MISMA dirección sin identificador entra: es una restricción cruzada,
    // no una prohibición sobre la dirección — un video de YouTube es esto.
    assertThatCode(() -> enlace(producto, "VIDEO_PRESENTACION", "https://youtube.com/w?v=a", null))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("`fk_product_links_product` — sin producto no hay enlace, y el producto no se borra")
  void laClaveForaneaMuerde() {
    assertThatThrownBy(
            () -> enlace(UUID.randomUUID(), "VIDEO_PRESENTACION", "https://vimeo.com/1", null))
        .isInstanceOf(DataIntegrityViolationException.class);

    // Y sin `ON DELETE`: borrar el producto con su enlace vivo falla. Es
    // deliberado —el producto no se borra físicamente nunca (`RN-PM-010`)— y
    // es lo que obliga a las suites a limpiar los enlaces primero.
    enlace(producto, "VIDEO_PRESENTACION", "https://vimeo.com/1", null);
    assertThatThrownBy(() -> jdbc.update("DELETE FROM products WHERE id = ?", producto))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // ---------------------------------------------------------------------------
  // Auxiliares
  // ---------------------------------------------------------------------------

  private int cuantasColumnas(String tabla, String columna) {
    Integer filas =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM information_schema.columns
             WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
            """,
            Integer.class,
            tabla,
            columna);
    return filas == null ? 0 : filas;
  }

  private void enlace(UUID producto, String tipo, String url, String externalId) {
    jdbc.update(
        "INSERT INTO product_links (product_id, type, url, external_id) VALUES (?, ?, ?, ?)",
        producto,
        tipo,
        url,
        externalId);
  }

  private UUID producto(String codigo) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products (scope, implementation, id, code, type, name, description,"
            + " source_membership_id, target_membership_id, price, currency_id, validity_days,"
            + " status) VALUES ('TIENDA', 'AUTOMATICA', ?, ?, 'BOT', 'Bot de esquema',"
            + " 'Producto de prueba', NULL, NULL, 100.00, CAST(? AS uuid), NULL, 'ACTIVO')",
        id,
        codigo,
        USD);
    return id;
  }

  private void limpiar() {
    jdbc.update(
        "DELETE FROM product_links WHERE product_id IN (SELECT id FROM products WHERE code LIKE 'PL\\_%')");
    jdbc.update("DELETE FROM products WHERE code LIKE 'PL\\_%'");
  }
}
