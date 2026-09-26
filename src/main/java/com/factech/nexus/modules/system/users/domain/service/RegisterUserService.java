package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.users.application.RegisterUserCommand;
import com.factech.nexus.modules.system.users.application.UserResponse;
import com.factech.nexus.modules.system.users.domain.models.DocumentIdentity;
import com.factech.nexus.modules.system.users.domain.models.Email;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.models.Username;
import com.factech.nexus.modules.system.users.domain.repository.AssignableCountry;
import com.factech.nexus.modules.system.users.domain.repository.AssignableDocumentType;
import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import com.factech.nexus.modules.system.users.domain.repository.MembershipCatalog;
import com.factech.nexus.modules.system.users.domain.repository.RoleCatalog;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.modules.system.users.domain.security.CommercialStructure;
import com.factech.nexus.modules.system.users.domain.security.PrivilegeContainment;
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
 *   <li>Al menos un rol informado (`RN-SP-023` → {@code 400}, en el DTO).
 *   <li>Unicidad de nombre de usuario y correo (`RN-SP-016` → {@code 409}).
 *   <li>Los roles existen y sirven (`EX-003` → {@code 422}).
 *   <li>Ningún rol excede los privilegios del actor (`RN-SEG-010` → {@code 409}).
 *   <li>Toda persona nace con nivel (`RN-SP-018`): la indicada, o el suelo. <b>Ya no hay caso de
 *       rechazo</b> desde el 05-09-2026 — la comprobación de los dos sentidos murió con
 *       `RN-SP-013`.
 *   <li>Vendedor ⟺ superior (`RN-SP-019` → {@code 409}).
 *   <li>El superior porta el rol padre inmediato (`RN-SP-020` → {@code 409}).
 *   <li>El país existe ({@code 422}) y está activo ({@code 409}) — `RN-SP-034`, 07-09-2026.
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

  private final UserRepository usuarios;
  private final RoleCatalog roles;
  private final MembershipCatalog membresias;
  private final AssignableCountry paises;
  private final AssignableDocumentType documentos;
  private final CommercialStructure estructura;
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
      MembershipCatalog membresias,
      CommercialStructure estructura,
      AuthenticatedActor actor,
      PasswordPolicy politica,
      PasswordHasher hasher,
      AuditWriter auditoria,
      UuidV7Generator ids,
      AssignableCountry paises,
      AssignableDocumentType documentos) {
    this(
        usuarios,
        roles,
        membresias,
        estructura,
        actor,
        politica,
        hasher,
        auditoria,
        ids,
        paises,
        documentos,
        Clock.systemUTC());
  }

  RegisterUserService(
      UserRepository usuarios,
      RoleCatalog roles,
      MembershipCatalog membresias,
      CommercialStructure estructura,
      AuthenticatedActor actor,
      PasswordPolicy politica,
      PasswordHasher hasher,
      AuditWriter auditoria,
      UuidV7Generator ids,
      AssignableCountry paises,
      AssignableDocumentType documentos,
      Clock reloj) {
    this.usuarios = usuarios;
    this.roles = roles;
    this.membresias = membresias;
    this.paises = paises;
    this.documentos = documentos;
    this.estructura = estructura;
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

    // `RN-SP-018`, reescrita el 05-09-2026: TODA PERSONA NACE CON NIVEL. Si el
    // alta no indica membresía, la de arranque. Ya no se comprueba nada sobre
    // ella: la comprobación de los dos sentidos —consumidor sin membresía y
    // membresía sin consumidor— murió con `RN-SP-013`.
    UUID membresia =
        comando.membershipId() != null ? comando.membershipId() : membresias.floor().id();

    UUID superior = verificarSuperior(concedidos, comando.supervisorId());

    // `RN-SP-034`. VA DESPUÉS DE LOS ROLES Y NO ANTES: es una consulta más a la
    // base y no aporta nada adelantarla. Y va ANTES de la escritura porque
    // `fk_users_country` no distingue «no existe» de «está inactivo» — dejarlo
    // al motor daría un `500` sobre una regla de negocio.
    verificarPais(comando.countryId());

    // `RN-SP-035`. Va después del país y antes de la escritura, por lo mismo:
    // `fk_users_document_type` no distingue «no existe» de «está inactivo», y
    // `uq_users_document` daría un `500` sobre una regla de negocio.
    verificarDocumento(comando.documento());

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
                comando.countryId(),
                comando.documento(),
                comando.contacto(),
                comando.roleIds(),
                ahora));

    // SIN CONDICIÓN. No existe un instante en que la persona esté escrita y sin
    // nivel — ni entre estas dos sentencias, porque comparten transacción.
    //
    // SIN PRODUCTO, y es uno de los dos únicos casos del sistema (`RN-SP-056`):
    // el suelo no se compra, se recibe. Inventarle un producto obligaría a tener
    // un «producto BECA» en el catálogo, que sería comprable y habría que excluir
    // de la oferta a mano.
    usuarios.grantProduct(
        new UserRepository.ProductGrant(
            ids.next(), usuario.getId(), null, membresia, null, null, ahora, null));

    if (superior != null) {
      usuarios.assignSupervisor(ids.next(), usuario.getId(), superior, ahora);
    }

    // SE AUDITA LA CONCEDIDA DE VERDAD, no la pedida: si el alta no indicó
    // ninguna, el registro tiene que decir que nació en el suelo y no que nació
    // sin nada.
    auditar(usuario, concedidos, membresia, superior);

    return UserResponses.de(usuario, concedidos, usuarios, paises, documentos, usuario.getId());
  }

  // ---------------------------------------------------------------------------
  // Reglas
  // ---------------------------------------------------------------------------

  /**
   * `RN-SP-034` → {@code 422} si no existe, {@code 409} si está inactivo.
   *
   * <p><b>Los dos casos NO comparten respuesta</b>, al revés que los roles de `EX-003` y que el
   * producto y el vendedor del registro público. La diferencia es a quién se le habla: aquí quien
   * pregunta ya tiene {@code users:create} y puede ver el catálogo entero de todos modos, de modo
   * que distinguirlos no revela nada — y sí le dice qué hacer. Ante el {@code 422} vuelve a leer el
   * catálogo, porque su selector está desincronizado; ante el {@code 409} sabe que alguien retiró
   * ese país de la circulación, que es una conversación y no un error de la petición.
   *
   * <p><b>No se ofrece dar de alta el país sobre la marcha.</b> El catálogo es de `RF-SP-020`, y
   * `RN-SP-009` hace que un país registrado por error no se pueda corregir jamás: crearlo desde
   * aquí convertiría una errata en un alta permanente.
   */
  private void verificarPais(UUID countryId) {
    var pais =
        paises
            .find(countryId)
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "EX-009",
                        "El país indicado no existe.",
                        List.of(
                            new FieldError(
                                "countryId",
                                "EX-009",
                                "El país '" + countryId + "' no existe en el catálogo."))));

    if (!pais.active()) {
      throw conflicto(
          "RN-SP-034",
          "countryId",
          "El país '" + pais.code() + "' está inactivo y no se puede asignar.");
    }
  }

  /**
   * `RN-SP-035` → {@code 422} si el tipo no existe, {@code 409} si está inactivo o si el par ya lo
   * tiene alguien.
   *
   * <p><b>Y aquí NO hay ninguna comprobación de mayoría de edad</b>, que es lo que da sentido a
   * todo el diseño. El catálogo de `RF-SP-051` <b>solo contiene documentos de adulto</b>, de modo
   * que declarar el de un menor no llega a este método como un caso a rechazar: llega como un tipo
   * que no existe, igual que un identificador inventado. La regla vive en el contenido de una tabla
   * y en {@code fk_users_document_type}, no en un {@code if} que alguien pueda olvidar.
   *
   * <p>La unicidad se consulta <b>para el mensaje</b>; la garantiza {@code uq_users_document} en la
   * escritura, igual que el nombre de usuario. Entre esta lectura y el {@code INSERT} hay una
   * ventana que dos altas simultáneas atraviesan, y el adaptador traduce esa violación al mismo
   * {@code 409} distinguiéndola <b>por nombre de restricción</b>.
   */
  private void verificarDocumento(DocumentIdentity documento) {
    var tipo =
        documentos
            .find(documento.typeId())
            .orElseThrow(
                () ->
                    new UnprocessableEntityException(
                        "EX-010",
                        "El tipo de documento indicado no existe.",
                        List.of(
                            new FieldError(
                                "documentTypeId",
                                "EX-010",
                                "El tipo de documento '"
                                    + documento.typeId()
                                    + "' no existe en el catálogo."))));

    if (!tipo.active()) {
      throw conflicto(
          "RN-SP-035",
          "documentTypeId",
          "El tipo de documento '" + tipo.abbreviation() + "' está inactivo.");
    }

    // NO DICE DE QUIÉN ES, y no distingue si esa persona está vigente o
    // eliminada: decirlo informaría de la existencia de una cuenta, y
    // `RN-SP-035` no libera el documento al eliminar.
    if (usuarios.existsDocument(documento)) {
      throw conflicto("RN-SP-035", "documentNumber", "Ese documento ya está registrado.");
    }
  }

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
    List<FieldError> excedidos =
        PrivilegeContainment.excesos(concedidos, actor.permissions()).stream()
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
    Optional<AssignableRole> mayorRango = estructura.rolDeMayorRango(concedidos);

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
    Optional<AssignableRole> padre = estructura.rolExigidoAlSuperior(rol);

    if (padre.isEmpty()) {
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
    // EL IDENTIFICADOR Y NO EL CÓDIGO: es lo que quedó en la columna. El código
    // vive en otra tabla que `RN-SP-009` deja intacta, de modo que resolverlo al
    // leer la auditoría da siempre la misma respuesta.
    estado.put("country_id", usuario.getCountryId().toString());
    // EL IDENTIFICADOR DEL TIPO Y EL NÚMERO, que es lo que quedó en las
    // columnas. La abreviación vive en otra tabla y resolverla al leer la
    // auditoría da siempre la misma respuesta.
    estado.put(
        "document_type_id",
        usuario.getDocumentTypeId() == null ? null : usuario.getDocumentTypeId().toString());
    estado.put("document_number", usuario.getDocumentNumber());
    estado.put("phone", usuario.getPhone());
    estado.put("companyPhone", usuario.getCompanyPhone());
    estado.put("address_line1", usuario.getAddressLine1());
    estado.put("address_line2", usuario.getAddressLine2());
    estado.put("city", usuario.getCity());
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

  private static BusinessRuleException conflicto(String codigo, String campo, String mensaje) {
    return new BusinessRuleException(
        codigo, mensaje, List.of(new FieldError(campo, codigo, mensaje)));
  }
}
