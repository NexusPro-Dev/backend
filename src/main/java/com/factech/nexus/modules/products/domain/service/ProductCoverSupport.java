package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.ProductDetailResponse;
import com.factech.nexus.modules.products.domain.repository.ProductQueryRepository;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;

/**
 * Lo que las dos operaciones de la portada comparten y no merece dos copias: la respuesta.
 *
 * <p>Las dos responden <b>con el producto</b> (`RF-PM-014` §6.2, `RF-PM-015` §14.1) y en la misma
 * forma que la corrección: lo que cambió es un campo del producto, y el cliente repinta la ficha
 * con lo que vuelve. Es el mismo cierre que {@code UpdateProductService}, releído tras volcar.
 */
final class ProductCoverSupport {

  static final String MODULO = "PM";
  static final String ENTIDAD = "products";

  private ProductCoverSupport() {}

  static ProductDetailResponse detalleDe(
      UUID id,
      ProductQueryRepository consultas,
      ProductExchangeResolver conversiones,
      ProductLinkReader enlaces) {
    return consultas
        .findDetail(id)
        .map(
            fila ->
                ProductDetailResponse.from(
                    fila,
                    enlaces.crudosDe(fila.id()),
                    null,
                    conversiones
                        .para(List.of(fila.currencyId()))
                        .de(fila.currencyId(), fila.price())))
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "EX-001", "No existe un producto con ese identificador."));
  }

  static ResourceNotFoundException productoNoVivo() {
    return new ResourceNotFoundException(
        "EX-001", "No existe un producto vivo con ese identificador.");
  }
}
