package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.DeletePackageRequest;
import com.factech.nexus.modules.products.domain.models.DeletionReason;
import com.factech.nexus.modules.products.domain.models.PackageItem;
import com.factech.nexus.modules.products.domain.models.ProductPackage;
import com.factech.nexus.modules.products.domain.repository.PackageItemRepository;
import com.factech.nexus.modules.products.domain.repository.ProductPackageRepository;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-PM-022`: retirar un paquete.
 *
 * <p>El retiro del producto (`RF-PM-006`) con otra tabla: motivo <b>antes de cualquier
 * consulta</b>, el paquete <b>en cualquier estado</b> y bloqueado para distinguir «no existe» de
 * «ya está retirado», la instantánea antes de marcar —<b>con sus filas</b>, que es lo que el
 * registro tiene que poder decir que tenía dentro—, y la marca sin tocar {@code status}. <b>Las
 * filas de asociación permanecen</b>: son catálogo, y el detalle administrativo las sigue
 * enseñando.
 */
@Service
public class DeletePackageService {

  private final ProductPackageRepository paquetes;
  private final PackageItemRepository filas;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public DeletePackageService(
      ProductPackageRepository paquetes, PackageItemRepository filas, AuditWriter auditoria) {
    this(paquetes, filas, auditoria, Clock.systemUTC());
  }

  DeletePackageService(
      ProductPackageRepository paquetes,
      PackageItemRepository filas,
      AuditWriter auditoria,
      Clock reloj) {
    this.paquetes = paquetes;
    this.filas = filas;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public void delete(UUID id, DeletePackageRequest peticion) {
    DeletionReason motivo = new DeletionReason(peticion == null ? null : peticion.reason());

    ProductPackage paquete =
        paquetes
            .findByIdForUpdate(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un paquete con ese identificador."));
    // `EX-002`, y se distingue de `EX-001` a propósito: el catálogo devuelve los
    // retirados a quien tiene `packages:read`, y quien retira dos veces merece
    // saber que la primera funcionó.
    if (paquete.estaRetirado()) {
      String mensaje = "El paquete ya está retirado.";
      throw new BusinessRuleException(
          "EX-002", mensaje, List.of(new FieldError("id", "EX-002", mensaje)));
    }

    Map<String, Object> instantanea = new LinkedHashMap<>(paquete.instantanea());
    instantanea.put(
        "items",
        filas.findByPackage(paquete.getId()).stream().map(DeletePackageService::fila).toList());

    paquete.delete(OffsetDateTime.now(reloj));

    auditoria.recordDeletion(
        new DeletionEvent(
            PackageDetailReader.MODULO,
            PackageDetailReader.ENTIDAD,
            paquete.getId(),
            DeletionType.LOGICAL,
            motivo.value(),
            instantanea));
  }

  private static Map<String, Object> fila(PackageItem fila) {
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("product_id", fila.getProductId().toString());
    estado.put("discount_type", fila.getDiscount().getType().name());
    estado.put("discount_value", fila.getDiscount().getValue().toPlainString());
    return estado;
  }
}
