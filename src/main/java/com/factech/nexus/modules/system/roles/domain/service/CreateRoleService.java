package com.factech.nexus.modules.system.roles.domain.service;

import com.factech.nexus.modules.system.permissions.application.PermissionItem;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.roles.application.CreateRoleCommand;
import com.factech.nexus.modules.system.roles.application.RoleResponse;
import com.factech.nexus.modules.system.roles.domain.models.Role;
import com.factech.nexus.modules.system.roles.domain.models.RoleCode;
import com.factech.nexus.modules.system.roles.domain.repository.PermissionCatalog;
import com.factech.nexus.modules.system.roles.domain.repository.RoleRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
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
 * Alta de un rol (`RF-SP-001` · `T-15`).
 *
 * <p><b>El orden de verificación es el contrato</b>, no una preferencia de implementación.
 * Determina qué error recibe una petición que incumple varias cosas a la vez, así que `plan.md` §4
 * lo fija y este método lo sigue al pie de la letra, deteniéndose en la primera que falla:
 *
 * <ol>
 *   <li>Unicidad de código y nombre (`EX-001` → {@code 409}).
 *   <li>Rol padre existente y activo (`EX-002` → {@code 422}).
 *   <li>Existencia de los permisos en el catálogo (`EX-005` → {@code 422}).
 *   <li>Contención en el padre (`EX-003` → {@code 409}).
 *   <li>Contención en el actor (`EX-004` → {@code 409}).
 * </ol>
 *
 * <p>Las tres últimas <b>no son evaluables</b> sin haber resuelto antes el padre y los permisos: el
 * orden no es preferencia, es dependencia. Las dos últimas viven en el agregado, no aquí, porque
 * son reglas de negocio y no orquestación.
 *
 * <p><b>{@code @Transactional} vive aquí</b>, en el caso de uso; nunca en el controlador ni en el
 * repositorio (`plan.md` §7).
 */
