package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.ChangePackageStatusRequest;
import com.factech.nexus.modules.products.application.PackageDetailResponse;
import com.factech.nexus.modules.products.domain.models.PackageStatus;
import com.factech.nexus.modules.products.domain.models.ProductPackage;
import com.factech.nexus.modules.products.domain.repository.PackageItemRepository;
import com.factech.nexus.modules.products.domain.repository.ProductPackageRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-PM-021`: activar o desactivar un paquete.
 *
 * <p><b>Activar mira lo que es del paquete y no lo que es de sus productos</b> (`RN-PM-040`):
 * descripción y cuántos son suyos, y se comprueban <b>juntos</b>; que los productos estén activos
 * hoy es de cada producto, cambia sin que el paquete se entere, y por eso lo mira la oferta cada
 * vez (`RN-PM-039`). Un paquete activo con un producto inactivo dentro es un estado legítimo: el
 * detalle lo nombra (`CA-PM-294`).
 */
@Service
public class ChangePackageStatusService {

  private final ProductPackageRepository paquetes;
  private final PackageItemRepository filas;
  private final AuditWriter auditoria;
  private final PackageDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public ChangePackageStatusService(
      ProductPackageRepository paquetes,
      PackageItemRepository filas,
      AuditWriter auditoria,
      PackageDetailReader detalle) {
    this(paquetes, filas, auditoria, detalle, Clock.systemUTC());
  }

  ChangePackageStatusService(
      ProductPackageRepository paquetes,
      PackageItemRepository filas,
      AuditWriter auditoria,
      PackageDetailReader detalle,
      Clock reloj) {
    this.paquetes = paquetes;
    this.filas = filas;
    this.auditoria = auditoria;
    this.detalle = detalle;
    this.reloj = reloj;
  }

  @Transactional
  public PackageDetailResponse change(UUID id, ChangePackageStatusRequest peticion) {
    PackageStatus destino = resolver(peticion.status());
    ProductPackage paquete = AssociatePackageProductService.paqueteVivo(paquetes, id);
    PackageStatus anterior = paquete.getStatus();

    // Pedir el estado que ya tiene no es un error: `200` sin escribir ni auditar.
    if (anterior == destino) {
      return detalle.leer(paquete.getId());
    }
    if (destino == PackageStatus.ACTIVO) {
      verificarQuePuedePublicarse(paquete);
    }
    // Desactivar no comprueba nada (`FA-002`).

    if (destino == PackageStatus.ACTIVO) {
      paquete.activate(OffsetDateTime.now(reloj));
    } else {
      paquete.deactivate(OffsetDateTime.now(reloj));
    }
    paquetes.flush();
    auditoria.recordChange(
        new ChangeEvent(
            PackageDetailReader.MODULO,
            PackageDetailReader.ENTIDAD,
            paquete.getId(),
            ChangeAction.UPDATE,
            Map.of("status", Map.of("before", anterior.name(), "after", destino.name()))));
    return detalle.leer(paquete.getId());
  }

  /**
   * `EX-002` y `EX-003` <b>juntos</b> (`CA-PM-293`): si faltan las dos cosas, lo dice de una vez,
   * como el alta devuelve juntas sus validaciones.
   */
  private void verificarQuePuedePublicarse(ProductPackage paquete) {
    List<FieldError> motivos = new ArrayList<>();
    if (!paquete.tieneDescripcion()) {
      motivos.add(
          new FieldError(
              "description",
              "EX-002",
              "El paquete no tiene descripción: no se publica lo que no se explica."));
    }
    int cuantos = filas.findSiblings(paquete.getId()).size();
    if (cuantos < 2) {
      motivos.add(
          new FieldError(
              "items",
              "EX-003",
              ("El paquete tiene %d productos y necesita al menos dos: uno solo con descuento es"
                      + " una promoción, no un paquete.")
                  .formatted(cuantos)));
    }
    if (!motivos.isEmpty()) {
      throw new BusinessRuleException(
          motivos.get(0).code(),
          motivos.size() == 1
              ? motivos.get(0).message()
              : "El paquete no se puede activar todavía: sin descripción y con menos de dos productos.",
          motivos);
    }
  }

  private static PackageStatus resolver(String valor) {
    return Arrays.stream(PackageStatus.values())
        .filter(estado -> estado.name().equalsIgnoreCase(valor == null ? "" : valor.trim()))
        .findFirst()
        .orElseThrow(
            () -> {
              String mensaje = "El estado es obligatorio y debe ser ACTIVO o INACTIVO.";
              return new ValidationException(
                  "VAL-002", mensaje, List.of(new FieldError("status", "VAL-002", mensaje)));
            });
  }
}
