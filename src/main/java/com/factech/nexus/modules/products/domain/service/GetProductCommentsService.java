package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.ProductCommentPublicItem;
import com.factech.nexus.modules.products.domain.repository.ProductCommentQueryRepository;
import com.factech.nexus.modules.products.domain.repository.ProductQueryRepository;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de `RF-PM-012`: las reseñas de un producto, sin autenticación.
 *
 * <h2>Un solo punto de salida para el {@code 404}</h2>
 *
 * <p>Producto inexistente, inactivo, retirado — y <b>el identificador malformado</b>— responden el
 * mismo código y el mismo mensaje. Un anónimo con un identificador no debe poder saber si
 * corresponde a un producto en preparación, y en una ruta pública la forma del identificador
 * también es información (`spec.md` §10, §11). Por eso el identificador llega aquí como texto y se
 * convierte dentro, y no por el convertidor de {@code shared/error}, que respondería {@code 400}.
 *
 * <h2>Tres sentencias, y una cuando el producto no procede</h2>
 *
 * <p>El producto, la página y el total. El nombre del autor viene <b>en la sentencia de la
 * página</b>, por un {@code JOIN} a {@code users}: el número no crece con el tamaño de la página
 * (`CA-PM-209`). Y quien recorre identificadores al azar cuesta una.
 */
@Service
public class GetProductCommentsService {

  private final ProductCommentQueryRepository resenas;
  private final ProductQueryRepository productos;
  private final Pagination paginacion;

  public GetProductCommentsService(
      ProductCommentQueryRepository resenas,
      ProductQueryRepository productos,
      Pagination paginacion) {
    this.resenas = resenas;
    this.productos = productos;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public PageResponse<ProductCommentPublicItem> list(String productId, Integer page, Integer size) {
    // La paginación primero: su `400` no es información sobre nadie.
    Pagination.Slice trozo = paginacion.resolver(page, size);

    UUID producto = comoUuid(productId).orElseThrow(GetProductCommentsService::noExiste);
    if (!productos.isPurchasable(producto)) {
      throw noExiste();
    }

    return PageResponse.de(
        resenas.findPageByProduct(producto, trozo.offset(), trozo.size()).stream()
            .map(ProductCommentPublicItem::from)
            .toList(),
        resenas.countLiveByProduct(producto),
        trozo.page(),
        trozo.size());
  }

  /** El {@code 404} único: el mismo texto que el alta usa para el producto (`RN-PM-028`). */
  private static ResourceNotFoundException noExiste() {
    return CreateProductCommentService.noComprable();
  }

  private static java.util.Optional<UUID> comoUuid(String valor) {
    if (valor == null || valor.length() != 36) {
      return java.util.Optional.empty();
    }
    try {
      return java.util.Optional.of(UUID.fromString(valor));
    } catch (IllegalArgumentException malformado) {
      return java.util.Optional.empty();
    }
  }
}
