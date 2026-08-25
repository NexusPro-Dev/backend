package com.factech.nexus.modules.system.roles.domain.service;

import com.factech.nexus.modules.system.permissions.application.PermissionItem;
import com.factech.nexus.modules.system.roles.application.DeleteRoleRequest;
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
import com.factech.nexus.shared.error.ValidationException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Eliminar un rol (`RF-SP-009`).
 *
 * <p><b>El motivo se verifica el primero de todo</b>, antes de leer el rol: el Art. V.13 exige
 * rechazar la eliminación sin motivo <b>antes de ejecutarla</b>, y comprobarlo después dejaría el
 * caso en que un motivo vacío llega hasta el borrado y solo se detiene por casualidad.
 *
 * <p>Orden completo, que es el de `spec.md` §8:
 *
 * <ol>
 *   <li>Motivo informado y con contenido real (`EX-001` → {@code 400}).
 *   <li>Rol vigente, no de sistema y no del actor — las tres puertas comunes.
 *   <li>No es el rol raíz (`EX-004` → {@code 409}).
 *   <li>Sin roles hijos vigentes (`EX-002` → {@code 409}), enumerándolos.
 *   <li>Sin personas asignadas (`EX-003` → {@code 409}), diciendo cuántas.
 *   <li>Borrado lógico y auditoría.
 * </ol>
 *
 * <p><b>Los dos rechazos de `RN-SEG-008` informan qué lo impide y no solo que algo lo impide.</b>
 * Un «no se puede eliminar» sin detalle obliga a buscar a mano qué roles cuelgan o quién porta el
 * rol, que es justo lo que el actor necesitaba saber para decidir. Al de personas se le sugiere
 * además <b>desactivar</b> el rol con `RF-SP-007`, que suele ser lo que en realidad se quería.
 */
@Service
public class DeleteRoleService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "roles";

  private final RoleRepository roles;
  private final RoleWriteAccess acceso;
  private final PermissionCatalog catalogo;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public DeleteRoleService(
      RoleRepository roles,
      RoleWriteAccess acceso,
      PermissionCatalog catalogo,
      AuditWriter auditoria) {
    this(roles, acceso, catalogo, auditoria, Clock.systemUTC());
  }

  DeleteRoleService(
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
  public void delete(UUID roleId, DeleteRoleRequest peticion) {
    String motivo = verificarMotivo(peticion);

    Role rol = acceso.cargarModificable(roleId, "EX-006");
    acceso.verificarNoEsLaRaiz(rol);
    verificarSinHijos(rol);
    verificarSinPersonas(rol);

    // El estado se captura ANTES de borrar: después, `deleted_at` ya no es nulo
    // y el registro diría que se eliminó un rol que ya estaba eliminado.
    List<PermissionItem> permisos = catalogo.findAllById(rol.getPermissionIds());
    Map<String, Object> estado = estadoAlEliminar(rol, permisos);

    rol.delete(OffsetDateTime.now(reloj));

    // En la MISMA transacción (Art. V.14): si el borrado se revierte, su
    // constancia también.
    auditoria.recordDeletion(
        new DeletionEvent(MODULO, ENTIDAD, rol.getId(), DeletionType.LOGICAL, motivo, estado));

    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.ROLE_DELETED,
            Severity.ALTA,
            Outcome.SUCCESS,
            null,
            Map.of(
                "roleId", rol.getId().toString(),
                "roleCode", rol.getCode().value(),
                "reason", motivo)));
  }

  /** `VAL-001` y `VAL-002` → {@code 400}. Un motivo de solo espacios es no tener motivo. */
  private static String verificarMotivo(DeleteRoleRequest peticion) {
    String motivo = peticion == null || peticion.reason() == null ? "" : peticion.reason().trim();
    if (motivo.isEmpty()) {
      String mensaje = "Debe indicar el motivo de la eliminación.";
      throw new ValidationException(
          "VAL-001", mensaje, List.of(new FieldError("reason", "VAL-001", mensaje)));
    }
    return motivo;
  }

  /** `EX-002` → {@code 409}, enumerando los roles que lo impiden. */
  private void verificarSinHijos(Role rol) {
    List<String> hijos = roles.childCodesOf(rol.getId());
    if (hijos.isEmpty()) {
      return;
    }
    throw new BusinessRuleException(
        "RN-SEG-008",
        "No es posible eliminar el rol: tiene roles dependientes.",
        hijos.stream()
            .map(
                codigo ->
                    new FieldError(
                        "id", "RN-SEG-008", "El rol '" + codigo + "' depende de este rol."))
            .toList());
  }

  /**
   * `EX-003` → {@code 409}.
   *
   * <p>El mensaje dice <b>cuántas</b> personas y no quiénes: el listado es `RF-SP-025`, con su
   * propio permiso. Y sugiere desactivar, porque quien intenta borrar un rol con gente asignada
   * casi siempre quiere retirar el acceso y no perder el rol.
   */
  private void verificarSinPersonas(Role rol) {
    long asignadas = roles.countAssignedUsers(rol.getId());
    if (asignadas == 0) {
      return;
    }
    String mensaje =
        "No es posible eliminar el rol: "
            + asignadas
            + (asignadas == 1 ? " persona lo tiene" : " personas lo tienen")
            + " asignado. Si el objetivo es retirar el acceso, desactívelo.";
    throw new BusinessRuleException(
        "RN-SEG-008", mensaje, List.of(new FieldError("id", "RN-SEG-008", mensaje)));
  }

  /**
   * El rol completo en el momento de borrarse, con sus permisos <b>por código</b>.
   *
   * <p>Por código y no por identificador: quien lea la auditoría dentro de dos años necesita
   * entender qué concedía el rol sin resolver referencias contra un catálogo que pudo cambiar.
   */
  private static Map<String, Object> estadoAlEliminar(Role rol, List<PermissionItem> permisos) {
    Map<String, Object> estado = new HashMap<>();
    estado.put("code", rol.getCode().value());
    estado.put("name", rol.getName());
    estado.put("description", rol.getDescription());
    estado.put("role_type", rol.getRoleType().name());
    estado.put(
        "parent_role_id", rol.getParentRoleId() == null ? null : rol.getParentRoleId().toString());
    estado.put("status", rol.getStatus().name());
    estado.put("permissions", permisos.stream().map(PermissionItem::code).toList());
    return estado;
  }
}
