package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.PackageDetailResponse;
import com.factech.nexus.modules.products.domain.models.CambioDePortada;
import com.factech.nexus.modules.products.domain.models.ProductImage;
import com.factech.nexus.modules.products.domain.models.ProductPackage;
import com.factech.nexus.modules.products.domain.repository.ProductImageRepository;
import com.factech.nexus.modules.products.domain.repository.ProductPackageRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RF-PM-028` — subir o reemplazar la portada de un paquete.
 *
 * <p><b>{@link UploadProductCoverService} con el paquete, y nada nuevo sobre imágenes.</b> El
 * archivo lo comprueba el mismo {@link ProductImage}, los bytes van a la misma tabla por el mismo
 * repositorio, y la dirección la sirve la misma ruta pública: esta operación no añade ningún
 * conocimiento; añade el hueco (`V11`) y quién lo llena. Es una copia legible y no una abstracción
 * sobre dos entidades (`plan.md` §9).
 *
 * <p><b>El archivo se comprueba antes de tocar la base</b>, y <b>el orden de las escrituras no es
 * negociable</b> —insertar la nueva, repuntar, <b>volcar</b>, borrar la anterior—, por lo que
 * explica el servicio del producto: {@code fk_product_packages_cover_image} no tiene {@code ON
 * DELETE}.
 *
 * <p><b>Sin condición de estado ni de contenido</b>: un paquete inactivo, vacío o sin descripción
 * la admite igual. Y a diferencia del producto, <b>tampoco la tiene el retiro</b>: el paquete no
 * declara icono ni color (`RN-PM-045`).
 */
@Service
public class UploadPackageCoverService {

  private final ProductPackageRepository paquetes;
  private final ProductImageRepository imagenes;
  private final PackageDetailReader detalle;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  @Autowired
  public UploadPackageCoverService(
      ProductPackageRepository paquetes,
      ProductImageRepository imagenes,
      PackageDetailReader detalle,
      AuditWriter auditoria,
      UuidV7Generator ids) {
    this(paquetes, imagenes, detalle, auditoria, ids, Clock.systemUTC());
  }

  UploadPackageCoverService(
      ProductPackageRepository paquetes,
      ProductImageRepository imagenes,
      PackageDetailReader detalle,
      AuditWriter auditoria,
      UuidV7Generator ids,
      Clock reloj) {
    this.paquetes = paquetes;
    this.imagenes = imagenes;
    this.detalle = detalle;
    this.auditoria = auditoria;
    this.ids = ids;
    this.reloj = reloj;
  }

  /**
   * @param bytes el archivo tal cual llegó; nulo o vacío es `VAL-002`
   */
  @Transactional
  public PackageDetailResponse upload(UUID id, byte[] bytes) {
    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    // Primero el archivo, y sin base de por medio: los tres `VAL` salen de aquí.
    ProductImage nueva = ProductImage.de(ids.next(), bytes, ahora);

    ProductPackage paquete = AssociatePackageProductService.paqueteVivo(paquetes, id);

    imagenes.save(nueva);
    CambioDePortada cambio = paquete.asignarPortada(nueva.getId(), ahora);
    // El UPDATE llega a la base antes que el DELETE, o la clave foránea muerde.
    paquetes.flush();
    if (cambio.anterior() != null) {
      imagenes.deleteById(cambio.anterior());
    }

    // Siempre hay cambio: cada subida estrena identificador.
    auditoria.recordChange(
        new ChangeEvent(
            PackageDetailReader.MODULO,
            PackageDetailReader.ENTIDAD,
            paquete.getId(),
            ChangeAction.UPDATE,
            cambio.cambios()));

    return detalle.leer(paquete.getId());
  }
}
