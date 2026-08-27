package com.factech.nexus.modules.products.application;

import com.factech.nexus.shared.pagination.PageResponse;
import java.util.List;

/**
 * La página del catálogo, que es {@link PageResponse} <b>más el orden aplicado</b> (`RF-PM-002`).
 *
 * <p>`spec.md` §6.2 pide devolver el orden aplicado «para que quien recibe la página sepa sobre qué
 * está paginando». No es un adorno: el orden por omisión no es el único posible y una página
 * ordenada por precio no significa lo mismo que una ordenada por fecha, de modo que sin este campo
 * el cliente que no envió {@code sort} no puede saber sobre qué se le está paginando.
 *
 * <p><b>Es un superconjunto y no otra forma</b>, y esa es la línea que no se cruza: los seis campos
 * de {@link PageResponse} están aquí con su mismo nombre y su mismo significado, de modo que quien
 * lea la forma uniforme del sistema lee esta igual. Añadir el campo a {@link PageResponse} habría
 * obligado a todos los listados de `SP` a declarar un orden que la mitad no tiene.
 *
 * <p><b>{@code sort} es el nombre público del campo, no la columna.</b> Devolver {@code
 * p.created_at DESC, p.id DESC} filtraría el esquema al contrato y ataría la respuesta al SQL.
 */
public record ProductPageResponse(
    List<ProductItem> content,
    long totalElements,
    int totalPages,
    int page,
    int size,
    boolean totalIsExact,
    String sort) {

  /**
   * Envuelve una página ya construida.
   *
   * <p>Se construye <b>desde</b> {@link PageResponse} y no en paralelo: el cálculo de {@code
   * totalPages} vive en un solo sitio, y dos cálculos del mismo número acaban divergiendo en el
   * borde —la última página incompleta— que es donde nadie mira.
   */
  public static ProductPageResponse de(PageResponse<ProductItem> pagina, String orden) {
    return new ProductPageResponse(
        pagina.content(),
        pagina.totalElements(),
        pagina.totalPages(),
        pagina.page(),
        pagina.size(),
        pagina.totalIsExact(),
        orden);
  }
}
