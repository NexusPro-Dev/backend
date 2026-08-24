package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.users.application.DeleteUserRequest;
import com.factech.nexus.modules.system.users.domain.models.ChangeReason;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import com.factech.nexus.modules.system.users.domain.repository.RoleCatalog;
import com.factech.nexus.modules.system.users.domain.repository.UserMembership;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.modules.system.users.domain.repository.UserSupervisor;
import com.factech.nexus.modules.system.users.domain.security.RootAdministratorPresence;
import com.factech.nexus.modules.system.users.domain.security.SelfOperationGuard;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ForbiddenException;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.security.SessionRevoker;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Eliminar lógicamente a una persona (`RF-SP-029`).
 *
 * <p>Orden de verificación (`plan.md` §4):
 *
 * <ol>
 *   <li><b>Motivo presente y con contenido</b>, lo primero de todo: el Art. V.13 exige rechazar la
 *       eliminación sin motivo <b>antes de ejecutarla</b>.
 *   <li>La persona existe y no está eliminada, <b>bloqueada</b> — {@code 404}, sin distinguir
 *       «nunca existió» de «ya estaba eliminada».
 *   <li>No es el propio actor (`RN-SP-017`) — {@code 403}.
 *   <li>No es el último portador activo del rol raíz — {@code 409}.
 *   <li>No tiene personas a cargo — {@code 409}.
 *   <li><b>Captura del estado completo, antes de tocar nada.</b>
 *   <li>Marca, borrado de asignaciones, cierre del superior, revocación de sesiones y auditoría.
 * </ol>
 *
 * <p><b>El paso 6 es el que se olvida y no falla.</b> Después de borrar las asignaciones ya no hay
 * nada que capturar, y el registro de eliminación quedaría sin decir qué roles y qué membresía
 * tenía la persona — que es justo lo que el Art. V.13 existe para conservar. Nada avisaría.
 *
 * <p><b>No hay {@code 409} por tener roles asignados</b>, y es la diferencia con la eliminación de
 * un rol: allí se rechaza si hay portadores porque quedarían colgando. Aquí no hay nada aguas abajo
 * — las asignaciones se retiran <b>con</b> la persona.
 */
@Service
public class DeleteUserService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "users";

  private final UserRepository usuarios;
  private final RoleCatalog roles;
  private final RootAdministratorPresence raiz;
  private final SessionRevoker sesiones;
  private final AuthenticatedActor actor;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public DeleteUserService(
      UserRepository usuarios,
      RoleCatalog roles,
      RootAdministratorPresence raiz,
      SessionRevoker sesiones,
      AuthenticatedActor actor,
      AuditWriter auditoria) {
    this(usuarios, roles, raiz, sesiones, actor, auditoria, Clock.systemUTC());
  }

  DeleteUserService(
      UserRepository usuarios,
      RoleCatalog roles,
      RootAdministratorPresence raiz,
      SessionRevoker sesiones,
      AuthenticatedActor actor,
      AuditWriter auditoria,
      Clock reloj) {
    this.usuarios = usuarios;
    this.roles = roles;
    this.raiz = raiz;
    this.sesiones = sesiones;
    this.actor = actor;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public void delete(UUID userId, DeleteUserRequest peticion) {
    // 1. El motivo, antes que nada.
    ChangeReason motivo = new ChangeReason(peticion == null ? null : peticion.reason());

    // 2. La persona, bloqueada. El 404 no distingue «nunca existió» de «ya
    //    estaba eliminada»: es el mismo silencio que el detalle impone.
    User usuario =
        usuarios
            .findNotDeletedByIdForUpdate(userId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-004", "No existe una persona con ese identificador."));

    // 3. `RN-SP-017` → 403.
    if (SelfOperationGuard.esSuPropiaCuenta(actor.id(), userId)) {
      String mensaje = "No puede eliminar su propia cuenta.";
      throw new ForbiddenException(
          "RN-SP-017", mensaje, List.of(new FieldError("id", "RN-SP-017", mensaje)));
    }

    // 4 y 5.
    if (!raiz.sobreviveSinEl(userId)) {
      String mensaje =
          "Es el último superadministrador activo: eliminarlo dejaría al sistema sin ninguna vía de"
              + " administración. Nombre otro antes.";
      throw new BusinessRuleException(
          "RN-SP-001", mensaje, List.of(new FieldError("id", "RN-SP-001", mensaje)));
    }
    int aCargo = usuarios.countSupervisees(userId);
    if (aCargo > 0) {
      String mensaje =
          "La persona tiene " + aCargo + " a cargo: reasigne su equipo antes de eliminarla.";
      throw new BusinessRuleException(
          "RN-SP-022", mensaje, List.of(new FieldError("id", "RN-SP-022", mensaje)));
    }

    // 6. LA CAPTURA VA ANTES DE TOCAR NADA. Después no hay qué capturar, y
    //    olvidarlo no rompe ninguna prueba que no lo verifique a propósito.
    List<AssignableRole> rolesQueTenia = roles.findAllById(roles.roleIdsOf(userId));
    Optional<UserMembership> membresia = usuarios.findMembership(userId);
    Optional<UserSupervisor> superior = usuarios.findActiveSupervisor(userId);
    String estadoAlEliminar = usuario.getStatus().name();

    // 7. Todo en la misma transacción, y en este orden.
    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    usuarios.markDeleted(userId, ahora);
    usuarios.removeAllRoles(userId);
    membresia.ifPresent(sinUsar -> usuarios.removeMembership(userId));

    // La MISMA marca de tiempo que la eliminación, no una posterior: si
    // difirieran, el historial diría que la persona estuvo a cargo de alguien
    // durante unos milisegundos después de haber dejado de existir.
    superior.ifPresent(sinUsar -> usuarios.endSupervisor(userId, ahora));

    sesiones.revokeAllForAccessChange(userId);

    auditar(
        usuario,
        estadoAlEliminar,
        rolesQueTenia,
        membresia.orElse(null),
        superior.orElse(null),
        motivo);
  }

  /**
   * Eliminación lógica <b>con motivo</b>, y un evento de seguridad tras el commit.
   *
   * <p>El {@code snapshot} lleva el estado completo que la persona tenía al eliminarse: su estado
   * —que <b>no</b> se toca, para que el registro diga en qué situación estaba—, sus roles, su
   * membresía y su superior. Es lo que permite reconstruir qué era, y sin ello el registro solo
   * diría que existió.
   *
   * <p><b>Sin ningún campo derivado de la credencial</b>, ni siquiera su longitud (Art. IV.8).
   */
  private void auditar(
      User usuario,
      String estado,
      List<AssignableRole> rolesQueTenia,
      UserMembership membresia,
      UserSupervisor superior,
      ChangeReason motivo) {

    Map<String, Object> foto = new HashMap<>();
    foto.put("username", usuario.getUsername());
    foto.put("email", usuario.getEmail());
    foto.put("first_name", usuario.getFirstName());
    foto.put("last_name", usuario.getLastName());
    foto.put("status", estado);
    foto.put("roles", rolesQueTenia.stream().map(AssignableRole::code).sorted().toList());
    foto.put("membership_code", membresia == null ? null : membresia.code());
    foto.put("supervisor", superior == null ? null : superior.username());

    auditoria.recordDeletion(
        new DeletionEvent(
            MODULO, ENTIDAD, usuario.getId(), DeletionType.LOGICAL, motivo.value(), foto));

    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.USER_DELETED,
            Severity.ALTA,
            Outcome.SUCCESS,
            usuario.getId(),
            Map.of("username", usuario.getUsername(), "reason", motivo.value())));
  }
}
