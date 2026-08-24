package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.users.application.AssignRolesRequest;
import com.factech.nexus.modules.system.users.application.UserResponse;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import com.factech.nexus.modules.system.users.domain.repository.MembershipCatalog;
import com.factech.nexus.modules.system.users.domain.repository.RoleCatalog;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.modules.system.users.domain.security.CommercialStructure;
import com.factech.nexus.modules.system.users.domain.security.ConsumerStatus;
import com.factech.nexus.modules.system.users.domain.security.PrivilegeContainment;
import com.factech.nexus.modules.system.users.domain.security.RoleAssignment;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asignar roles a una persona (`RF-SP-030`).
 *
 * <p><b>Es aditiva, nunca un reemplazo.</b> La operación no representa el estado final del
 * conjunto: por eso es un {@code POST} sobre un subrecurso y no un {@code PUT} sobre la lista. Un
 * reemplazo haría retiros implícitos que se saltarían `RN-SP-001`, `RN-SP-015` y `RN-SP-022`, es
 * decir, tres reglas cuyo incumplimiento nadie vería.
 *
 * <p>El orden de verificación es el contrato (`plan.md` §4):
 *
 * <ol>
 *   <li>La persona existe y no está eliminada — {@code 404}.
 *   <li>Todos los roles existen y no están eliminados — {@code 422} `EX-002`.
 *   <li>Ninguno está inactivo — {@code 422} `EX-003`.
 *   <li>Ninguno excede los permisos del actor (`RN-SEG-010`) — {@code 409}.
 *   <li>Coherencia de la membresía (`RN-SP-018`) — {@code 422}.
 *   <li>Coherencia del superior (`RN-SP-019`) — {@code 422}.
 *   <li>El superior existe, está activo y porta el rol padre inmediato (`RN-SP-020`) — {@code 422}.
 * </ol>
 *
 * <p>Los pasos 5 a 7 <b>no son evaluables</b> antes de resolver los roles: hasta saber cuáles son y
 * de qué clase, no se puede saber si la persona termina siendo consumidor o vendedor. El orden es
 * dependencia, no preferencia. El paso 4 va antes que ellos porque <b>un rol fuera del alcance del
 * actor debe rechazarse aunque además falte la membresía</b>: el intento de escalada es lo que más
 * importa registrar.
 *
 * <p><b>Todo se escribe en una sola transacción.</b> No existe un instante en que el consumidor
 * esté sin membresía ni el vendedor sin superior.
 */
@Service
public class AssignUserRolesService {

  private static final String MODULO = "SP";

  private final UserRepository usuarios;
  private final RoleCatalog roles;
  private final MembershipCatalog membresias;
  private final CommercialStructure estructura;
  private final AuthenticatedActor actor;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  @Autowired
  public AssignUserRolesService(
      UserRepository usuarios,
      RoleCatalog roles,
      MembershipCatalog membresias,
      CommercialStructure estructura,
      AuthenticatedActor actor,
      AuditWriter auditoria,
      UuidV7Generator ids) {
    this(usuarios, roles, membresias, estructura, actor, auditoria, ids, Clock.systemUTC());
  }

  AssignUserRolesService(
      UserRepository usuarios,
      RoleCatalog roles,
      MembershipCatalog membresias,
      CommercialStructure estructura,
      AuthenticatedActor actor,
      AuditWriter auditoria,
      UuidV7Generator ids,
      Clock reloj) {
    this.usuarios = usuarios;
    this.roles = roles;
    this.membresias = membresias;
    this.estructura = estructura;
    this.actor = actor;
    this.auditoria = auditoria;
    this.ids = ids;
    this.reloj = reloj;
  }

  @Transactional
  public UserResponse assign(UUID userId, AssignRolesRequest peticion) {
    // 1. La persona. No se exige que esté ACTIVA: administrar los roles de una
    //    cuenta suspendida es legítimo, y exigirlo la volvería inadministrable.
    User usuario =
        usuarios
            .findNotDeletedById(userId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "VAL-006", "No existe una persona con ese identificador."));

    Set<UUID> actuales = roles.roleIdsOf(userId);
    Set<UUID> nuevos = RoleAssignment.aAgregar(actuales, peticion.roleIds());

