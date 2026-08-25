package com.factech.nexus.modules.system.roles.domain.service;

import com.factech.nexus.modules.system.roles.application.ChangeRoleStatusRequest;
import com.factech.nexus.modules.system.roles.application.RoleResponse;
import com.factech.nexus.modules.system.roles.domain.models.Role;
import com.factech.nexus.modules.system.roles.domain.models.RoleStatus;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Activar o desactivar un rol (`RF-SP-007`).
 *
 * <p><b>Desactivar no es retirar.</b> Las asignaciones a personas se conservan intactas
 * (`CA-SP-051`) y lo que cambia es que el rol deja de conceder permisos <b>de inmediato</b>
 * (`RN-SEG-002`, `CA-SP-050`). Eso ocurre sin tocar nada más porque los permisos efectivos se
 * resuelven contra los roles vigentes en cada petición, y no se copian a ningún sitio: no hay caché
 * que invalidar ni token que esperar a que expire.
 *
 * <p><b>El rol raíz se protege aparte de los de sistema</b>, y no por redundancia: un rol raíz
 * inactivo no concede nada, lo que dejaría al sistema sin su última vía de administración. La
 * prohibición no debe depender de que alguien recuerde marcarlo como de sistema.
 *
 * <p><b>La operación es idempotente</b> (`FA-001`, `CA-SP-052`): pedir el estado que el rol ya
 * tiene no cambia nada, no registra evento y no es un error.
 */
@Service
public class ChangeRoleStatusService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "roles";

  private final RoleWriteAccess acceso;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public ChangeRoleStatusService(RoleWriteAccess acceso, AuditWriter auditoria) {
    this(acceso, auditoria, Clock.systemUTC());
  }

  ChangeRoleStatusService(RoleWriteAccess acceso, AuditWriter auditoria, Clock reloj) {
    this.acceso = acceso;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public RoleResponse change(UUID roleId, ChangeRoleStatusRequest peticion) {
    RoleStatus destino = resolver(peticion.status());

    Role rol = acceso.cargarModificable(roleId, "EX-003");
    acceso.verificarNoEsLaRaiz(rol);

    RoleStatus anterior = rol.getStatus();
    if (!rol.changeStatus(destino, java.time.OffsetDateTime.now(reloj))) {
      return acceso.respuesta(rol);
    }

    auditoria.recordChange(
        new ChangeEvent(
            MODULO,
            ENTIDAD,
            rol.getId(),
            ChangeAction.UPDATE,
            Map.of("status", Map.of("before", anterior.name(), "after", destino.name()))));

    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.ROLE_UPDATED,
            Severity.ALTA,
            Outcome.SUCCESS,
            null,
            Map.of(
                "roleId", rol.getId().toString(),
                "roleCode", rol.getCode().value(),
                "status", Map.of("before", anterior.name(), "after", destino.name()))));

    return acceso.respuesta(rol);
  }

  /**
   * `VAL-001`. Se compara sin distinguir caja y el mensaje enumera los admitidos: un estado
   * rechazado sin decir cuáles existen obliga a buscarlos en la documentación.
   */
  private static RoleStatus resolver(String valor) {
    return Arrays.stream(RoleStatus.values())
        .filter(estado -> estado.name().equalsIgnoreCase(valor.trim()))
        .findFirst()
        .orElseThrow(
            () -> {
              String mensaje =
                  "El estado '"
                      + valor
                      + "' no es válido. Valores admitidos: "
                      + Arrays.stream(RoleStatus.values()).map(Enum::name).toList()
                      + ".";
              return new ValidationException(
                  "VAL-001", mensaje, List.of(new FieldError("status", "VAL-001", mensaje)));
            });
  }
}
