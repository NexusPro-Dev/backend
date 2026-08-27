package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.domain.models.Product;

/**
 * Persistencia del catálogo (`RF-PM-001`).
 *
 * <p>Las dos comprobaciones de existencia sirven para <b>dar un mensaje preciso</b> —cuál de los
 * dos campos está duplicado—; la garantía la dan {@code uq_products_code} y {@code
 * uq_products_name}, y su violación la traduce el adaptador. La restricción decide, esto redacta.
 */
public interface ProductRepository {

  /**
   * ¿Hay ya un producto con ese código, <b>vivo o eliminado</b>? (`RN-PM-013`)
   *
   * <p>Incluye los retirados a propósito, al revés que el nombre: el código no se libera nunca,
   * porque el día que una factura diga {@code UPGRADE_ORO} tiene que resolver a un solo producto.
   */
  boolean existsCode(String code);

  /**
   * ¿Hay ya un producto <b>vivo</b> con ese nombre, sin distinguir mayúsculas ni acentos?
   * (`RN-PM-005`)
   *
   * <p>Compara sobre la forma normalizada, igual que {@code uq_products_name}. Si comparara el
   * texto literal mientras el índice compara la forma normalizada, el {@code 409} legible se
   * convertiría en un fallo de integridad justo en el caso que más importa: el nombre que solo
   * difiere en mayúsculas o acentos.
   */
  boolean existsAliveName(String name);

  /** Persiste el producto nuevo y fuerza el volcado para poder traducir el duplicado. */
  Product save(Product producto);
}
