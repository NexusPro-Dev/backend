package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.users.application.RevokeRolesRequest;
import com.factech.nexus.modules.system.users.application.UserResponse;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.repository.AssignableCountry;
import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import com.factech.nexus.modules.system.users.domain.repository.RoleCatalog;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.modules.system.users.domain.security.CommercialStructure;
import com.factech.nexus.modules.system.users.domain.security.PrivilegeContainment;
import com.factech.nexus.modules.system.users.domain.security.RoleAssignment;
import com.factech.nexus.modules.system.users.domain.security.RootAdministratorPresence;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.security.SessionRevoker;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retirar roles de una persona (`RF-SP-031`).
 *
 * <p><b>No es la inversa de `RF-SP-030`</b>, y las tres asimetrías son deliberadas:
 *
 * <ul>
 *   <li><b>No se comprueba que los roles existan.</b> Retirar un rol eliminado del catálogo es
 *       legítimo: la asignación sigue ahí y debe poder soltarse. Un rol que la persona no tiene
 *       tampoco es un error — es `FA-001`, y afecta cero filas.
 *   <li><b>Arrastra cascadas.</b> Quedarse sin ningún rol de consumidor <b>borra</b> la membresía
 *       (`RN-SP-015`); quedarse sin ningún rol de vendedor <b>cierra</b> la asignación de superior
 *       (`RN-SP-019`). Asignar no deshace nada; retirar sí.
 *   <li><b>Revoca las sesiones.</b> Asignar no lo hace: ampliar lo que alguien puede hacer no exige
 *       echarlo. Retirar sin revocar deja vivo hasta siete días el acceso que se acaba de quitar,
 *       porque el refresh token sobrevive al cambio de permisos.
 * </ul>
 *
 * <p>Orden de verificación (`plan.md` §4):
 *
 * <ol>
 *   <li>La persona existe y no está eliminada — {@code 404}.
 *   <li>Ningún rol a retirar excede los permisos del actor (`RN-SEG-010`) — {@code 409}.
 *   <li>El retiro no deja al sistema sin superadministrador activo (`RN-SP-001`), <b>bajo
 *       bloqueo</b> — {@code 409}.
 *   <li>Si la persona quedaría sin ningún rol de vendedor, no tiene a nadie a cargo (`RN-SP-022`) —
 *       {@code 409}.
 * </ol>
 *
 * <p>Los pasos 3 y 4 no son evaluables sin saber antes qué roles se retiran <b>de verdad</b>: los
 * que la persona no tenía no cuentan.
 */
@Service
public class RevokeUserRolesService {

  private static final String MODULO = "SP";

  private final UserRepository usuarios;
  private final RoleCatalog roles;
  private final AssignableCountry paises;
  private final CommercialStructure estructura;
  private final RootAdministratorPresence raiz;
  private final SessionRevoker sesiones;
  private final AuthenticatedActor actor;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public RevokeUserRolesService(
      UserRepository usuarios,
      RoleCatalog roles,
      CommercialStructure estructura,
      RootAdministratorPresence raiz,
      SessionRevoker sesiones,
      AuthenticatedActor actor,
      AuditWriter auditoria,
      AssignableCountry paises) {
    this(usuarios, roles, estructura, raiz, sesiones, actor, auditoria, paises, Clock.systemUTC());
  }

