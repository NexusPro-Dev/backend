package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.users.application.RegisterUserCommand;
import com.factech.nexus.modules.system.users.application.UserResponse;
import com.factech.nexus.modules.system.users.domain.models.Email;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.models.Username;
import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import com.factech.nexus.modules.system.users.domain.repository.RoleCatalog;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
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
import com.factech.nexus.shared.security.PasswordHasher;
import com.factech.nexus.shared.security.PasswordPolicy;
import java.time.Clock;
import java.time.OffsetDateTime;
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
 * Alta de una persona (`RF-SP-024`).
 *
 * <p><b>Es el caso de uso con más reglas del módulo</b>, y el orden en que se verifican es el
 * contrato: determina qué error recibe una petición que incumple varias cosas a la vez.
 *
 * <ol>
 *   <li>Unicidad de nombre de usuario y correo (`RN-SP-016` → {@code 409}).
 *   <li>Los roles existen y sirven (`EX-003` → {@code 422}).
 *   <li>Ningún rol excede los privilegios del actor (`RN-SEG-010` → {@code 409}).
 *   <li>Consumidor ⟺ membresía (`RN-SP-018` → {@code 409}).
 *   <li>Vendedor ⟺ superior (`RN-SP-019` → {@code 409}).
 *   <li>El superior porta el rol padre inmediato (`RN-SP-020` → {@code 409}).
 * </ol>
 *
 * <p>Las cuatro últimas <b>no son evaluables</b> sin haber resuelto antes los roles: el orden no es
 * preferencia, es dependencia.
 *
 * <p><b>Todo se escribe en una sola transacción</b> (Art. V.14). `CA-SP-373` y `CA-SP-397` lo
 * exigen de forma explícita: no existe un instante en que el consumidor esté sin membresía ni el
 * vendedor sin superior.
 */
