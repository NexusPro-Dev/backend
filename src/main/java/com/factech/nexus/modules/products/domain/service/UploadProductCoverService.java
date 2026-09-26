package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.ProductDetailResponse;
import com.factech.nexus.modules.products.domain.models.Product;
import com.factech.nexus.modules.products.domain.models.ProductImage;
import com.factech.nexus.modules.products.domain.repository.ProductImageRepository;
import com.factech.nexus.modules.products.domain.repository.ProductQueryRepository;
import com.factech.nexus.modules.products.domain.repository.ProductRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.images.CambioDePortada;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RF-PM-014` — subir o reemplazar la portada de un producto.
 *
 * <p><b>La corrección de siempre con un solo campo y un archivo delante.</b> Los pasos son los de
 * {@code UpdateProductService}: encontrar el producto vivo bloqueando, cambiar {@code
 * cover_image_id}, registrar el diff en la misma transacción, devolver el detalle. Lo que no tiene
 * precedente —leer un archivo, saber qué es, guardar y borrar bytes— vive en un sitio cada uno: el
 * controlador, {@link ProductImage} y {@link ProductImageRepository}.
 *
 * <p><b>El archivo se comprueba antes de tocar la base</b> (`spec.md` §8): {@link ProductImage#de}
 * lanza sus tres rechazos sin haber bloqueado ninguna fila del catálogo, para que un cliente que
 * insiste con un archivo malo no le cueste nada al catálogo. El precio es que un archivo malo sobre
 * un producto inexistente recibe {@code 400} y no {@code 404}, y se acepta.
 *
 * <p><b>El orden de las escrituras no es negociable</b>: insertar la nueva, repuntar el producto,
 * <b>volcar</b>, y solo entonces borrar la anterior. {@code fk_products_cover_image} no tiene
 * {@code ON DELETE}: borrar una imagen todavía señalada es una violación de integridad, y borrarla
 * antes de que el {@code UPDATE} llegue a la base es lo mismo visto desde JPA.
 *
 * <p><b>Sin condición de tipo ni de estado</b>: subir una portada nunca deja al producto peor. Las
 * condiciones de `RN-PM-034` las tiene {@link RemoveProductCoverService}.
 */
@Service
public class UploadProductCoverService {

  private final ProductRepository productos;
  private final ProductImageRepository imagenes;
  private final ProductQueryRepository consultas;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final ProductExchangeResolver conversiones;
  private final ProductLinkReader enlaces;
  private final Clock reloj;

  @Autowired
  public UploadProductCoverService(
      ProductRepository productos,
      ProductImageRepository imagenes,
      ProductQueryRepository consultas,
      AuditWriter auditoria,
      UuidV7Generator ids,
      ProductExchangeResolver conversiones,
      ProductLinkReader enlaces) {
    this(productos, imagenes, consultas, auditoria, ids, conversiones, enlaces, Clock.systemUTC());
  }

  UploadProductCoverService(
      ProductRepository productos,
      ProductImageRepository imagenes,
      ProductQueryRepository consultas,
      AuditWriter auditoria,
      UuidV7Generator ids,
      ProductExchangeResolver conversiones,
      ProductLinkReader enlaces,
      Clock reloj) {
    this.productos = productos;
    this.imagenes = imagenes;
    this.consultas = consultas;
    this.auditoria = auditoria;
    this.ids = ids;
    this.conversiones = conversiones;
    this.enlaces = enlaces;
    this.reloj = reloj;
  }

  /**
   * @param bytes el archivo tal cual llegó; nulo o vacío es `VAL-002`
   */
  @Transactional
  public ProductDetailResponse upload(UUID id, byte[] bytes) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    // Primero el archivo, y sin base de por medio: los tres `VAL` salen de aquí.
    ProductImage nueva = ProductImage.de(ids.next(), bytes, ahora);

    Product producto =
        productos.findAliveByIdForUpdate(id).orElseThrow(ProductCoverSupport::productoNoVivo);

    imagenes.save(nueva);
    CambioDePortada cambio = producto.asignarPortada(nueva.getId(), ahora);
    // El UPDATE llega a la base antes que el DELETE, o la clave foránea muerde.
    productos.flush();
    if (cambio.anterior() != null) {
      imagenes.deleteById(cambio.anterior());
    }

    // Siempre hay cambio: cada subida estrena identificador (`FA-002`).
    auditoria.recordChange(
        new ChangeEvent(
            ProductCoverSupport.MODULO,
            ProductCoverSupport.ENTIDAD,
            producto.getId(),
            ChangeAction.UPDATE,
            cambio.cambios()));

    return ProductCoverSupport.detalleDe(producto.getId(), consultas, conversiones, enlaces);
  }
}