@Service
public class CreateRoleService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "roles";

  private final RoleRepository roles;
  private final PermissionCatalog catalogo;
  private final AuthenticatedActor actor;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  /**
   * Constructor de producción.
   *
   * <p>La anotación no es decorativa: Spring solo infiere el constructor cuando la clase declara
   * exactamente uno, y aquí hay dos —el segundo existe para que la prueba pueda fijar el reloj—.
   * Sin ella busca el constructor sin argumentos, no lo encuentra y el contexto no arranca.
   */
  @Autowired
  public CreateRoleService(
      RoleRepository roles,
      PermissionCatalog catalogo,
      AuthenticatedActor actor,
      AuditWriter auditoria,
      UuidV7Generator ids) {
    this(roles, catalogo, actor, auditoria, ids, Clock.systemUTC());
  }

  CreateRoleService(
      RoleRepository roles,
      PermissionCatalog catalogo,
      AuthenticatedActor actor,
      AuditWriter auditoria,
      UuidV7Generator ids,
      Clock reloj) {
    this.roles = roles;
    this.catalogo = catalogo;
    this.actor = actor;
    this.auditoria = auditoria;
    this.ids = ids;
    this.reloj = reloj;
  }

  /**
   * Registra el rol y deja constancia en los dos registros que exige `security.md` §8.1.
   *
   * @return el rol creado, con su padre y sus permisos resueltos
   */
  @Transactional
  public RoleResponse create(CreateRoleCommand comando) {
    RoleCode code = new RoleCode(comando.code());

    verificarUnicidad(code, comando.name());
    Role padre = resolverPadre(comando.parentRoleId());
    List<PermissionItem> permisos = resolverPermisos(comando.permissionIds());

    Role rol =
        Role.create(
            ids.next(),
            code,
            comando.name(),
            comando.description(),
            comando.roleType(),
            padre,
            permisos,
            actor.permissions(),
            OffsetDateTime.now(reloj));

    roles.save(rol);

    // En la MISMA transacción (Art. V.14): si el alta se revierte, su evento
    // también; si el evento falla, el alta falla.
    auditoria.recordChange(
        new ChangeEvent(
            MODULO, ENTIDAD, rol.getId(), ChangeAction.CREATE, estadoInicial(rol, permisos)));

    // Independiente y DESPUÉS del commit: emitido antes, una reversión dejaría
    // un evento SUCCESS de un rol que no existe, y ese evento no se puede
    // retirar porque su transacción ya cerró (`plan.md` §7).
    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.ROLE_CREATED,
            Severity.ALTA,
            Outcome.SUCCESS,
            null,
            Map.of(
                "roleId", rol.getId().toString(),
                "roleCode", rol.getCode().value(),
                "permissions", permisos.stream().map(PermissionItem::code).toList())));

    return RoleResponse.from(rol, padre, permisos);
  }

  /**
   * `EX-001`. La verificación previa existe <b>para poder dar un mensaje preciso</b> —cuál de los
   * dos está duplicado—; quien garantiza la unicidad es el índice único parcial, y su violación la
   * traduce el repositorio. La restricción decide; esto solo redacta.
   */
  private void verificarUnicidad(RoleCode code, String name) {
    if (roles.existsActiveCode(code)) {
      throw new BusinessRuleException(
          "RN-SEG-001",
          "Ya existe un rol con ese código.",
          List.of(new FieldError("code", "RN-SEG-001", "Ya existe un rol con ese código.")));
    }
    if (roles.existsActiveName(name)) {
      throw new BusinessRuleException(
          "RN-SEG-001",
          "Ya existe un rol con ese nombre.",
          List.of(new FieldError("name", "RN-SEG-001", "Ya existe un rol con ese nombre.")));
    }
  }

  /**
   * `EX-002`. Un rol <b>eliminado lógicamente se trata como inexistente</b> (`spec.md` §13), y uno
   * inactivo tampoco sirve: los tres casos —ausente, eliminado, inactivo— comparten respuesta
   * porque distinguirlos le diría a quien pregunta qué roles existen y en qué estado están, que es
   * información que este endpoint no tiene por qué revelar.
   *
   * <p>{@code 422} y no {@code 404}: el recurso de la ruta es la colección {@code /api/v1/roles},
   * que existe. Lo que no resuelve es una referencia del cuerpo.
   */
  private Role resolverPadre(UUID parentRoleId) {
    return roles
        .findById(parentRoleId)
        .filter(Role::isUsableAsParent)
        .orElseThrow(
            () ->
                new UnprocessableEntityException(
                    "EX-002",
                    "El rol padre no existe o no está activo.",
                    List.of(
                        new FieldError(
                            "parentRoleId",
                            "EX-002",
                            "El rol padre no existe o no está activo."))));
  }

  /**
   * `EX-005`. Enumera <b>todos</b> los permisos ausentes del catálogo, no el primero: la
   * especificación exige informar qué permisos no existen, y devolverlos de a uno convierte una
   * corrección en varias vueltas.
   */
  private List<PermissionItem> resolverPermisos(Set<UUID> solicitados) {
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
                        "EX-005",
                        "El permiso '" + id + "' no existe en el catálogo."))
            .toList();

    throw new UnprocessableEntityException(
        "EX-005", "Uno o más permisos no existen en el catálogo.", ausentes);
  }

  /**
   * Estado inicial completo para {@code changes}, no un diff con {@code before} en nulo
   * (`architecture.md` §6.6.2).
   *
   * <p>Los permisos viajan <b>por código</b> y dentro de este mismo evento: son parte del estado
   * inicial del agregado, y una fila de auditoría por permiso fragmentaría la línea de tiempo del
   * rol en tantas entradas como permisos tenga sin responder ninguna pregunta que el evento único
   * no responda.
   *
   * <p>Se usa un {@code HashMap} y no {@code Map.of} porque {@code description} y {@code
   * parentRoleId} pueden ser nulos, y las fábricas inmutables del JDK rechazan valores nulos.
   */
  private static Map<String, Object> estadoInicial(Role rol, List<PermissionItem> permisos) {
    Map<String, Object> estado = new HashMap<>();
    estado.put("code", rol.getCode().value());
    estado.put("name", rol.getName());
    estado.put("description", rol.getDescription());
    estado.put("role_type", rol.getRoleType().name());
    estado.put(
        "parent_role_id", rol.getParentRoleId() == null ? null : rol.getParentRoleId().toString());
    estado.put("status", rol.getStatus().name());
    estado.put("is_system", rol.isSystem());
    estado.put("permissions", permisos.stream().map(PermissionItem::code).toList());
    return estado;
  }
}