  RevokeUserRolesService(
      UserRepository usuarios,
      RoleCatalog roles,
      CommercialStructure estructura,
      RootAdministratorPresence raiz,
      SessionRevoker sesiones,
      AuthenticatedActor actor,
      AuditWriter auditoria,
      AssignableCountry paises,
      Clock reloj) {
    this.usuarios = usuarios;
    this.roles = roles;
    this.paises = paises;
    this.estructura = estructura;
    this.raiz = raiz;
    this.sesiones = sesiones;
    this.actor = actor;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public UserResponse revoke(UUID userId, RevokeRolesRequest peticion) {
    User usuario =
        usuarios
            .findNotDeletedByIdForUpdate(userId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "VAL-006", "No existe una persona con ese identificador."));

    Set<UUID> actuales = roles.roleIdsOf(userId);
    Set<UUID> aRetirar = RoleAssignment.aRetirar(actuales, peticion.roleIds());

    // Los roles que se retiran de verdad, ya con sus permisos. Se leen aunque
    // estén eliminados del catálogo: sus permisos siguen definiendo el alcance.
    List<AssignableRole> retirados = roles.findAllById(aRetirar);

    // 2. `RN-SEG-010` también gobierna el retiro: quien no posee el permiso no
    //    puede quitar el rol que lo concede.
    verificarAlcanceDelActor(retirados);

    if (aRetirar.isEmpty()) {
      // `FA-001`: nada que retirar. No se escribe, no se audita y no se revocan
      // sesiones — echar a alguien de su sesión por una petición que no cambió
      // nada sería un efecto sin causa.
      return UserResponses.de(usuario, roles.findAllById(actuales), usuarios, paises, userId);
    }

    Set<UUID> resultantes = RoleAssignment.resultadoTrasRetirar(actuales, aRetirar);
    List<AssignableRole> catalogoResultante = roles.findAllById(resultantes);

    // 3. `RN-SP-001`, bajo el bloqueo del conjunto de portadores.
    verificarSuperadministrador(userId, retirados, catalogoResultante);

    // 4. `RN-SP-022`, solo si la persona deja de ser vendedor.
    boolean pierdeLaCondicionDeVendedor =
        estructura.rolDeMayorRango(catalogoResultante).isEmpty()
            && !estructura.rolDeMayorRango(roles.findAllById(actuales)).isEmpty();

    if (pierdeLaCondicionDeVendedor) {
      verificarEquipoACargo(userId);
    }

    // 5. `RN-SP-023`: nadie se queda sin ningún rol.
    verificarQueConservaAlgunRol(resultantes);

    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    usuarios.removeRoles(userId, aRetirar);

    // LA CASCADA DE LA MEMBRESÍA SE RETIRÓ EL 05-09-2026, con `RN-SP-015`.
    // Quien deja de ser consumidor CONSERVA LA MEMBRESÍA QUE TENÍA, incluida una
    // comprada: `RN-SP-018` reescrita no admite a nadie sin nivel, y bajarla al
    // suelo sería quitarle a alguien algo que pagó.
    //
    // LA DEL SUPERIOR COMERCIAL SE QUEDA, y las dos eran simétricas — leer este
    // método esperando la otra es el error que este comentario existe para
    // evitar. `RN-SP-019` no se tocó.

    boolean cierraSuperior =
        pierdeLaCondicionDeVendedor && usuarios.findActiveSupervisor(userId).isPresent();
    if (cierraSuperior) {
      usuarios.endSupervisor(userId, ahora);
    }

    // Dentro de la transacción, antes del commit: si esto falla, el retiro se
    // revierte entero. Es preferible que el retiro no ocurra a que ocurra y
    // deje vivo el acceso que decía haber cortado.
    int sesionesRevocadas = sesiones.revokeAllForAccessChange(userId);

    auditar(usuario, retirados, catalogoResultante, cierraSuperior, sesionesRevocadas);

    return UserResponses.de(usuario, catalogoResultante, usuarios, paises, userId);
  }

  // ---------------------------------------------------------------------------
  // Reglas
  // ---------------------------------------------------------------------------

  /**
   * `RN-SP-023`: una persona conserva siempre al menos un rol.
   *
   * <p>Se comprueba <b>la última</b>, después de `RN-SP-001` y de `RN-SP-022`, y el orden importa:
   * aquellas dos protegen al sistema y a terceros, y su rechazo dice más que este. Quien intenta
   * vaciar de roles al último superadministrador debe leer que dejaría al sistema sin
   * administración, no que la persona se queda sin roles.
   *
   * <p><b>No se resuelve conservando uno automáticamente.</b> Cuál dejar es una decisión de negocio
   * que el sistema no puede tomar, y la salida para quien ya no debe operar no es quedarse sin
   * roles —eso produce una cuenta que se autentica y no puede hacer nada— sino `RF-SP-028` o
   * `RF-SP-029`, que además exigen declarar el motivo.
   */
  private void verificarQueConservaAlgunRol(Set<UUID> resultantes) {
    if (resultantes.isEmpty()) {
      throw new BusinessRuleException(
          "RN-SP-023",
          "Una persona debe conservar al menos un rol.",
          List.of(
              new FieldError(
                  "roleIds",
                  "RN-SP-023",
                  "Una persona debe conservar al menos un rol. Para retirarle el acceso,"
                      + " desactive su cuenta.")));
    }
  }

  private void verificarAlcanceDelActor(List<AssignableRole> retirados) {
    List<AssignableRole> excedidos = PrivilegeContainment.excesos(retirados, actor.permissions());
    if (!excedidos.isEmpty()) {
      List<FieldError> detalle =
          excedidos.stream()
              .map(
                  rol ->
                      new FieldError(
                          "roleIds",
                          "RN-SEG-010",
                          "El rol '" + rol.code() + "' concede permisos que usted no posee."))
              .toList();
      throw new BusinessRuleException(
          "RN-SEG-010", "No puede retirar roles que exceden sus propios permisos.", detalle);
    }
  }

