package com.factech.nexus.modules.products.application;

import java.util.UUID;

/**
 * La única función que sabe cómo se llama la ruta pública de una portada (`RF-PM-016`).
 *
 * <p><b>Es una ruta y no una URL absoluta</b>: el backend no sabe bajo qué dominio lo sirven, y el
 * cliente ya conoce la base con la que llama a todo lo demás. Y es <b>por imagen y no por
 * producto</b> —{@code /product-images/{imageId}}—, para que la dirección no cambie nunca de
 * contenido y se pueda cachear un año: reemplazar la portada es otra dirección
 * (`requirements/pm.md` §5.2.9).
 *
 * <p>Vive en un solo sitio para que las cinco formas de respuesta que la publican no puedan
 * divergir, y para que el día que cambie la ruta cambie aquí y en {@code ProductImageController}.
 */
public final class ProductImageUrls {

  public static final String RUTA = "/api/v1/product-images/";

  private ProductImageUrls() {}

  /**
   * La dirección de la portada, o nulo cuando el producto no tiene (presente y nulo en el JSON).
   */
  public static String de(UUID coverImageId) {
    return coverImageId == null ? null : RUTA + coverImageId;
  }
}
