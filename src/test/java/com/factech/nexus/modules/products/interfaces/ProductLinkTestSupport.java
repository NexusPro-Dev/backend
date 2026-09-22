package com.factech.nexus.modules.products.interfaces;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Siembra y limpia `product_links` en las pruebas (`RF-PM-001` · `T-42`).
 *
 * <p><b>Existe por la clave foránea.</b> {@code fk_product_links_product} no lleva {@code ON
 * DELETE} —el producto no se borra físicamente nunca (`RN-PM-010`)—, de modo que cualquier suite
 * que siembre un enlace y termine con {@code DELETE FROM products} falla; y falla <b>lejos de donde
 * está el error</b>, en la primera clase que vacíe el catálogo después. {@link
 * #limpiar(JdbcTemplate)} es la línea que hay que poner ANTES de ese borrado, y está aquí para que
 * se escriba igual en las siete suites que la necesitan.
 *
 * <p>Es público porque `MV` también lo usa: el cupón del bot se lee desde `RF-MV-014`, y sembrarlo
 * con un {@code INSERT} escrito a mano allí duplicaría la forma de la tabla en otro módulo.
 */
public final class ProductLinkTestSupport {

  private ProductLinkTestSupport() {}

  /** Declara un enlace de un producto, por su código. */
  public static void enlace(
      JdbcTemplate jdbc, String codigoDeProducto, String tipo, String url, String externalId) {
    jdbc.update(
        """
        INSERT INTO product_links (product_id, type, url, external_id)
        SELECT id, ?, ?, ? FROM products WHERE code = ?
        """,
        tipo,
        url,
        externalId,
        codigoDeProducto);
  }

  /** Lo mismo, cuando la prueba tiene el identificador y no el código. */
  public static void enlace(
      JdbcTemplate jdbc, UUID productoId, String tipo, String url, String externalId) {
    jdbc.update(
        """
        INSERT INTO product_links (product_id, type, url, external_id)
        VALUES (CAST(? AS uuid), ?, ?, ?)
        """,
        productoId,
        tipo,
        url,
        externalId);
  }

  /** Deja `product_links` vacía. Va SIEMPRE antes de borrar productos. */
  public static void limpiar(JdbcTemplate jdbc) {
    jdbc.update("DELETE FROM product_links");
  }
}