    // 2 y 3. Los roles pedidos, con las dos causas separadas.
    List<AssignableRole> pedidos = resolverRoles(peticion.roleIds());

    // 4. `RN-SEG-010`, antes que cualquier regla de coherencia.
    verificarAlcanceDelActor(pedidos);

    Set<UUID> resultantes = RoleAssignment.resultado(actuales, nuevos);
    List<AssignableRole> catalogoResultante = roles.findAllById(resultantes);
    List<AssignableRole> catalogoPrevio = roles.findAllById(actuales);

    // 5. `RN-SP-018` — consumidor ⟺ membresía, condicional en los dos sentidos.
    boolean membresiaNueva =
        verificarMembresia(userId, catalogoPrevio, catalogoResultante, peticion);

    // 6 y 7. `RN-SP-019` y `RN-SP-020`.
    UUID superiorNuevo =
        verificarSuperior(userId, catalogoPrevio, catalogoResultante, peticion.supervisorId());

    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    if (!nuevos.isEmpty()) {
      usuarios.addRoles(userId, nuevos);
    }
    if (membresiaNueva) {
      usuarios.assignMembership(
          userId, peticion.membershipId(), peticion.membershipEndsAt(), ahora);
    }
    if (superiorNuevo != null) {
      usuarios.assignSupervisor(ids.next(), userId, superiorNuevo, ahora);
    }

    if (!nuevos.isEmpty() || membresiaNueva || superiorNuevo != null) {
      auditar(
          usuario,
          catalogoResultante,
          nuevos,
          membresiaNueva ? peticion.membershipId() : null,
          superiorNuevo);
    }

