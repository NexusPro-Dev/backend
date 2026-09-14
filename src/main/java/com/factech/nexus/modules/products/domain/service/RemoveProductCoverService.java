package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.ProductDetailResponse;
import com.factech.nexus.modules.products.domain.models.Product;
import com.factech.nexus.modules.products.domain.models.Product.CambioDePortada;
import com.factech.nexus.modules.products.domain.repository.ProductImageRepository;
import com.factech.nexus.modules.products.domain.repository.ProductQueryRepository;
import com.factech.nexus.modules.products.domain.repository.ProductRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RF-PM-015` — quitar la portada de un producto.
 *
 * <p><b>La subida al revés, con la regla delante y un atajo antes de la regla.</b> Quién decide es
 * el agregado: {@link Product#quitarPortada} devuelve un diff vacío si no había portada —y entonces
 * aquí no se escribe nada, ni auditoría ni {@code updated_at}—, y lanza `VAL-002` si es un upgrade
 * sin icono (`RN-PM-034`). Este servicio solo ordena los pasos y borra los bytes.
 *
 * <p><b>El {@code UPDATE} se vuelca antes del {@code DELETE}</b>, con {@code flush()} explícito:
 * Hibernate ya ordena los borrados al final del volcado, pero el orden correcto no debe depender de
 * una regla de Hibernate que nadie va a recordar (`plan.md` §7).
 */
@Service
public class RemoveProductCoverService {

  private final ProductRepository productos;
  private final ProductImageRepository imagenes;
  private final ProductQueryRepository consultas;
  private final AuditWriter auditoria;
  private final ProductExchangeResolver conversiones;
  private final Clock reloj;

  @Autowired
  public RemoveProductCoverService(
      ProductRepository productos,
      ProductImageRepository imagenes,
      ProductQueryRepository consultas,
      AuditWriter auditoria,
      ProductExchangeResolver conversiones) {
    this(productos, imagenes, consultas, auditoria, conversiones, Clock.systemUTC());
  }

  RemoveProductCoverService(
      ProductRepository productos,
      ProductImageRepository imagenes,
      ProductQueryRepository consultas,
      AuditWriter auditoria,
      ProductExchangeResolver conversiones,
      Clock reloj) {
    this.productos = productos;
    this.imagenes = imagenes;
    this.consultas = consultas;
    this.auditoria = auditoria;
    this.conversiones = conversiones;
    this.reloj = reloj;
  }

  @Transactional
  public ProductDetailResponse remove(UUID id) {
    Product producto =
        productos.findAliveByIdForUpdate(id).orElseThrow(ProductCoverSupport::productoNoVivo);

    CambioDePortada cambio = producto.quitarPortada(OffsetDateTime.now(reloj));
    if (cambio.huboCambio()) {
      productos.flush();
      imagenes.deleteById(cambio.anterior());
      auditoria.recordChange(
          new ChangeEvent(
              ProductCoverSupport.MODULO,
              ProductCoverSupport.ENTIDAD,
              producto.getId(),
              ChangeAction.UPDATE,
              cambio.cambios()));
    }

    return ProductCoverSupport.detalleDe(producto.getId(), consultas, conversiones);
  }
}
