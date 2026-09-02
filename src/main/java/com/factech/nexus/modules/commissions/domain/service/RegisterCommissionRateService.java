package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.CommissionRateResponse;
import com.factech.nexus.modules.commissions.application.RegisterCommissionRateRequest;
import com.factech.nexus.modules.commissions.domain.models.CommissionRate;
import com.factech.nexus.modules.commissions.domain.repository.CommissionRateRepository;
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
 * Alta de una tasa de comisión <b>de rol</b> (`RF-CM-001`).
 *
 * <p><b>Le quedó una verificación de las cuatro que tenía.</b> Hasta el 01-09-2026 comprobaba el
 * rol, el producto, la persona y el solapamiento; ahora solo el rol. Las otras tres se fueron con
 * los campos: el producto a `RF-CM-007`, la persona a `RF-CM-006` y el solapamiento a la vigencia,
 * que esta tabla ya no tiene.
 *
 * <p><b>Y lo que registra NO paga nada todavía</b> (`RN-CM-012`). Esta operación llena un catálogo;
 * lo que pone una tasa en vigor es asociarla a un producto. Una tasa creada y no asociada parece
 * configurada y no rige — y eso no falla, se descubre liquidando. Por eso la respuesta lleva {@code
 * associatedProducts}, que aquí vale siempre cero.
 *
 * <p><b>La lectura de fuera entra por una interfaz publicada</b> (**D-25**), nunca por las tablas
 * de `SP`. La regla de ArchUnit lo ancla: sin ella la frontera sería una convención.
 */
@Service
public class RegisterCommissionRateService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "commission_rates";

  private final CommissionRateRepository tasas;
  private final RoleCatalog roles;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  /**
   * Constructor de producción. La anotación es obligatoria porque la clase declara dos
   * constructores —el segundo existe para que la prueba pueda fijar el reloj— y Spring solo infiere
   * cuando hay uno.
   */
  @Autowired
  public RegisterCommissionRateService(
      CommissionRateRepository tasas,
      RoleCatalog roles,
      AuditWriter auditoria,
      UuidV7Generator ids) {
    this(tasas, roles, auditoria, ids, Clock.systemUTC());
  }

  RegisterCommissionRateService(
      CommissionRateRepository tasas,
      RoleCatalog roles,
      AuditWriter auditoria,
      UuidV7Generator ids,
      Clock reloj) {
    this.tasas = tasas;
    this.roles = roles;
    this.auditoria = auditoria;
    this.ids = ids;
    this.reloj = reloj;
  }

  @Transactional
  public CommissionRateResponse register(RegisterCommissionRateRequest peticion) {
    RoleView rol = verificarRol(peticion);

    CommissionRate nueva =
        tasas.save(
            CommissionRate.create(
                ids.next(), peticion.roleId(), peticion.percentage(), OffsetDateTime.now(reloj)));

    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, nueva.getId(), ChangeAction.CREATE, nueva.instantanea()));

    return CommissionRateResponse.from(nueva, rol);
  }

  /**
   * `RN-CM-001`, en dos mitades que se distinguen.
   *
   * <p>Que el rol no exista es un dato equivocado —`422`—; que exista y no sea vendedor es un dato
   * que <b>no vale para esto</b> —`400`—. Devolver lo mismo haría que quien escribió bien el
   * identificador buscara el error donde no está.
   */
  private RoleView verificarRol(RegisterCommissionRateRequest peticion) {
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