    return UserResponses.de(usuario, catalogoResultante, usuarios, userId);
  }

  // ---------------------------------------------------------------------------
  // Reglas
  // ---------------------------------------------------------------------------

  /**
   * `EX-002` y `EX-003`, <b>separados y enumerando todos los infractores</b>.
   *
   * <p>El inexistente y el eliminado comparten respuesta porque son lo mismo desde fuera: el
   * identificador no designa un rol asignable. El inactivo es otra cosa y lleva su propio código —
   * quien recibe el rechazo tiene que poder distinguir «corrija el identificador» de «active el
   * rol», y en esta operación ya posee el permiso que le permitiría consultarlo.
   *
   * <p>Se comprueban en dos pasadas y no en una: si un rol no existe y otro está inactivo, el
   * rechazo es `EX-002`, que es el que declara el orden del plan.
   */
  private List<AssignableRole> resolverRoles(List<UUID> pedidos) {
    Set<UUID> conjunto = new LinkedHashSet<>(pedidos);
    List<AssignableRole> encontrados = roles.findAllById(conjunto);

    Map<UUID, AssignableRole> porId = new HashMap<>();
    encontrados.forEach(rol -> porId.put(rol.id(), rol));

    List<FieldError> inexistentes =
        pedidos.stream()
            .filter(id -> !porId.containsKey(id) || porId.get(id).deleted())
            .map(id -> new FieldError("roleIds", "EX-002", "El rol '" + id + "' no existe."))
            .toList();

    if (!inexistentes.isEmpty()) {
      throw new UnprocessableEntityException("EX-002", "Uno o más roles no existen.", inexistentes);
    }

    List<FieldError> inactivos =
        pedidos.stream()
            .map(porId::get)
            .filter(rol -> !rol.active())
            .map(
                rol ->
                    new FieldError(
                        "roleIds", "EX-003", "El rol '" + rol.code() + "' está inactivo."))
            .toList();

    if (!inactivos.isEmpty()) {
      throw new UnprocessableEntityException(
          "EX-003", "Uno o más roles están inactivos.", inactivos);
    }
    return encontrados;
  }

  /** `RN-SEG-010` → {@code 409}. La comprobación vive en un solo sitio (`PrivilegeContainment`). */
  private void verificarAlcanceDelActor(List<AssignableRole> pedidos) {
    List<AssignableRole> excedidos = PrivilegeContainment.excesos(pedidos, actor.permissions());

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
          "RN-SEG-010", "No puede conceder roles que exceden sus propios permisos.", detalle);
    }
  }

  /**
   * `RN-SP-018` → {@code 422}, evaluada sobre el <b>estado resultante</b>.
   *
   * <p>La pregunta no es «¿se está concediendo un rol de consumidor?» sino «¿termina siendo
   * consumidor y sin membresía?». La diferencia se ve al conceder un segundo rol de consumidor a
   * quien ya tiene membresía: exigirla otra vez sería absurdo, e ignorarlo sin más dejaría pasar el
   * caso en que sí falta.
   *
   * @return si hay que escribir la membresía indicada
   */
  private boolean verificarMembresia(
      UUID userId,
      List<AssignableRole> antes,
      List<AssignableRole> despues,
      AssignRolesRequest peticion) {

    boolean seraConsumidor = ConsumerStatus.esConsumidor(despues);
    boolean yaTieneMembresia = usuarios.findMembership(userId).isPresent();
    UUID indicada = peticion.membershipId();

    if (peticion.membershipEndsAt() != null && indicada == null) {
      throw noProcesable(
          "EX-006", "membershipEndsAt", "La vigencia solo se admite acompañando a una membresía.");
    }

    if (!seraConsumidor) {
      if (indicada != null) {
        throw noProcesable(
            "EX-006",
            "membershipId",
            "No se puede asignar una membresía a quien no portará ningún rol de consumidor.");
      }
      return false;
    }

    if (yaTieneMembresia) {
      // Ya es consumidor con membresía. Cambiarla es `RF-SP-032`, no esta
      // operación: admitirlo aquí sería una edición encubierta y sin su permiso.
      if (indicada != null) {
        throw noProcesable(
            "EX-006",
            "membershipId",
            "La persona ya tiene membresía. Para cambiarla use la operación de membresía.");
      }
      return false;
    }

    boolean eraConsumidor = ConsumerStatus.esConsumidor(antes);
    if (indicada == null) {
      throw noProcesable(
          "RN-SP-018",
          "membershipId",
          eraConsumidor
              ? "La persona es consumidor y no tiene membresía: indíquela en esta misma operación."
              : "Todo consumidor debe tener membresía: indíquela en esta misma operación.");
    }
    if (membresias.find(indicada).isEmpty()) {
      throw noProcesable("EX-006", "membershipId", "La membresía indicada no existe.");
    }
    return true;
  }

  /**
   * `RN-SP-019` y `RN-SP-020` → {@code 422}, con la <b>comparación de rango</b> que distingue el
   * ascenso.
   *
   * <p>Aquí está lo que el alta no puede tener. Añadir `AGENTE` a quien ya es `DIRECTOR` no cambia
   * nada: sigue reportando a quien reportaba. Pero <b>ascender</b> a alguien de agente a director
   * sí cambia con quién debe cumplirse la regla, porque un director no puede estar a cargo de otro
   * director. Por eso se compara el rango antes y después, y no se mira solo si se concede algún
   * rol vendedor.
   *
   * @return el superior que hay que registrar, o {@code null} si no corresponde ninguno
   */
  private UUID verificarSuperior(
      UUID userId, List<AssignableRole> antes, List<AssignableRole> despues, UUID supervisorId) {

    Optional<AssignableRole> rangoDespues = estructura.rolDeMayorRango(despues);

    if (rangoDespues.isEmpty()) {
      if (supervisorId != null) {
        throw noProcesable(
            "EX-008",
            "supervisorId",
            "No se puede asignar un superior comercial a quien no portará ningún rol de vendedor.");
      }
      return null;
    }

    AssignableRole rol = rangoDespues.get();
    Optional<AssignableRole> exigido = estructura.rolExigidoAlSuperior(rol);

    if (exigido.isEmpty()) {
      // Cúspide comercial: no reporta a nadie. Si venía de más abajo y tenía
      // superior, `RF-SP-041` es quien lo cierra — esta operación no retira.
      if (supervisorId != null) {
        throw noProcesable(
            "EX-008",
            "supervisorId",
            "El rol '" + rol.code() + "' es la cúspide comercial y no declara superior.");
      }
      return null;
    }

    boolean cambiaElRango = estructura.cambiaElRango(antes, despues);
    boolean tieneSuperior = usuarios.findActiveSupervisor(userId).isPresent();

    if (!cambiaElRango && tieneSuperior) {
      // Asignación lateral sobre quien ya reporta correctamente: nada que hacer.
      if (supervisorId != null) {
        throw noProcesable(
            "EX-008",
            "supervisorId",
            "La persona ya tiene superior comercial y su rango no cambia con esta operación.");
      }
      return null;
    }

    if (supervisorId == null) {
      throw noProcesable(
          "RN-SP-019",
          "supervisorId",
          cambiaElRango && tieneSuperior
              ? "El cambio de rango exige declarar de nuevo el superior comercial: el anterior"
                  + " puede haber dejado de ser admisible."
              : "Todo vendedor debe tener superior comercial: indíquelo en esta misma operación.");
    }

    if (supervisorId.equals(userId)) {
      throw noProcesable("VAL-007", "supervisorId", "Nadie puede ser su propio superior.");
    }
    if (usuarios.findUsableById(supervisorId).isEmpty()) {
      throw noProcesable(
          "VAL-007", "supervisorId", "El superior indicado no existe o no está activo.");
    }
    if (!roles.roleIdsOf(supervisorId).contains(exigido.get().id())) {
      throw noProcesable(
          "RN-SP-020",
          "supervisorId",
          "El superior debe portar el rol '"
              + exigido.get().code()
              + "', que es el rol padre inmediato de '"
              + rol.code()
              + "'.");
    }

    if (tieneSuperior) {
      // El ascenso cierra la asignación anterior y abre otra: la fila cerrada
      // dice a quién se atribuía cada resultado antes del ascenso.
      usuarios.endSupervisor(userId, OffsetDateTime.now(reloj));
    }
    return supervisorId;
  }

  // ---------------------------------------------------------------------------
  // Auditoría
  // ---------------------------------------------------------------------------

  /**
   * Hasta <b>tres</b> eventos de cambio y <b>uno</b> de seguridad, todos bajo el mismo {@code
   * correlation_id}.
   *
   * <p>Tres y no uno porque son tres entidades distintas —la asignación de roles, la membresía y el
   * superior— y la auditoría se consulta por entidad. Que compartan correlación es lo que permite
   * recuperar la operación entera; fundirlos en un solo evento haría imposible responder «qué le
   * pasó a la membresía de esta persona» sin leer eventos de otra cosa.
   *
   * <p>El de seguridad se engancha al commit: un evento que documenta una escalada de privilegios
   * <b>revertida</b> es, en una investigación, un dato falso.
   */
  private void auditar(
      User usuario,
      List<AssignableRole> resultantes,
      Set<UUID> agregados,
      UUID membresia,
      UUID superior) {

    if (!agregados.isEmpty()) {
      List<String> codigosAgregados =
          resultantes.stream()
              .filter(rol -> agregados.contains(rol.id()))
              .map(AssignableRole::code)
              .sorted()
              .toList();

      Map<String, Object> cambio = new HashMap<>();
      cambio.put("added_roles", codigosAgregados);
      cambio.put("roles", codigos(resultantes));
      auditoria.recordChange(
          new ChangeEvent(MODULO, "user_roles", usuario.getId(), ChangeAction.UPDATE, cambio));
    }

    if (membresia != null) {
      auditoria.recordChange(
          new ChangeEvent(
              MODULO,
              "user_memberships",
              usuario.getId(),
              ChangeAction.CREATE,
              Map.of("membership_id", membresia.toString())));
    }

    if (superior != null) {
      auditoria.recordChange(
          new ChangeEvent(
              MODULO,
              "user_supervisors",
              usuario.getId(),
              ChangeAction.CREATE,
              Map.of("supervisor_id", superior.toString())));
    }

    Map<String, Object> detalle = new HashMap<>();
    detalle.put("roles", codigos(resultantes));
    detalle.put("added", agregados.size());

    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.USER_ROLES_ASSIGNED,
            Severity.ALTA,
            Outcome.SUCCESS,
            usuario.getId(),
            detalle));
  }

  private static List<String> codigos(List<AssignableRole> roles) {
    return roles.stream().map(AssignableRole::code).sorted().toList();
  }

  private static UnprocessableEntityException noProcesable(
      String codigo, String campo, String mensaje) {
    List<FieldError> detalle = new ArrayList<>();
    detalle.add(new FieldError(campo, codigo, mensaje));
    return new UnprocessableEntityException(codigo, mensaje, detalle);
  }
}
