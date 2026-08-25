package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.users.application.AssignSupervisorRequest;
import com.factech.nexus.modules.system.users.application.CommercialStructureResponse;
import com.factech.nexus.modules.system.users.domain.models.ChangeReason;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.models.UserStatus;
import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import com.factech.nexus.modules.system.users.domain.repository.RoleCatalog;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.modules.system.users.domain.repository.UserSupervisor;
import com.factech.nexus.modules.system.users.domain.security.CommercialStructure;
import com.factech.nexus.modules.system.users.domain.security.SelfOperationGuard;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
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
 * Establecer o cambiar el superior comercial (`RF-SP-041`).
 *
 * <p><b>Nunca un {@code UPDATE} del superior sobre una fila.</b> Cada cambio <b>cierra</b> el tramo
 * vigente con su fecha y <b>abre</b> otro, de modo que tras una reasignación quedan dos filas y no
 * una modificada. Sobrescribir destruiría el historial que dice a quién se atribuía cada resultado
 * comercial en cada periodo — y las comisiones ya liquidadas dejarían de poder justificarse.
 *
 * <p><b>El equipo se mueve con el reasignado.</b> Sus subordinados conservan a su superior; lo
 * único que cambia es de quién depende la rama entera. Una implementación que los arrastrara al
 * superior nuevo reorganizaría la empresa con cada cambio y <b>pasaría todas las demás pruebas</b>:
 * por eso `T-07` existe y verifica justamente que no ocurre nada.
 *
 * <p>Orden de verificación (`plan.md` §4):
 *
 * <ol>
 *   <li>Motivo con contenido — {@code 400}, y <b>antes de saber si habrá cambio</b>.
 *   <li>Ambas personas existen y no están eliminadas — {@code 404}.
 *   <li>El subordinado no es el propio actor (`RN-SP-017`) — {@code 409}.
 *   <li>Subordinado y superior no son la misma persona — {@code 409}.
 *   <li>El subordinado porta rol comercial y no es la cúspide — {@code 409}.
 *   <li>El superior está {@code ACTIVO} y porta el rol que exige `RN-SP-020` — {@code 409}.
 * </ol>
 *
 * <p>El paso 1 va el primero aunque no sepamos todavía si habrá cambio, y `FA-001` lo confirma:
 * exigir el motivo solo cuando resulte haber cambio obligaría a validar en dos momentos distintos
 * según el estado previo, que es la clase de condicional que acaba dejando pasar un caso.
 */
