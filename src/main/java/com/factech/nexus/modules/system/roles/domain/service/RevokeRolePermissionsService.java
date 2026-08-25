package com.factech.nexus.modules.system.roles.domain.service;

import com.factech.nexus.modules.system.permissions.application.PermissionItem;
import com.factech.nexus.modules.system.roles.application.RolePermissionsRequest;
import com.factech.nexus.modules.system.roles.application.RoleResponse;
import com.factech.nexus.modules.system.roles.domain.models.Role;
import com.factech.nexus.modules.system.roles.domain.repository.PermissionCatalog;
import com.factech.nexus.modules.system.roles.domain.repository.RoleRepository;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retirar permisos de un rol (`RF-SP-006`).
 *
 * <p><b>`RN-SEG-005` es lo que distingue esta operación de su simétrica.</b> Agregar un permiso al
 * padre nunca rompe nada —el conjunto solo crece—, mientras que retirárselo puede dejar a un hijo
 * declarando algo que su padre ya no tiene, que es exactamente el invariante de contención al
 * revés. Por eso aquí hay una comprobación que en `RF-SP-005` no existe.
 *
 * <p><b>No hay cascada</b> (`CA-SP-043`): el rechazo dice qué roles y qué permisos lo impiden, y es
 * el actor quien decide en qué orden retirarlos. Propagar la revocación hacia abajo retiraría
 * permisos que nadie pidió retirar, en roles que el actor quizá ni sabía que existían.
 *
 * <p><b>Los hijos inactivos cuentan igual que los activos</b> (`CA-SP-155`): el invariante vale
 * siempre, no solo mientras el rol concede algo. Los eliminados no, porque no están vigentes.
 *
 * <p><b>La eliminación de la asociación es física y no exige motivo</b> (`RN-SP-005`, `CA-SP-046`):
 * es una asociación y no una entidad de negocio, de modo que el Art. V.13 no la alcanza. Sí queda
 * constancia en la auditoría de eliminación, con los <b>códigos</b> de rol y de permiso — legibles
 * sin resolver referencias contra un catálogo que pudo cambiar (`CA-SP-156`).
 */
@Service
public class RevokeRolePermissionsService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "role_permissions";

  private final RoleRepository roles;
  private final RoleWriteAccess acceso;
  private final PermissionCatalog catalogo;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public RevokeRolePermissionsService(
      RoleRepository roles,
      RoleWriteAccess acceso,
      PermissionCatalog catalogo,
      AuditWriter auditoria) {
    this(roles, acceso, catalogo, auditoria, Clock.systemUTC());
  }

  RevokeRolePermissionsService(
      RoleRepository roles,
      RoleWriteAccess acceso,
      PermissionCatalog catalogo,
      AuditWriter auditoria,
      Clock reloj) {
    this.roles = roles;
    this.acceso = acceso;
    this.catalogo = catalogo;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public RoleResponse revoke(UUID roleId, RolePermissionsRequest peticion) {
    Role rol = acceso.cargarModificable(roleId, "EX-004");

    // No se exige que los permisos existan en el catálogo: retirar algo que no
    // está es idempotente, y un identificador inventado simplemente no coincide
    // con ninguna asociación. Lo que se resuelve es lo que el rol SÍ declara,
    // que es lo único que puede retirarse.
    Set<UUID> solicitados = peticion.permissionIds();
    verificarSinHijosQueLoDeclaren(rol, solicitados);

    List<PermissionItem> presentes =
        catalogo.findAllById(solicitados).stream()
            .filter(permiso -> rol.getPermissionIds().contains(permiso.id()))
            .toList();

    List<PermissionItem> retirados = rol.revoke(presentes, OffsetDateTime.now(reloj));

    if (retirados.isEmpty()) {
      // `FA-001`: ninguno estaba asociado. Nada cambió, nada se registra.
      return acceso.respuesta(rol);
    }

    auditar(rol, retirados);
    return acceso.respuesta(rol);
  }

  /**
   * `EX-001` → {@code 409} (`RN-SEG-005`), diciendo <b>qué rol</b> declara <b>qué permiso</b>.
   *
   * <p>Sin ese detalle el actor no sabría qué corregir: un «lo declaran roles dependientes» obliga
   * a revisar a mano cada hijo y cada permiso.
   */
  private void verificarSinHijosQueLoDeclaren(Role rol, Set<UUID> solicitados) {
    List<RoleRepository.PermissionHolder> impedimentos =
        roles.childrenDeclaring(rol.getId(), solicitados);

    if (impedimentos.isEmpty()) {
      return;
    }
    throw new BusinessRuleException(
        "RN-SEG-005",
        "No es posible retirar el permiso: lo declaran uno o más roles dependientes.",
        impedimentos.stream()
            .map(
                impedimento ->
                    new FieldError(
                        "permissionIds",
                        "RN-SEG-005",
                        "El rol '"
                            + impedimento.roleCode()
                            + "' declara el permiso '"
                            + impedimento.permissionCode()
                            + "'."))
            .toList());
  }

  /**
   * Auditoría de <b>eliminación</b> y no de cambio, con {@code deletion_type = ASSOCIATION} y sin
   * motivo.
   *
   * <p>Es lo que `RN-SP-005` decide: lo que desaparece es una asociación, no una entidad, y exigir
   * motivo para retirar un permiso convertiría cada ajuste de un rol en un trámite. La constancia
   * sigue existiendo — quién, cuándo y qué —, que es lo que la auditoría necesita.
   */
  private void auditar(Role rol, List<PermissionItem> retirados) {
    List<String> codigos = retirados.stream().map(PermissionItem::code).sorted().toList();

    auditoria.recordDeletion(
        new DeletionEvent(
            MODULO,
            ENTIDAD,
            rol.getId(),
            DeletionType.ASSOCIATION,
            null,
            Map.of(
                "role_code", rol.getCode().value(),
                "role_id", rol.getId().toString(),
                "permissions", codigos)));

    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.ROLE_PERMISSIONS_CHANGED,
            Severity.ALTA,
            Outcome.SUCCESS,
            null,
            Map.of(
                "roleId", rol.getId().toString(),
                "roleCode", rol.getCode().value(),
                "revoked", codigos)));
  }
}