@Service
public class RegisterUserService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "users";

  /**
   * Tope de saltos al buscar el rol vendedor de mayor rango.
   *
   * <p>La jerarquía de roles es acíclica por `RN-SEG-006` y {@code ck_roles_parent_not_self`}, de
   * modo que este límite no debería alcanzarse nunca. Existe porque un recorrido de punteros sin
   * tope convierte un defecto de datos en un cuelgue del servidor, y prefiero un rechazo a un hilo
   * bloqueado.
   */
  private static final int SALTOS_MAXIMOS = 32;

  private final UserRepository usuarios;
  private final RoleCatalog roles;
  private final AuthenticatedActor actor;
  private final PasswordPolicy politica;
  private final PasswordHasher hasher;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  /** Constructor de producción; el segundo existe para que la prueba pueda fijar el reloj. */
  @Autowired
  public RegisterUserService(
      UserRepository usuarios,
      RoleCatalog roles,
      AuthenticatedActor actor,
      PasswordPolicy politica,
      PasswordHasher hasher,
      AuditWriter auditoria,
      UuidV7Generator ids) {
    this(usuarios, roles, actor, politica, hasher, auditoria, ids, Clock.systemUTC());
  }

  RegisterUserService(
      UserRepository usuarios,
      RoleCatalog roles,
      AuthenticatedActor actor,
      PasswordPolicy politica,
      PasswordHasher hasher,
      AuditWriter auditoria,
      UuidV7Generator ids,
      Clock reloj) {
    this.usuarios = usuarios;
    this.roles = roles;
    this.actor = actor;
    this.politica = politica;
    this.hasher = hasher;
    this.auditoria = auditoria;
    this.ids = ids;
    this.reloj = reloj;
  }

  @Transactional
  public UserResponse register(RegisterUserCommand comando) {
    Username username = new Username(comando.username());
    Email email = new Email(comando.email());

    // La política se verifica ANTES de tocar la base: es una validación de
    // formato, y necesita el nombre de usuario y el correo ya normalizados para
    // comprobar que la contraseña no los contiene.
    politica.verificar(comando.password(), username.value(), email.value());

    verificarUnicidad(username, email);

    List<AssignableRole> concedidos = resolverRoles(comando.roleIds());
    verificarAlcanceDelActor(concedidos);
    verificarMembresia(concedidos, comando.membershipId());
    UUID superior = verificarSuperior(concedidos, comando.supervisorId());

    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    User usuario =
        usuarios.save(
            User.create(
                ids.next(),
                username,
                email,
                comando.firstName(),
                comando.lastName(),
                hasher.hash(comando.password()),
                comando.roleIds(),
                ahora));

    if (comando.membershipId() != null) {
      usuarios.assignMembership(usuario.getId(), comando.membershipId(), ahora);
    }
    if (superior != null) {
      usuarios.assignSupervisor(ids.next(), usuario.getId(), superior, ahora);
    }

    auditar(usuario, concedidos, comando.membershipId(), superior);

    return UserResponse.from(usuario, referencias(concedidos));
  }

  // ---------------------------------------------------------------------------
  // Reglas
  // ---------------------------------------------------------------------------

  /** `RN-SP-016`. La garantía la dan los índices únicos totales; esto solo redacta el mensaje. */
  private void verificarUnicidad(Username username, Email email) {
    if (usuarios.existsUsername(username)) {
      throw conflicto("RN-SP-016", "username", "Ese nombre de usuario ya está en uso.");
    }
    if (usuarios.existsEmail(email)) {
      throw conflicto("RN-SP-016", "email", "Ese correo ya está en uso.");
    }
  }

  /**
   * `EX-003` → {@code 422}. Enumera <b>todos</b> los roles que no sirven, no el primero.
   *
   * <p>Inexistente, eliminado e inactivo comparten respuesta: distinguirlos le diría a quien
   * pregunta qué roles existen y en qué estado están.
   */
  private List<AssignableRole> resolverRoles(Set<UUID> pedidos) {
    List<AssignableRole> encontrados = roles.findAllById(pedidos);

    Set<UUID> utiles = new LinkedHashSet<>();
    encontrados.stream()
        .filter(AssignableRole::usable)
        .map(AssignableRole::id)
        .forEach(utiles::add);

    List<FieldError> invalidos =
        pedidos.stream()
            .filter(id -> !utiles.contains(id))
            .map(
                id ->
                    new FieldError(
                        "roleIds", "EX-003", "El rol '" + id + "' no existe o no está activo."))
            .toList();

    if (!invalidos.isEmpty()) {
      throw new UnprocessableEntityException(
          "EX-003", "Uno o más roles no existen o no están activos.", invalidos);
    }
    return encontrados;
  }

  /**
   * `RN-SEG-010` → {@code 409}: nadie concede permisos que no posee.
   *
   * <p>Se compara <b>permiso a permiso</b> y no rol a rol, que es como `RF-SP-001` y `RF-SP-005` ya
   * lo resuelven: dos roles distintos pueden conceder lo mismo, y comparar identificadores de rol
   * rechazaría concesiones legítimas.
   *
   * <p><b>El actor no necesita portar el rol, sino sus permisos.</b> Un administrador puede
   * conceder `CONTABILIDAD` sin ser contable, siempre que posea todo lo que ese rol declara.
   */
  private void verificarAlcanceDelActor(List<AssignableRole> concedidos) {
    Set<String> delActor = actor.permissions();

    List<FieldError> excedidos =
        concedidos.stream()
            .filter(rol -> !delActor.containsAll(rol.permissionCodes()))
            .map(
                rol ->
                    new FieldError(
                        "roleIds",
                        "RN-SEG-010",
                        "El rol '" + rol.code() + "' concede permisos que usted no posee."))
            .toList();

    if (!excedidos.isEmpty()) {
      throw new BusinessRuleException(
          "RN-SEG-010", "No puede conceder roles que exceden sus propios permisos.", excedidos);
    }
  }

  /**
   * `RN-SP-018` → {@code 409}, y es <b>condicional en los dos sentidos</b>.
   *
   * <p>El rol de consumidor y la membresía son inseparables: no existe el estado «consumidor sin
   * nivel». Y la recíproca importa igual — indicar una membresía sin el rol que la exige no es un
   * dato que se ignore, es un `409`: sin ese rechazo, una petición copiada de otra dejaría una
   * membresía colgando de quien no es consumidor.
   */
  private void verificarMembresia(List<AssignableRole> concedidos, UUID membresia) {
    boolean hayConsumidor = concedidos.stream().anyMatch(AssignableRole::esConsumidor);

    if (hayConsumidor && membresia == null) {
      throw conflicto(
          "RN-SP-018",
          "membershipId",
          "Todo consumidor debe tener membresía: indíquela en esta misma operación.");
    }
    if (!hayConsumidor && membresia != null) {
      throw conflicto(
          "RN-SP-018",
          "membershipId",
          "No se puede asignar una membresía a quien no porta ningún rol de consumidor.");
    }
  }

  /**
   * `RN-SP-019` y `RN-SP-020` → {@code 409}.
   *
   * <p>Todo vendedor declara superior <b>salvo la cúspide</b>: quien porta el rol vendedor de mayor
   * rango, aquel cuyo rol padre ya no es vendedor. Y el superior debe portar exactamente ese rol
   * padre inmediato — no un ancestro cualquiera—, que es lo que hace que la cadena de personas
   * herede la aciclicidad de la cadena de roles sin necesitar una regla anti-ciclos propia.
   *
   * @return el superior que hay que registrar, o {@code null} si no corresponde ninguno
   */
  private UUID verificarSuperior(List<AssignableRole> concedidos, UUID supervisorId) {
    Optional<AssignableRole> mayorRango = rolVendedorDeMayorRango(concedidos);

    if (mayorRango.isEmpty()) {
      if (supervisorId != null) {
        throw conflicto(
            "RN-SP-019",
            "supervisorId",
            "No se puede asignar un superior comercial a quien no porta ningún rol de vendedor.");
      }
      return null;
    }

    AssignableRole rol = mayorRango.get();
    Optional<AssignableRole> padre = roles.findById(rol.parentRoleId());
    boolean esCuspide = padre.isEmpty() || !padre.get().esVendedor();

    if (esCuspide) {
      // La cúspide de la fuerza comercial no reporta a nadie. Indicar superior
      // aquí es tan incorrecto como omitirlo abajo.
      if (supervisorId != null) {
        throw conflicto(
            "RN-SP-019",
            "supervisorId",
            "El rol '" + rol.code() + "' es la cúspide comercial y no declara superior.");
      }
      return null;
    }

    if (supervisorId == null) {
      throw conflicto(
          "RN-SP-019",
          "supervisorId",
          "Todo vendedor debe tener superior comercial: indíquelo en esta misma operación.");
    }

    // El superior tiene que existir, no estar eliminado y estar ACTIVO: una
    // persona dada de baja no puede quedar a cargo de nadie, porque `RN-SP-022`
    // impediría después retirarle el acceso sin reasignar a su equipo — se
    // crearía el bloqueo desde el propio alta.
    if (usuarios.findUsableById(supervisorId).isEmpty()) {
      throw conflicto(
          "RN-SP-020", "supervisorId", "El superior indicado no existe o no está activo.");
    }

    AssignableRole exigido = padre.get();
    if (!roles.roleIdsOf(supervisorId).contains(exigido.id())) {
      throw conflicto(
          "RN-SP-020",
          "supervisorId",
          "El superior debe portar el rol '"
              + exigido.code()
              + "', que es el rol padre inmediato de '"
              + rol.code()
              + "'.");
    }
    return supervisorId;
  }

  /**
   * El rol vendedor de mayor rango entre los concedidos.
   *
   * <p>Es aquel que <b>no desciende de ningún otro</b> de los roles vendedores de la misma persona.
   * Se mira así y no «el primero» porque <b>un ascenso cambia con quién debe cumplirse la
   * regla</b>: quien pasa de agente a director deja de poder estar a cargo de un director.
   *
   * <p><b>Hueco declarado:</b> si la persona portara dos roles vendedores en ramas distintas
   * —ninguno ancestro del otro— habría dos candidatos y las reglas no dicen cuál manda. Se toma el
   * primero por código para que el resultado sea determinista, y queda anotado en `tasks.md`: el
   * catálogo aprobado es una cadena lineal, de modo que hoy el caso no puede darse.
   */
  private Optional<AssignableRole> rolVendedorDeMayorRango(List<AssignableRole> concedidos) {
    List<AssignableRole> vendedores =
        concedidos.stream().filter(AssignableRole::esVendedor).sorted(porCodigo()).toList();

    if (vendedores.size() <= 1) {
      return vendedores.stream().findFirst();
    }
    Set<UUID> deLaPersona =
        new LinkedHashSet<>(vendedores.stream().map(AssignableRole::id).toList());

    return vendedores.stream().filter(rol -> !desciendeDeAlguno(rol, deLaPersona)).findFirst();
  }

  /** Recorre el árbol hacia arriba con tope: un dato corrupto no debe colgar un hilo. */
  private boolean desciendeDeAlguno(AssignableRole rol, Set<UUID> candidatos) {
    UUID actualPadre = rol.parentRoleId();
    for (int salto = 0; salto < SALTOS_MAXIMOS && actualPadre != null; salto++) {
      if (candidatos.contains(actualPadre)) {
        return true;
      }
      Optional<AssignableRole> padre = roles.findById(actualPadre);
      if (padre.isEmpty()) {
        return false;
      }
      actualPadre = padre.get().parentRoleId();
    }
    return false;
  }

  private static java.util.Comparator<AssignableRole> porCodigo() {
    return java.util.Comparator.comparing(AssignableRole::code);
  }

  // ---------------------------------------------------------------------------
  // Auditoría
  // ---------------------------------------------------------------------------

  /**
   * Dos eventos, y <b>uno solo de seguridad</b>.
   *
   * <p>Cuando el alta concede roles podría argumentarse que ocurre también «asignación de roles a
   * un usuario». No se emiten los dos: es una sola operación atómica, y dos eventos harían que
   * cualquier recuento de asignaciones contase de más. Los roles concedidos viajan en el detalle.
   *
   * <p><b>El de seguridad se engancha al commit y el de cambio no.</b> Un evento de seguridad que
   * documenta una creación revertida es peor que ninguno: en una investigación, es un dato falso.
   */
  private void auditar(
      User usuario, List<AssignableRole> concedidos, UUID membresia, UUID superior) {

    Map<String, Object> estado = new HashMap<>();
    estado.put("username", usuario.getUsername());
    // El correo NORMALIZADO, que es lo que quedó en la tabla. Cómo llegó
    // exactamente la petición es asunto de `request_log` (Art. XV.3).
    estado.put("email", usuario.getEmail());
    estado.put("first_name", usuario.getFirstName());
    estado.put("last_name", usuario.getLastName());
    estado.put("status", usuario.getStatus().name());
    estado.put("must_change_password", usuario.isMustChangePassword());
    estado.put("roles", codigos(concedidos));
    estado.put("membership_id", membresia == null ? null : membresia.toString());
    estado.put("supervisor_id", superior == null ? null : superior.toString());
    // Ningún campo derivado de la credencial, ni siquiera su longitud (Art. IV.8).

    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, usuario.getId(), ChangeAction.CREATE, estado));

    auditoria.recordSecurityAfterCommit(
        new SecurityEvent(
            SecurityEventType.USER_CREATED,
            Severity.ALTA,
            Outcome.SUCCESS,
            usuario.getId(),
            Map.of("username", usuario.getUsername(), "roles", codigos(concedidos))));
  }

  private static List<String> codigos(List<AssignableRole> roles) {
    return roles.stream().map(AssignableRole::code).sorted().toList();
  }

  private static List<UserResponse.RoleRef> referencias(List<AssignableRole> roles) {
    return roles.stream()
        .sorted(porCodigo())
        .map(rol -> new UserResponse.RoleRef(rol.id(), rol.code(), rol.name()))
        .toList();
  }

  private static BusinessRuleException conflicto(String codigo, String campo, String mensaje) {
    return new BusinessRuleException(
        codigo, mensaje, List.of(new FieldError(campo, codigo, mensaje)));
  }
}