@Service
public class AssignSupervisorService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "user_supervisors";

  private final UserRepository usuarios;
  private final RoleCatalog roles;
  private final CommercialStructure estructura;
  private final AuthenticatedActor actor;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  @Autowired
  public AssignSupervisorService(
      UserRepository usuarios,
      RoleCatalog roles,
      CommercialStructure estructura,
      AuthenticatedActor actor,
      AuditWriter auditoria,
      UuidV7Generator ids) {
    this(usuarios, roles, estructura, actor, auditoria, ids, Clock.systemUTC());
  }

  AssignSupervisorService(
      UserRepository usuarios,
      RoleCatalog roles,
      CommercialStructure estructura,
      AuthenticatedActor actor,
      AuditWriter auditoria,
      UuidV7Generator ids,
      Clock reloj) {
    this.usuarios = usuarios;
    this.roles = roles;
    this.estructura = estructura;
    this.actor = actor;
    this.auditoria = auditoria;
    this.ids = ids;
    this.reloj = reloj;
  }

  @Transactional
  public CommercialStructureResponse assign(UUID userId, AssignSupervisorRequest peticion) {
    // 1. El motivo, antes que nada y antes de saber si habrá cambio.
    ChangeReason motivo = new ChangeReason(peticion.reason());

    // 2. El subordinado, BLOQUEADO: es lo que serializa dos reasignaciones
    //    simultáneas y evita que la unicidad parcial produzca un 500.
    User subordinado =
        usuarios
            .findNotDeletedByIdForUpdate(userId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "VAL-002", "No existe una persona con ese identificador."));

    User superior =
        usuarios
            .findNotDeletedById(peticion.supervisorId())
            .orElseThrow(
                () -> new ResourceNotFoundException("VAL-002", "El superior indicado no existe."));

    // 3. `RN-SP-017`. Aquí es un 409 y en `RF-SP-028` y `RF-SP-029` es un 403:
    //    los planes aprobados NO coinciden en el código, y la discrepancia se
    //    declara en §4.bis en lugar de resolverse por cuenta propia. La
    //    comparación y su razón viven en un solo sitio; el estado lo elige cada
    //    contrato.
    if (SelfOperationGuard.esSuPropiaCuenta(actor.id(), userId)) {
      throw conflicto(
          "RN-SP-017", "userId", "No puede reasignar el superior comercial de su propia cuenta.");
    }

    // 4. Nadie a su propio cargo. El esquema también lo impide
    //    (`ck_user_supervisors_no_self`), pero un 409 explica y un 500 no.
    if (userId.equals(peticion.supervisorId())) {
      throw conflicto("VAL-007", "supervisorId", "Nadie puede ser su propio superior comercial.");
    }

    // 5. El subordinado pertenece a la fuerza comercial y no es la cúspide.
    List<AssignableRole> rolesDelSubordinado = roles.findAllById(roles.roleIdsOf(userId));
    AssignableRole rango =
        estructura
            .rolDeMayorRango(rolesDelSubordinado)
            .orElseThrow(
                () ->
                    conflicto(
                        "VAL-003",
                        "userId",
                        "La persona no pertenece a la fuerza comercial: no tiene superior que"
                            + " asignar."));

    AssignableRole exigido =
        estructura
            .rolExigidoAlSuperior(rango)
            .orElseThrow(
                () ->
                    conflicto(
                        "VAL-004",
                        "userId",
                        "El rol '"
                            + rango.code()
                            + "' es la cúspide comercial y no declara superior."));

    // 6. El superior está activo y porta el rol que exige el orden de mando.
    if (superior.getStatus() != UserStatus.ACTIVO) {
      throw conflicto("VAL-006", "supervisorId", "El superior indicado no está activo.");
    }
    if (!roles.roleIdsOf(peticion.supervisorId()).contains(exigido.id())) {
      // El mensaje dice QUÉ ROL debería portar. Sin ese dato, quien recibe el
      // error no sabe a quién buscar.
      throw conflicto(
          "RN-SP-020",
          "supervisorId",
          "El superior debe portar el rol '"
              + exigido.code()
              + "', que es el rol padre inmediato de '"
              + rango.code()
              + "'.");
    }

    Optional<UserSupervisor> anterior = usuarios.findActiveSupervisor(userId);

    // `FA-001`: el mismo superior no cierra ni abre nada, y NO registra evento.
    // Partir el historial en dos tramos idénticos con una fecha de corte
    // inventada sería peor que no registrar: describiría un cambio que nadie hizo.
    if (anterior
        .map(previo -> previo.supervisorId().equals(peticion.supervisorId()))
        .orElse(false)) {
      return respuesta(subordinado, rango, anterior.orElse(null), null, null);
    }

    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    if (anterior.isPresent()) {
      usuarios.endSupervisor(userId, ahora);
    }
    usuarios.assignSupervisor(ids.next(), userId, peticion.supervisorId(), ahora);

    auditar(subordinado, anterior.orElse(null), superior, motivo);

    UserSupervisor vigente = usuarios.findActiveSupervisor(userId).orElseThrow();
    return respuesta(
        subordinado, rango, vigente, anterior.orElse(null), anterior.isPresent() ? ahora : null);
  }

  // ---------------------------------------------------------------------------

  private CommercialStructureResponse respuesta(
      User subordinado,
      AssignableRole rango,
      UserSupervisor vigente,
      UserSupervisor anterior,
      OffsetDateTime cerradoEn) {

    return new CommercialStructureResponse(
        new CommercialStructureResponse.Person(
            subordinado.getId(),
            subordinado.getUsername(),
            subordinado.getFirstName(),
            subordinado.getLastName(),
            rango.code(),
            subordinado.getStatus().name(),
            null),
        vigente == null ? null : persona(vigente, vigente.since()),
        anterior == null ? null : persona(anterior, null),
        cerradoEn,
        null);
  }

  private static CommercialStructureResponse.Person persona(
      UserSupervisor superior, OffsetDateTime desde) {
    return new CommercialStructureResponse.Person(
        superior.supervisorId(),
        superior.username(),
        superior.firstName(),
        superior.lastName(),
        superior.roleCode(),
        superior.status(),
        desde);
  }

  /**
   * <b>Un</b> evento de cambio, aunque la operación cierre un tramo y abra otro.
   *
   * <p>Dos eventos harían que cualquier recuento de reasignaciones contase el doble, y describirían
   * como dos decisiones lo que fue una.
   *
   * <p>El {@code entity_id} es el <b>subordinado</b> y no la fila de asignación: la pregunta que
   * alguien hará es «¿a quién ha reportado esta persona?», y con el identificador de la fila esa
   * consulta no se puede hacer sin conocer de antemano los tramos.
   *
   * <p><b>Ningún evento de seguridad</b> (`CA-SP-428`). Reasignar un superior no altera un solo
   * permiso de nadie: es estructura comercial, no control de acceso.
   */
  private void auditar(User subordinado, UserSupervisor anterior, User nuevo, ChangeReason motivo) {

    Map<String, Object> cambios = new HashMap<>();
    cambios.put("before", anterior == null ? null : anterior.username());
    cambios.put("after", nuevo.getUsername());
    cambios.put("reason", motivo.value());

    auditoria.recordChange(
        new ChangeEvent(
            MODULO,
            ENTIDAD,
            subordinado.getId(),
            anterior == null ? ChangeAction.CREATE : ChangeAction.UPDATE,
            cambios));
  }

  private static BusinessRuleException conflicto(String codigo, String campo, String mensaje) {
    return new BusinessRuleException(
        codigo, mensaje, List.of(new FieldError(campo, codigo, mensaje)));
  }
}
