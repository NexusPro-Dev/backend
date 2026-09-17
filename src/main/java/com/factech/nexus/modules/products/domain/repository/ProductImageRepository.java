package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.domain.models.ProductImage;
import java.util.Optional;
import java.util.UUID;

/**
 * Los bytes de las portadas (`RN-PM-033`).
 *
 * <p><b>Sin métodos de listado, a propósito.</b> Una imagen se guarda, se lee por su identificador
 * —solo para servirla, `RF-PM-016`— y se borra cuando deja de ser portada. Ninguna lectura del
 * catálogo pasa por aquí: el listado, el detalle, la oferta y el hotlink devuelven {@code
 * cover_image_id} convertido en una dirección, y no tocan esta tabla (`ProductImage`).
 */
public interface ProductImageRepository {

  ProductImage save(ProductImage imagen);

  /** La única lectura del sistema que carga {@code content}: la que sirve la imagen. */
  Optional<ProductImage> findById(UUID id);

  /**
   * Borra la imagen que dejó de ser portada.
   *
   * <p><b>Después</b> de que {@code products.cover_image_id} haya dejado de señalarla y de que ese
   * cambio se haya volcado: {@code fk_products_cover_image} no tiene {@code ON DELETE}, y borrar
   * una imagen todavía señalada es una violación de integridad.
   */
  void deleteById(UUID id);
}
