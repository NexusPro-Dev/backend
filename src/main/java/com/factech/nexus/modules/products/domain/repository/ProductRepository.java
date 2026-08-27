package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.domain.models.Product;
import java.util.Optional;
import java.util.UUID;

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

  /**
   * El producto <b>vivo</b>, bloqueado para escritura (`RF-PM-005`).
   *
   * <p><b>{@code PESSIMISTIC_WRITE} y no una versión optimista</b>: la operación lee, decide y
   * escribe, y el rechazo de dos peticiones sobre <b>el mismo</b> producto debe llegar como espera
   * y no como un {@code 409} que el actor no provocó. Lo que este bloqueo <b>no</b> serializa son
   * dos productos <b>distintos</b> compitiendo por el mismo destino — eso lo decide {@code
   * uq_products_upgrade_target}, y confundir las dos cosas es el defecto que `RN-SP-018` costó en
   * `SP`.
   *
   * <p>Excluye los retirados: un producto retirado no vuelve a la venta cambiándole el estado
   * (`EX-001`).
   */
  Optional<Product> findAliveByIdForUpdate(UUID id);

  /**
   * El producto <b>en cualquier estado</b>, bloqueado para escritura (`RF-PM-006`).
   *
   * <p>Al contrario que {@link #findAliveByIdForUpdate}, <b>incluye los retirados</b>, y esa es la
   * diferencia que permite distinguir «no existe» de «ya está retirado». `RF-PM-005` no necesita
   * distinguirlos —su `EX-001` cubre los dos casos con la misma respuesta— y el retiro sí: quien
   * intenta retirar dos veces merece saber que su primera petición ya funcionó.
   *
   * <p><b>Aquí no hay nada que ocultar</b>, al revés que al eliminar una persona: el catálogo
   * <b>devuelve</b> los productos retirados a cualquiera con {@code products:read} (`CA-PM-018`),
   * de modo que responder «ya estaba retirado» no revela nada que el actor no pueda consultar.
   */
  Optional<Product> findByIdForUpdate(UUID id);

  /**
   * El upgrade <b>activo</b> que ocupa ese destino, si lo hay (`RN-PM-004`).
   *
   * <p>Existe para <b>redactar</b> el rechazo —nombrar cuál desactivar, que es lo único
   * accionable—, no para garantizarlo: entre esta lectura y la escritura cabe otra transacción. La
   * garantía la da el índice único parcial.
   *
   * @param excluido el producto que se está activando, que no debe contarse a sí mismo
   */
  Optional<Product> findActiveUpgradeFor(UUID targetMembershipId, UUID excluido);

  /**
   * Vuelca los cambios pendientes y traduce lo que el índice rechace.
   *
   * <p><b>Sin este volcado explícito la violación saltaría al confirmar</b>, fuera del caso de uso,
   * y llegaría al manejador global como un fallo no controlado — un {@code 500} donde corresponde
   * un {@code 409}. Hace falta aquí y no en {@code save} porque en una modificación no hay {@code
   * persist} que dispare nada: el cambio vive en la entidad gestionada hasta el {@code commit}.
   */
  void flush();
}
