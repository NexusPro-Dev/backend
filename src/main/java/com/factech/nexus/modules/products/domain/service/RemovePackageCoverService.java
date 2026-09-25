package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.PackageDetailResponse;
import com.factech.nexus.modules.products.domain.models.ProductPackage;
import com.factech.nexus.modules.products.domain.repository.ProductImageRepository;
import com.factech.nexus.modules.products.domain.repository.ProductPackageRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.images.CambioDePortada;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RF-PM-029` — quitar la portada de un paquete, <b>y nunca decir que no</b>.
 *
 * <p>Es {@link RemoveProductCoverService} sin la regla: {@link ProductPackage#quitarPortada} no
 * comprueba nada porque el paquete no declara icono ni color (`RN-PM-045`) — sin portada, el
 * frontend le pone el icono de promoción y el color por omisión, de modo que no puede quedarse sin
 * nada con qué pintarse. Lo que sí conserva es el atajo: sin portada, el agregado devuelve un diff
 * vacío y aquí no se escribe nada, ni auditoría ni {@code updated_at}.
 *
 * <p><b>El {@code UPDATE} se vuelca antes del {@code DELETE}</b>, con {@code flush()} explícito,
 * por lo mismo que en el producto.
 */
@Service
public class RemovePackageCoverService {

  private final ProductPackageRepository paquetes;
  private final ProductImageRepository imagenes;
  private final PackageDetailReader detalle;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public RemovePackageCoverService(
      ProductPackageRepository paquetes,
      ProductImageRepository imagenes,
      PackageDetailReader detalle,
      AuditWriter auditoria) {
    this(paquetes, imagenes, detalle, auditoria, Clock.systemUTC());
  }

  RemovePackageCoverService(
      ProductPackageRepository paquetes,
      ProductImageRepository imagenes,
      PackageDetailReader detalle,
      AuditWriter auditoria,
      Clock reloj) {
    this.paquetes = paquetes;
    this.imagenes = imagenes;
    this.detalle = detalle;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public PackageDetailResponse remove(UUID id) {
    ProductPackage paquete = AssociatePackageProductService.paqueteVivo(paquetes, id);

    CambioDePortada cambio = paquete.quitarPortada(OffsetDateTime.now(reloj));
    if (cambio.huboCambio()) {
      paquetes.flush();
      imagenes.deleteById(cambio.anterior());
      auditoria.recordChange(
          new ChangeEvent(
              PackageDetailReader.MODULO,
              PackageDetailReader.ENTIDAD,
              paquete.getId(),
              ChangeAction.UPDATE,
              cambio.cambios()));
    }

    return detalle.leer(paquete.getId());
  }
}
