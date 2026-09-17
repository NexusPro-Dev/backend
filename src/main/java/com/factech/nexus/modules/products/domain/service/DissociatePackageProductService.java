package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.PackageDetailResponse;
import com.factech.nexus.modules.products.domain.models.PackageItem;
import com.factech.nexus.modules.products.domain.models.ProductPackage;
import com.factech.nexus.modules.products.domain.repository.PackageItemRepository;
import com.factech.nexus.modules.products.domain.repository.ProductPackageRepository;
import com.factech.nexus.modules.products.domain.repository.ProductRepository;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-PM-025`: quitar un producto de un paquete.
 *
 * <p><b>Sin motivo, borrado físico, {@code ASSOCIATION}</b> (`RN-PM-042`, Art. V.13): la fila es
 * una asociación, y el registro de eliminación guarda el descuento y el precio del producto en la
 * instantánea para que la auditoría diga qué rebaja tenía cuando salió. <b>Responde el paquete</b>
 * y no {@code 204}, porque lo que cambió es su precio. El paquete queda en su estado aunque quede
 * con uno o con cero (`FA-001`): lo saca de la oferta `RN-PM-040`, y el detalle lo dice.
 */
@Service
public class DissociatePackageProductService {

  private final ProductPackageRepository paquetes;
  private final PackageItemRepository filas;
  private final ProductRepository productos;
  private final AuditWriter auditoria;
  private final PackageDetailReader detalle;

  public DissociatePackageProductService(
      ProductPackageRepository paquetes,
      PackageItemRepository filas,
      ProductRepository productos,
      AuditWriter auditoria,
      PackageDetailReader detalle) {
    this.paquetes = paquetes;
    this.filas = filas;
    this.productos = productos;
    this.auditoria = auditoria;
    this.detalle = detalle;
  }

  @Transactional
  public PackageDetailResponse dissociate(UUID packageId, UUID productId) {
    ProductPackage paquete = AssociatePackageProductService.paqueteVivo(paquetes, packageId);
    PackageItem fila = CorrectPackageDiscountService.filaDe(filas, paquete.getId(), productId);

    // La instantánea ANTES de borrar, con el precio de hoy del producto: después
    // no queda nada que capturar. Nulo si el producto no existiera, que no
    // ocurre con la clave foránea puesta.
    BigDecimal precio = productos.findById(productId).map(p -> p.getPrice()).orElse(null);
    Map<String, Object> instantanea = fila.instantanea(precio);

    filas.delete(fila);

    // `ASSOCIATION` admite motivo nulo (`ck_deletion_reason`): la tercera clase
    // de eliminación existe exactamente para esto.
    auditoria.recordDeletion(
        new DeletionEvent(
            PackageDetailReader.MODULO,
            AssociatePackageProductService.ENTIDAD_FILA,
            paquete.getId(),
            DeletionType.ASSOCIATION,
            null,
            instantanea));

    return detalle.leer(paquete.getId());
  }
}
