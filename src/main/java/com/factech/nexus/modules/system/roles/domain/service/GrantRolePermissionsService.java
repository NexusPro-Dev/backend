package com.factech.nexus.modules.system.roles.domain.service;

import com.factech.nexus.modules.system.permissions.application.PermissionItem;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.roles.application.RolePermissionsRequest;
import com.factech.nexus.modules.system.roles.application.RoleResponse;
import com.factech.nexus.modules.system.roles.domain.models.Role;
import com.factech.nexus.modules.system.roles.domain.repository.PermissionCatalog;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agregar permisos a un rol (`RF-SP-005`).
 *
 * <p><b>La operación se aplica entera o no se aplica.</b> Un solo permiso que incumpla la
 * contención rechaza la petición completa (`spec.md` §13): aplicar los válidos e ignorar los que
 * fallan dejaría al rol en un estado que nadie pidió, y el actor no tendría forma de saber cuál de
 * los dos resultados obtuvo sin volver a consultarlo.
 *
 * <p>Orden de verificación:
 *
 * <ol>
 *   <li>Las tres puertas comunes: rol vigente, no de sistema, no del actor.
 *   <li>Los permisos existen en el catálogo (`EX-003` → {@code 422}), enumerando <b>todos</b> los
 *       ausentes.
 *   <li>Contención en el rol padre (`RN-SEG-003` → {@code 409}) — omitida en la raíz, que no tiene
 *       cota superior.
 *   <li>Contención en los permisos efectivos del actor (`RN-SEG-010` → {@code 409}).
 * </ol>
 *
 * <p>Las dos contenciones viven en el agregado y no aquí: son reglas de negocio, no orquestación. Y
 * ambas se registran con severidad <b>alta</b> en la auditoría de error, porque las dos son
 * intentos de escalada de privilegios y deben poder encontrarse buscando por severidad.
 *
 * <p><b>Idempotente</b> (`CA-SP-034`): los permisos que el rol ya declaraba se ignoran sin error ni
 * duplicados, y si no queda ninguno por agregar no hay cambio ni evento. <b>Nunca retira</b>
 * (`CA-SP-153`): agregar es agregar.
 */
@Service
public class GrantRolePermissionsService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "roles";

  private final RoleWriteAccess acceso;
  private final PermissionCatalog catalogo;
  private final AuthenticatedActor actor;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public GrantRolePermissionsService(
      RoleWriteAccess acceso,
      PermissionCatalog catalogo,
      AuthenticatedActor actor,
      AuditWriter auditoria) {
    this(acceso, catalogo, actor, auditoria, Clock.systemUTC());
  }

  GrantRolePermissionsService(
      RoleWriteAccess acceso,
      PermissionCatalog catalogo,
      AuthenticatedActor actor,
      AuditWriter auditoria,
      Clock reloj) {
    this.acceso = acceso;
    this.catalogo = catalogo;
    this.actor = actor;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public RoleResponse grant(UUID roleId, RolePermissionsRequest peticion) {
    Role rol = acceso.cargarModificable(roleId, "EX-006");

    List<PermissionItem> solicitados = resolver(peticion.permissionIds());
    Role padre = acceso.padreDe(rol);

    List<PermissionItem> agregados =
        rol.grant(solicitados, padre, actor.permissions(), OffsetDateTime.now(reloj));

    if (agregados.isEmpty()) {
      // `FA-001`: todos estaban ya. Nada cambió, de modo que nada se registra.
      return acceso.respuesta(rol);
    }

    auditar(rol, agregados);
    return acceso.respuesta(rol);
  }

  /**
   * `EX-003` → {@code 422}. Enumera <b>todos</b> los ausentes del catálogo y no el primero:
   * devolverlos de a uno convierte una corrección en varias vueltas.
   */
  private List<PermissionItem> resolver(Set<UUID> solicitados) {
    List<PermissionItem> encontrados = catalogo.findAllById(solicitados);
    if (encontrados.size() == solicitados.size()) {
      return encontrados;
    }

    Set<UUID> existentes =
        new LinkedHashSet<>(encontrados.stream().map(PermissionItem::id).toList());
    List<FieldError> ausentes =
        solicitados.stream()
            .filter(id -> !existentes.contains(id))
            .map(
                id ->
                    new FieldError(
                        "permissionIds",
                        "EX-003",
                        "El permiso '" + id + "' no existe en el catálogo."))
            .toList();

    throw new UnprocessableEntityException(
        "EX-003", "Uno o más permisos no existen en el catálogo.", ausentes);
  }

  /**
   * La auditoría registra <b>solo los realmente agregados</b>, y por código.
   *
   * <p>Registrar los solicitados incluiría los que el rol ya tenía, y la línea de tiempo del rol
   * diría que alguien le concedió algo que ya tenía — que es indistinguible de una concesión real
   * cuando se investiga meses después.
   */
  private void auditar(Role rol, List<PermissionItem> agregados) {
    List<String> codigos = agregados.stream().map(PermissionItem::code).sorted().toList();

    auditoria.recordChange(
        new ChangeEvent(
            MODULO,
            ENTIDAD,
            rol.getId(),
            ChangeAction.UPDATE,
            Map.of("permissions", Map.of("granted", codigos))));

    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.ROLE_PERMISSIONS_CHANGED,
            Severity.ALTA,
            Outcome.SUCCESS,
            null,
            Map.of(
                "roleId", rol.getId().toString(),
                "roleCode", rol.getCode().value(),
                "granted", codigos)));
  }
}