  /**
   * `RN-SP-001` → {@code 409}.
   *
   * <p>Solo se consulta si el retiro alcanza <b>de verdad</b> al rol raíz y la persona no lo
   * conserva por otra vía. La consulta toma un bloqueo, y tomarlo en cada retiro de cualquier rol
   * serializaría toda la administración de roles del sistema contra una regla que casi nunca
   * aplica.
   */
  private void verificarSuperadministrador(
      UUID userId, List<AssignableRole> retirados, List<AssignableRole> resultantes) {

    boolean pierdeElRolRaiz =
        retirados.stream().anyMatch(RevokeUserRolesService::esRolRaiz)
            && resultantes.stream().noneMatch(RevokeUserRolesService::esRolRaiz);

    if (!pierdeElRolRaiz) {
      return;
    }
    if (!raiz.sobreviveSinEl(userId)) {
      String mensaje =
          "El sistema no puede quedarse sin superadministrador activo: nombre otro antes de"
              + " retirar este rol.";
      throw new BusinessRuleException(
          "RN-SP-001", mensaje, List.of(new FieldError("roleIds", "RN-SP-001", mensaje)));
    }
  }

  /**
   * `RN-SP-022` → {@code 409}, informando <b>cuántas</b> personas y ninguna identidad.
   *
   * <p>Quién forma ese equipo se consulta con `RF-SP-042`, que tiene su propio permiso: devolver la
   * lista aquí concedería ese permiso por una puerta lateral. El número basta para que quien recibe
   * el rechazo entienda el tamaño de la reasignación que le toca hacer.
   */
  private void verificarEquipoACargo(UUID userId) {
    int aCargo = usuarios.countSupervisees(userId);
    if (aCargo > 0) {
      String mensaje =
          "La persona tiene " + aCargo + " a cargo: reasigne su equipo antes de retirar el rol.";
      throw new BusinessRuleException(
          "RN-SP-022", mensaje, List.of(new FieldError("roleIds", "RN-SP-022", mensaje)));
    }
  }

  private static boolean esRolRaiz(AssignableRole rol) {
    return "SUPERADMIN".equals(rol.code());
  }

  // ---------------------------------------------------------------------------
  // Auditoría
  // ---------------------------------------------------------------------------

  /**
   * Eliminación para los roles, cambio para lo que se cierra.
   *
   * <p><b>La membresía ya no aparece aquí</b> (05-09-2026): esta operación dejó de tocarla cuando
   * `RN-SP-015` se retiró, de modo que no hay nada suyo que registrar. Hasta entonces se anotaba
   * como eliminación —«quien deja de ser consumidor no tiene membresía, no que tuviera una que
   * terminó»— frente al cierre del superior, que <b>sobrevive con su fecha</b> porque dice a quién
   * se atribuía cada resultado comercial en cada periodo.
   *
   * <p>Las filas de eliminación quedan <b>sin motivo</b>: es la excepción del Art. V.13 que
   * `RN-SP-005` aplicó a las asociaciones, y el endpoint no lo pide.
   */
  private void auditar(
      User usuario,
      List<AssignableRole> retirados,
      List<AssignableRole> resultantes,
      boolean superiorCerrado,
      int sesionesRevocadas) {

    Map<String, Object> estadoRoles = new HashMap<>();
    estadoRoles.put("removed_roles", codigos(retirados));
    estadoRoles.put("roles", codigos(resultantes));

    auditoria.recordDeletion(
        new DeletionEvent(
            MODULO, "user_roles", usuario.getId(), DeletionType.ASSOCIATION, null, estadoRoles));

    if (superiorCerrado) {
      auditoria.recordChange(
          new ChangeEvent(
              MODULO,
              "user_supervisors",
              usuario.getId(),
              ChangeAction.UPDATE,
              Map.of("ended", true, "reason", "RN-SP-019")));
    }

    Map<String, Object> detalle = new HashMap<>();
    detalle.put("removed", codigos(retirados));
    detalle.put("roles", codigos(resultantes));
    detalle.put("revoked_sessions", sesionesRevocadas);

    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.USER_ROLES_REVOKED,
            Severity.ALTA,
            Outcome.SUCCESS,
            usuario.getId(),
            detalle));
  }

  private static List<String> codigos(List<AssignableRole> roles) {
    return new LinkedHashSet<>(roles.stream().map(AssignableRole::code).sorted().toList())
        .stream().toList();
  }
}
