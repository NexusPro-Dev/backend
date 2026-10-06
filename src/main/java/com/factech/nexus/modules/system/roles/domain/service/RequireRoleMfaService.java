package com.factech.nexus.modules.system.roles.domain.service;

import com.factech.nexus.modules.system.roles.application.RoleMfaRequirementRequest;
import com.factech.nexus.modules.system.roles.application.RoleMfaRequirementResponse;
import com.factech.nexus.modules.system.roles.domain.models.Role;
import com.factech.nexus.modules.system.roles.domain.repository.RoleRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
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
 * Exigir el segundo factor a los portadores de un rol (`RF-SP-077`, `RN-SP-062`).
 *
 * <p><b>La forma de {@code ChangeRoleStatusService}</b> (`plan.md` §1): un {@code PATCH} que no
 * escribe nada si el valor no cambia. <b>Lo que lo separa de aquel es qué guarda aplica</b>:
 * `RN-SEG-011` —el actor no porta el rol— sí; `RN-SEG-012` —los roles de sistema no se tocan—
 * <b>no</b>, como en la asignación de permisos: la marca es justo para los roles de sistema.
 *
 * <p><b>El efecto no lo produce este servicio</b>: la marca la leen el inicio de sesión y la
 * renovación al calcular {@code mer} (`RF-SP-072`). Por eso no se cierra ninguna sesión: quien
 * porta el rol queda retenido —o liberado— en su siguiente renovación.
 */
@Service
public class RequireRoleMfaService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "roles";

  private final RoleWriteAccess acceso;
  private final RoleRepository roles;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public RequireRoleMfaService(
      RoleWriteAccess acceso, RoleRepository roles, AuditWriter auditoria) {
    this(acceso, roles, auditoria, Clock.systemUTC());
  }

  RequireRoleMfaService(
      RoleWriteAccess acceso, RoleRepository roles, AuditWriter auditoria, Clock reloj) {
    this.acceso = acceso;
    this.roles = roles;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public RoleMfaRequirementResponse cambiar(UUID roleId, RoleMfaRequirementRequest peticion) {
    if (peticion == null || peticion.required() == null) {
      String mensaje = "Debe indicar si el rol exige el segundo factor.";
      throw new ValidationException(
          "VAL-001", mensaje, List.of(new FieldError("required", "VAL-001", mensaje)));
    }
    boolean exigido = peticion.required();

    // `RN-SEG-011` sí, `RN-SEG-012` no (`plan.md` §1).
    Role rol = acceso.cargarConPermisosModificables(roleId, "EX-003");
    boolean anterior = rol.requiresMfa();

    boolean cambio;
    try {
      cambio = rol.exigirSegundoFactor(exigido, OffsetDateTime.now(reloj));
    } catch (IllegalStateException raiz) {
      String mensaje = "El rol raíz exige siempre el segundo factor.";
      throw new UnprocessableEntityException(
          "VAL-002", mensaje, List.of(new FieldError("required", "VAL-002", mensaje)));
    }

    if (cambio) {
      auditoria.recordChange(
          new ChangeEvent(
              MODULO,
              ENTIDAD,
              rol.getId(),
              ChangeAction.UPDATE,
              Map.of("requires_mfa", Map.of("before", anterior, "after", exigido))));
      auditoria.recordSecurityAfterCommit(
          new SecurityEvent(
              SecurityEventType.ROLE_MFA_REQUIREMENT_CHANGED,
              Severity.ALTA,
              Outcome.SUCCESS,
              null,
              Map.of(
                  "roleId",
                  rol.getId().toString(),
                  "roleCode",
                  rol.getCode().value(),
                  "required",
                  exigido)));
    }

    RoleRepository.Portadores portadores = roles.contarPortadores(rol.getId());
    return new RoleMfaRequirementResponse(
        rol.getId(),
        rol.getCode().value(),
        rol.requiresMfa(),
        portadores.activos(),
        portadores.sinSegundoFactor());
  }
}
