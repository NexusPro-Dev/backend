package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.AfftrackRateResponse;
import com.factech.nexus.modules.commissions.application.RegisterAfftrackRateRequest;
import com.factech.nexus.modules.commissions.domain.models.AfftrackRate;
import com.factech.nexus.modules.commissions.domain.repository.AfftrackRateQueryRepository;
import com.factech.nexus.modules.commissions.domain.repository.AfftrackRateRepository;
import com.factech.nexus.modules.commissions.domain.repository.JpaAfftrackRateRepository;
import com.factech.nexus.modules.products.application.ProductCatalog.ProductView;
import com.factech.nexus.modules.system.roles.application.RoleCatalog;
import com.factech.nexus.modules.system.roles.application.RoleCatalog.RoleView;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RF-CM-015` — registrar un escalón afftrack de un rol sobre un producto FTD.
 *
 * <p>El orden de `RF-CM-001`, sin el tope: el rol es vendedor (`RN-CM-001`), el producto existe, no
 * está retirado y <b>es FTD</b> (`RN-CM-037`), el valor cabe en los decimales de su moneda
 * (`RN-CM-038`), y no hay otro escalón vivo con el mismo límite (`RN-CM-039`). <b>Sin bloqueo
 * consultivo</b>: sin tope que sumar, la única carrera es el duplicado, y la cierra el índice.
 *
 * <p><b>Registrar es poner en vigor</b>, como la tasa de rol: rige desde el siguiente cierre.
 */
@Service
public class RegisterAfftrackRateService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "afftrack_rates";

  private final AfftrackRateRepository escalones;
  private final AfftrackRateQueryRepository consultas;
  private final RoleCatalog roles;
  private final AfftrackProductCheck producto;
  private final ProductCurrencyScale escala;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  @Autowired
  public RegisterAfftrackRateService(
      AfftrackRateRepository escalones,
      AfftrackRateQueryRepository consultas,
      RoleCatalog roles,
      AfftrackProductCheck producto,
      ProductCurrencyScale escala,
      AuditWriter auditoria,
      UuidV7Generator ids) {
    this(escalones, consultas, roles, producto, escala, auditoria, ids, Clock.systemUTC());
  }

  RegisterAfftrackRateService(
      AfftrackRateRepository escalones,
      AfftrackRateQueryRepository consultas,
      RoleCatalog roles,
      AfftrackProductCheck producto,
      ProductCurrencyScale escala,
      AuditWriter auditoria,
      UuidV7Generator ids,
      Clock reloj) {
    this.escalones = escalones;
    this.consultas = consultas;
    this.roles = roles;
    this.producto = producto;
    this.escala = escala;
    this.auditoria = auditoria;
    this.ids = ids;
    this.reloj = reloj;
  }

  @Transactional
  public AfftrackRateResponse register(RegisterAfftrackRateRequest peticion) {
    RoleView rol = verificarRol(peticion);
    ProductView ftd = producto.verificar(peticion.productId());

    // El valor está en la moneda del producto (`RN-CM-017`, `RN-CM-038`).
    escala.verificarImporte(
        ftd.id(), peticion.amountPerFtd(), "VAL-005", "amountPerFtd", "El valor por FTD");

    int limite = peticion.limite();
    if (escalones.existsAlive(ftd.id(), rol.id(), limite, null)) {
      throw JpaAfftrackRateRepository.yaHayUnoConEseLimite();
    }

    AfftrackRate nuevo =
        escalones.save(
            AfftrackRate.create(
                ids.next(),
                ftd.id(),
                rol.id(),
                limite,
                peticion.amountPerFtd(),
                OffsetDateTime.now(reloj)));

    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, nuevo.getId(), ChangeAction.CREATE, nuevo.instantanea()));

    return consultas
        .findRow(nuevo.getId())
        .map(AfftrackRateResponse::from)
        .orElseThrow(() -> new IllegalStateException("El escalón recién creado no se lee."));
  }

  /** `EX-002` y `EX-001`, con los mismos códigos que el alta de tasas (`RF-CM-001`). */
  private RoleView verificarRol(RegisterAfftrackRateRequest peticion) {
    RoleView rol =
        roles
            .find(peticion.roleId())
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "EX-002",
                        "El rol indicado no existe.",
                        List.of(new FieldError("roleId", "EX-002", "El rol indicado no existe."))));
    if (!rol.esVendedor()) {
      String mensaje = "Solo los roles de tipo vendedor pueden llevar comisión.";
      throw new ValidationException(
          "EX-001", mensaje, List.of(new FieldError("roleId", "EX-001", mensaje)));
    }
    return rol;
  }
}
