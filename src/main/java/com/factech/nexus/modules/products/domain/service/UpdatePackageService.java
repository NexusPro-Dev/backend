package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.PackageDetailResponse;
import com.factech.nexus.modules.products.application.UpdatePackageRequest;
import com.factech.nexus.modules.products.domain.models.ProductPackage;
import com.factech.nexus.modules.products.domain.repository.ProductPackageRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-PM-020`: corregir nombre, descripción y alcance de un paquete.
 *
 * <p><b>Código y moneda se rechazan, no se ignoran</b> (`EX-003`), antes de cualquier consulta:
 * ignorarlos haría creer que el cambio se aplicó. La moneda es inmutable porque es la unidad en la
 * que se suma. <b>Vaciar la descripción de un paquete activo se permite</b> (`FA-001`): no lo
 * desactiva, lo saca de la oferta hasta que vuelva a tenerla, y el detalle lo dice.
 */
@Service
public class UpdatePackageService {

  private final ProductPackageRepository paquetes;
  private final AuditWriter auditoria;
  private final PackageDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public UpdatePackageService(
      ProductPackageRepository paquetes, AuditWriter auditoria, PackageDetailReader detalle) {
    this(paquetes, auditoria, detalle, Clock.systemUTC());
  }

  UpdatePackageService(
      ProductPackageRepository paquetes,
      AuditWriter auditoria,
      PackageDetailReader detalle,
      Clock reloj) {
    this.paquetes = paquetes;
    this.auditoria = auditoria;
    this.detalle = detalle;
    this.reloj = reloj;
  }

  @Transactional
  public PackageDetailResponse update(UUID id, UpdatePackageRequest peticion) {
    if (peticion.traeInmutables()) {
      String mensaje = "El código y la moneda del paquete no se pueden modificar.";
      throw new ValidationException(
          "EX-003",
          mensaje,
          List.of(
              new FieldError(
                  peticion.code().presente() ? "code" : "currencyId", "VAL-004", mensaje)));
    }
    if (!peticion.informaAlgo()) {
      String mensaje = "Debe informar al menos uno de los campos corregibles.";
      throw new ValidationException(
          "VAL-005", mensaje, List.of(new FieldError("body", "VAL-005", mensaje)));
    }

    ProductPackage paquete = AssociatePackageProductService.paqueteVivo(paquetes, id);

    verificarFormato(peticion);
    if (peticion.name().presente()) {
      String nombre = peticion.name().valor().trim();
      if (paquetes.existsAliveNameForOther(nombre, paquete.getId())) {
        String mensaje = "Ya existe un paquete con ese nombre.";
        throw new BusinessRuleException(
            "EX-002", mensaje, List.of(new FieldError("name", "EX-002", mensaje)));
      }
    }

    Map<String, Object> cambios =
        paquete.update(
            peticion.name(),
            peticion.description(),
            peticion.scope(),
            peticion.validFrom(),
            peticion.validTo(),
            OffsetDateTime.now(reloj));

    if (!cambios.isEmpty()) {
      // El volcado explícito convierte una carrera sobre el nombre en el `409`
      // traducido, y no en un fallo al confirmar fuera de este método.
      paquetes.flush();
      auditoria.recordChange(
          new ChangeEvent(
              PackageDetailReader.MODULO,
              PackageDetailReader.ENTIDAD,
              paquete.getId(),
              ChangeAction.UPDATE,
              cambios));
    }
    return detalle.leer(paquete.getId());
  }

  /**
   * `VAL-002` y `VAL-003`: el nombre y el alcance <b>no admiten vaciarse</b> —son obligatorios en
   * la columna, y «bórralo» no tiene ningún estado al que llevar el paquete—; la descripción sí.
   */
  private static void verificarFormato(UpdatePackageRequest peticion) {
    if (peticion.name().presente()) {
      String nombre = peticion.name().valor();
      if (nombre == null || nombre.isBlank() || nombre.trim().length() > 150) {
        String mensaje =
            "El nombre del paquete no puede quedar vacío ni superar los 150 caracteres.";
        throw new ValidationException(
            "VAL-002", mensaje, List.of(new FieldError("name", "VAL-002", mensaje)));
      }
    }
    if (peticion.scope().presente() && peticion.scope().valor() == null) {
      String mensaje =
          "El alcance del paquete es obligatorio y debe ser TIENDA, HOTLINK, AMBOS o NINGUNO.";
      throw new ValidationException(
          "VAL-003", mensaje, List.of(new FieldError("scope", "VAL-003", mensaje)));
    }
    if (peticion.description().presente()
        && peticion.description().valor() != null
        && peticion.description().valor().trim().length() > 1000) {
      String mensaje = "La descripción no puede exceder 1000 caracteres.";
      throw new ValidationException(
          "VAL-002", mensaje, List.of(new FieldError("description", "VAL-002", mensaje)));
    }
  }
}
