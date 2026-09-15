package com.factech.nexus.modules.products.domain.service;

import com.factech.nexus.modules.products.application.PackageDetailResponse;
import com.factech.nexus.modules.products.application.RegisterPackageRequest;
import com.factech.nexus.modules.products.domain.models.ProductPackage;
import com.factech.nexus.modules.products.domain.repository.ProductPackageRepository;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog;
import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog.CurrencyView;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-PM-017`: registrar un paquete.
 *
 * <p><b>El alta del producto sin tipo, sin membresías y sin precio.</b> Código contra todos —vivos
 * y retirados—, nombre contra los vivos, moneda por el puerto de `SP`, inserción en {@code
 * INACTIVO}, auditoría, y la relectura del detalle con su forma completa: sobre un paquete vacío,
 * tres ceros y {@code offerable: false} por «menos de dos».
 */
@Service
public class RegisterPackageService {

  private final ProductPackageRepository paquetes;
  private final CurrencyCatalog monedas;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final PackageDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public RegisterPackageService(
      ProductPackageRepository paquetes,
      CurrencyCatalog monedas,
      AuditWriter auditoria,
      UuidV7Generator ids,
      PackageDetailReader detalle) {
    this(paquetes, monedas, auditoria, ids, detalle, Clock.systemUTC());
  }

  RegisterPackageService(
      ProductPackageRepository paquetes,
      CurrencyCatalog monedas,
      AuditWriter auditoria,
      UuidV7Generator ids,
      PackageDetailReader detalle,
      Clock reloj) {
    this.paquetes = paquetes;
    this.monedas = monedas;
    this.auditoria = auditoria;
    this.ids = ids;
    this.detalle = detalle;
    this.reloj = reloj;
  }

  @Transactional
  public PackageDetailResponse register(RegisterPackageRequest peticion) {
    verificarUnicidad(peticion);
    CurrencyView moneda = verificarMoneda(peticion);

    ProductPackage nuevo =
        paquetes.save(
            ProductPackage.create(
                ids.next(),
                peticion.code(),
                peticion.name(),
                peticion.description(),
                moneda.id(),
                peticion.scope(),
                OffsetDateTime.now(reloj)));

    // La instantánea la arma el agregado, y es la misma que usa el retiro
    // (`RF-PM-022`): el registro de creación y el de eliminación describen el
    // mismo paquete con las mismas claves.
    auditoria.recordChange(
        new ChangeEvent(
            PackageDetailReader.MODULO,
            PackageDetailReader.ENTIDAD,
            nuevo.getId(),
            ChangeAction.CREATE,
            nuevo.instantanea()));

    return detalle.leer(nuevo.getId());
  }

  private void verificarUnicidad(RegisterPackageRequest peticion) {
    String codigo = peticion.code() == null ? null : peticion.code().toUpperCase(Locale.ROOT);
    // Contra TODOS, retirados incluidos (`EX-001`, `RN-PM-041`): el código no se
    // libera. La red es `uq_product_packages_code`, total.
    if (codigo != null && paquetes.existsCode(codigo)) {
      String mensaje = "Ya existe un paquete con ese código.";
      throw new BusinessRuleException(
          "EX-001", mensaje, List.of(new FieldError("code", "EX-001", mensaje)));
    }
    // Solo contra los VIVOS (`EX-002`): un retirado libera el nombre. La red es
    // el índice parcial, que muerde en el INSERT y sale con el mismo código.
    if (peticion.name() != null && paquetes.existsAliveName(peticion.name())) {
      String mensaje = "Ya existe un paquete con ese nombre.";
      throw new BusinessRuleException(
          "EX-002", mensaje, List.of(new FieldError("name", "EX-002", mensaje)));
    }
  }

  private CurrencyView verificarMoneda(RegisterPackageRequest peticion) {
    String mensaje = "La moneda indicada no existe o no está activa.";
    CurrencyView moneda =
        monedas
            .find(peticion.currencyId())
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "EX-003",
                        mensaje,
                        List.of(new FieldError("currencyId", "EX-003", mensaje))));
    if (!moneda.active()) {
      throw new UnprocessableEntityException(
          "EX-003", mensaje, List.of(new FieldError("currencyId", "EX-003", mensaje)));
    }
    return moneda;
  }
}
