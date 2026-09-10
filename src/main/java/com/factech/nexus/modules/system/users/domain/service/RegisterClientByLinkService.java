package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.users.application.PublicSellerLookup;
import com.factech.nexus.modules.system.users.application.RegistrableProductLookup;
import com.factech.nexus.modules.system.users.application.RegistrableProductLookup.RegistrableProductView;
import com.factech.nexus.modules.system.users.application.RegistrationSaleRegistrar;
import com.factech.nexus.modules.system.users.application.SelfRegistrationRequest;
import com.factech.nexus.modules.system.users.application.SelfRegistrationRequest.BrokerAccount;
import com.factech.nexus.modules.system.users.application.SelfRegistrationResponse;
import com.factech.nexus.modules.system.users.domain.models.ContactDetails;
import com.factech.nexus.modules.system.users.domain.models.DocumentIdentity;
import com.factech.nexus.modules.system.users.domain.models.Email;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.models.UserStatus;
import com.factech.nexus.modules.system.users.domain.models.Username;
import com.factech.nexus.modules.system.users.domain.repository.AssignableCountry;
import com.factech.nexus.modules.system.users.domain.repository.AssignableDocumentType;
import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import com.factech.nexus.modules.system.users.domain.repository.BrokerAccountRegistrar;
import com.factech.nexus.modules.system.users.domain.repository.BrokerAccountRegistrar.BrokerRef;
import com.factech.nexus.modules.system.users.domain.repository.MembershipCatalog;
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
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import com.factech.nexus.shared.security.PasswordHasher;
import com.factech.nexus.shared.security.PasswordPolicy;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
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
 * Registro de un cliente desde un enlace (`RF-SP-045`).
 *
 * <h2>Es el primer endpoint público del sistema que ESCRIBE</h2>
 *
 * <p>Los que ya existen o leen —el hotlink, los tres catálogos, la salud— o consumen una credencial
 * que alguien emitió —las de sesión y recuperación—. Este <b>crea cuentas</b>, y de ahí salen las
 * dos formas que gobiernan este servicio:
 *
 * <ul>
 *   <li><b>Las respuestas no distinguen</b> lo que un desconocido no debe poder averiguar. Producto
 *       inexistente, inactivo y retirado comparten respuesta; vendedor inexistente y sin rol
 *       vendedor, también. Distinguirlos convertiría el endpoint en una forma de enumerar el
 *       catálogo comercial y la plantilla.
 *   <li><b>Y sí distinguen lo que quien se registra necesita para corregir</b>: cuál de sus dos
 *       identidades chocó, y que su cuenta de broker ya está tomada.
 * </ul>
 *
 * <h2>Cuatro escrituras, MÁS UNA POR CUENTA DE BROKER, y una sola transacción</h2>
 *
 * <p>Cuenta, rol, membresía, atribución y <b>una o más</b> cuentas de broker. <b>Cualquier corte
 * deja un estado que ninguna regla admite</b>: una cuenta con rol de consumidor y sin membresía
 * viola `RN-SP-018`; una sin atribución deja un cliente que no comisiona a nadie; y una sin cuenta
 * de broker deja a la persona en {@code FTD_PENDIENTE} esperando un depósito que nadie podrá
 * atribuirle.
 *
 * <p><b>Que sean varias no cambia la transacción, y sí cambia lo que puede quedar a medias</b>: si
 * la tercera cuenta choca con el índice de `RN-SP-038`, se deshacen también las dos primeras y la
 * persona. Registrar a alguien con la mitad de sus brokers declarados sería peor que no registrarlo
 * — nadie sabría cuál falta.
 */
@Service
public class RegisterClientByLinkService {

  private static final String MODULO = "SP";
  private static final String ENTIDAD = "users";

  /** El código de la membresía gratuita. Convención elegida el 01-09-2026 sobre una columna. */
  private static final String MEMBRESIA_GRATUITA = "BECA";

  /** El rol de clasificación que recibe quien se registra. Lo siembra `V30`. */
  private static final String ROL_CLIENTE = "CLIENTE";

  private final UserRepository usuarios;
  private final RegistrableProductLookup productos;
  private final PublicSellerLookup vendedores;
  private final RoleCatalog roles;
  private final AssignableCountry paises;
  private final AssignableDocumentType documentos;
  private final BrokerAccountRegistrar brokers;
  private final MembershipCatalog membresias;
  private final RegistrationSaleRegistrar ventas;
  private final PasswordPolicy politica;
  private final PasswordHasher hasher;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  @Autowired
  public RegisterClientByLinkService(
      UserRepository usuarios,
      RegistrableProductLookup productos,
      PublicSellerLookup vendedores,
      RoleCatalog roles,
      AssignableCountry paises,
      AssignableDocumentType documentos,
      BrokerAccountRegistrar brokers,
      MembershipCatalog membresias,
      RegistrationSaleRegistrar ventas,
      PasswordPolicy politica,
      PasswordHasher hasher,
      AuditWriter auditoria,
      UuidV7Generator ids) {
    this(
        usuarios,
        productos,
        vendedores,
        roles,
        paises,
        documentos,
        brokers,
        membresias,
        ventas,
        politica,
        hasher,
        auditoria,
        ids,
        Clock.systemUTC());
  }

  RegisterClientByLinkService(
      UserRepository usuarios,
      RegistrableProductLookup productos,
      PublicSellerLookup vendedores,
      RoleCatalog roles,
      AssignableCountry paises,
      AssignableDocumentType documentos,
      BrokerAccountRegistrar brokers,
      MembershipCatalog membresias,
      RegistrationSaleRegistrar ventas,
      PasswordPolicy politica,
      PasswordHasher hasher,
      AuditWriter auditoria,
      UuidV7Generator ids,
      Clock reloj) {
    this.usuarios = usuarios;
    this.productos = productos;
    this.vendedores = vendedores;
    this.roles = roles;
    this.paises = paises;
    this.documentos = documentos;
    this.brokers = brokers;
    this.membresias = membresias;
    this.ventas = ventas;
    this.politica = politica;
    this.hasher = hasher;
    this.auditoria = auditoria;
    this.ids = ids;
    this.reloj = reloj;
  }

  @Transactional
  public SelfRegistrationResponse register(SelfRegistrationRequest peticion) {
    // EL MOVIMIENTO SE RESUELVE PRIMERO, y ese orden es nuevo del 09-09-2026:
    // desde que `product` y `referrer` desaparecieron del primer nivel, es él
    // quien dice qué producto es el del enlace y quién lo compartió.
    SelfRegistrationRequest.Movement movimiento = verificarMovimiento(peticion);

    RegistrableProductView producto = verificarProducto(movimiento.productId());
    UUID vendedor = verificarVendedor(movimiento.sellerUsername());

    Username username = new Username(peticion.username());
    Email email = new Email(peticion.email());

    // ANTES de tocar la base: es formato, y necesita el nombre de usuario y el
    // correo ya normalizados para comprobar que la contraseña no los contiene.
    politica.verificar(peticion.password(), username.value(), email.value());

    verificarUnicidad(username, email);

    UUID pais = verificarPais(peticion.countryCode());
    UUID tipoDocumento = verificarDocumento(peticion.documentType());
    List<BrokerAccount> cuentas = verificarCuentasDeBroker(producto, peticion);

    AssignableRole rolCliente =
        roles
            .findByCode(ROL_CLIENTE)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "El rol " + ROL_CLIENTE + " no está sembrado: `V30` es su migración."));

    OffsetDateTime ahora = OffsetDateTime.now(reloj);

    User usuario =
        usuarios.save(
            User.selfRegister(
                ids.next(),
                username,
                email,
                peticion.firstName(),
                peticion.lastName(),
                hasher.hash(peticion.password()),
                pais,
                new DocumentIdentity(tipoDocumento, peticion.documentNumber()),
                new ContactDetails(
                    Optional.ofNullable(peticion.phone()),
                    Patchable.de(peticion.addressLine1()),
                    Patchable.de(peticion.addressLine2()),
                    Patchable.de(peticion.city())),
                Set.of(rolCliente.id()),
                estadoInicial(producto),
                ahora));

    concederMembresia(usuario, producto, ahora);

    // `RN-SP-027`: no se registra un cliente sin atribución. La alternativa
    // —admitirlo con la atribución vacía— produciría clientes huérfanos que
    // nadie comisiona y que nadie sabe reclamar.
    usuarios.assignSupervisor(ids.next(), usuario.getId(), vendedor, ahora);

    // Todas en la MISMA transacción que la persona: una cuenta rechazada por el
    // índice deshace el registro entero, en lugar de dejar a alguien dentro con
    // media declaración.
    for (BrokerAccount cuenta : cuentas) {
      brokers.declare(ids.next(), usuario.getId(), cuenta.brokerId(), cuenta.accountId().trim());
    }

    // LA VENTA, en la misma transacción y la última: si se rechaza, no queda ni
    // la persona. Registrar a alguien cuya venta no se pudo anotar y anotar una
    // venta de alguien que no existe son los dos estados que esto evita.
    String venta =
        ventas.registerSale(
            usuario.getId(),
            movimiento.productId(),
            movimiento.paymentMethodId(),
            movimiento.movementTypeCode(),
            movimiento.sellerUsername());

    auditar(usuario, producto, movimiento.sellerUsername(), cuentas.size(), venta);

    return SelfRegistrationResponse.de(
        usuario.getId(), usuario.getUsername(), usuario.getStatus().name(), venta);
  }

  // ---------------------------------------------------------------------------
  // Las reglas del enlace
  // ---------------------------------------------------------------------------

  /**
   * `EX-001` y `EX-003`, sobre <b>el producto del movimiento</b>.
   *
   * <p><b>Los tres casos de `EX-001` comparten respuesta</b> —no existe, inactivo, retirado— y los
   * resuelve el propio puerto devolviendo vacío.
   *
   * <p><b>Llega por identificador desde el 09-09-2026</b>, y el puerto sigue admitiendo código o
   * identificador porque no es suyo el cambio: lo que desapareció es el campo {@code product} del
   * cuerpo, no la capacidad del catálogo de resolver un código. El campo del error sigue siendo
   * {@code product} —no {@code movement}— porque lo que no procede es el producto y no el bloque.
   */
  private RegistrableProductView verificarProducto(UUID identificador) {
    RegistrableProductView producto =
        productos
            .findRegistrable(identificador.toString())
            .orElseThrow(() -> noProcede("product", "EX-001"));

    if (!producto.upgrade()) {
      // Un bot no declara membresía destino, y sin nivel la cuenta violaría
      // `RN-SP-018` en el mismo instante de nacer.
      throw noProcede("product", "EX-003");
    }

    // `EX-004` MURIÓ EL 09-09-2026. Rechazaba todo producto que no llevara a la
    // membresía gratuita —«ese producto exige un pago»— porque no había con qué
    // cobrarlo. Ahora el registro anota la venta, de modo que el camino de pago
    // deja de estar cerrado: lo que cambia según el producto es el estado en el
    // que nace la cuenta y qué membresía se concede, no si se admite.
    return producto;
  }

  /**
   * ¿La cuenta nace esperando un depósito, o ya operativa? (09-09-2026)
   *
   * <p><b>{@code FTD_PENDIENTE} solo en el camino gratuito.</b> Ahí no hay dinero de por medio y lo
   * que abre la cuenta es el <b>primer depósito en el broker</b>, confirmado desde fuera
   * (`RF-SP-054`). Quien paga un producto no tiene ningún depósito que esperar: su cuenta nace
   * <b>{@code ACTIVO}</b>.
   *
   * <p><b>Lo que NO trae consigo es la membresía comprada</b>, y conviene no confundir las dos
   * cosas: estar activo es poder entrar y operar; tener el nivel comprado depende de que el pago se
   * confirme (`RN-MV-020`). Ver {@link #concederMembresia}.
   */
  private static UserStatus estadoInicial(RegistrableProductView producto) {
    return esRenovacionGratuita(producto) ? UserStatus.FTD_PENDIENTE : UserStatus.ACTIVO;
  }

  /**
   * `RN-SP-018` — toda persona nace con nivel, y cuál depende del camino.
   *
   * <ul>
   *   <li><b>Camino gratuito</b>: la membresía <b>del producto</b>, con su vigencia. No hay nada
   *       que confirmar — nadie pagó nada.
   *   <li><b>Camino de pago</b>: la membresía <b>del suelo</b>, sin vigencia. La comprada la
   *       concede <b>confirmar la venta</b> (`RN-MV-020`), y concederla aquí duplicaría lo que hace
   *       la confirmación — dos concesiones para una sola compra, y la segunda sin pago verificado.
   *       Es la contraparte de `RN-MV-004`: registrar una venta no concede nada.
   * </ul>
   */
  private void concederMembresia(
      User usuario, RegistrableProductView producto, OffsetDateTime ahora) {

    if (esRenovacionGratuita(producto)) {
      // Vigencia nula significa que la membresía NO CADUCA (`FA-001`), y no que
      // caduque hoy.
      OffsetDateTime fin =
          producto.validityDays() == null ? null : ahora.plusDays(producto.validityDays());

      usuarios.assignMembership(
          ids.next(), usuario.getId(), producto.targetMembershipId(), fin, ahora);
      return;
    }
    usuarios.assignMembership(ids.next(), usuario.getId(), membresias.floor().id(), null, ahora);
  }

  private static boolean esRenovacionGratuita(RegistrableProductView producto) {
    return MEMBRESIA_GRATUITA.equalsIgnoreCase(producto.sourceMembershipCode())
        && MEMBRESIA_GRATUITA.equalsIgnoreCase(producto.targetMembershipCode());
  }

  /**
   * El bloque del movimiento, que <b>siempre</b> viaja (09-09-2026).
   *
   * <p><b>Todo alta deja rastro de qué se vendió y con qué se pagó</b>, también la gratuita.
   *
   * <p><b>El método de pago NO se comprueba aquí, y es deliberado.</b> `RN-MV-022` lo hace
   * condicional <b>al importe</b>: prohibido si la venta vale cero —esa se registra con el pago
   * gratuito, que asigna el sistema y que el catálogo público ni siquiera devuelve— y obligatorio
   * si tiene importe. Repetir esa regla aquí daría <b>dos definiciones de cuándo hace falta
   * pagar</b>, y la de aquí sería además la equivocada: miraría las membresías del producto y no lo
   * que cuesta. Se reenvía tal cual llegó, y `MV` decide.
   *
   * <p><b>`VAL-016` MURIÓ EL 09-09-2026, y no por relajarse.</b> Comprobaba que el producto del
   * movimiento fuera el del enlace, y existía únicamente porque había <b>dos campos para un
   * dato</b>: {@code product} en el primer nivel y {@code productId} aquí. Al quitar el duplicado
   * la divergencia <b>dejó de poder expresarse</b>, y una comprobación de algo imposible es peor
   * que ninguna: sugiere que el riesgo sigue ahí. Lo mismo pasó con la mitad de `EX-010` que
   * vigilaba al vendedor.
   *
   * <p><b>Se resuelve ANTES que el producto y el vendedor</b>, porque es de donde salen los dos.
   * Que estén informados lo exige Bean Validation —`VAL-001` y `VAL-002`, los códigos que tenían
   * los campos que sustituyen—, de modo que aquí solo queda lo que una anotación no puede decir.
   */
  private SelfRegistrationRequest.Movement verificarMovimiento(SelfRegistrationRequest p) {
    SelfRegistrationRequest.Movement movimiento = p.movement();

    if (movimiento == null) {
      throw validacion("movement", "VAL-015", "Debe indicar los datos del movimiento.");
    }
    if (movimiento.movementTypeCode() == null || movimiento.movementTypeCode().isBlank()) {
      throw validacion("movement", "VAL-017", "Debe indicar el tipo de movimiento.");
    }
    return movimiento;
  }

  /** `EX-002` — inexistente, eliminado y sin rol vendedor comparten respuesta. */
  private UUID verificarVendedor(String username) {
    return vendedores
        .sellerIdByUsername(username)
        .orElseThrow(() -> noProcede("referrer", "EX-002"));
  }

  private void verificarUnicidad(Username username, Email email) {
    // Aquí SÍ se distingue, y contradice a `EX-001` a propósito: quien se
    // registra necesita saber cuál de sus dos identidades chocó para poder
    // corregirla. Que un nombre de usuario esté tomado se averigua igual
    // intentando registrarlo.
    if (usuarios.existsUsername(username)) {
      String mensaje = "Ese nombre de usuario ya está en uso.";
      throw new BusinessRuleException(
          "EX-005", mensaje, List.of(new FieldError("username", "VAL-007", mensaje)));
    }
    if (usuarios.existsEmail(email)) {
      String mensaje = "Ese correo ya está en uso.";
      throw new BusinessRuleException(
          "EX-005", mensaje, List.of(new FieldError("email", "VAL-008", mensaje)));
    }
  }

  /** `EX-006` — inexistente e inactivo comparten respuesta, al revés que en el alta interna. */
  private UUID verificarPais(String codigo) {
    return paises
        .findByCode(codigo)
        .filter(AssignableCountry.CountryRef::active)
        .map(AssignableCountry.CountryRef::id)
        .orElseThrow(() -> noProcede("countryCode", "EX-006"));
  }

  /**
   * `EX-007` — inexistente, inactivo y <b>ya en uso</b> comparten respuesta.
   *
   * <p><b>El documento repetido NO dice que lo esté</b>, al revés que el nombre de usuario: un
   * número de documento es un dato que se consigue, y confirmarle a un desconocido que esa persona
   * ya tiene cuenta publica algo que él no tenía. El coste está aceptado: quien de verdad ya estaba
   * registrado no sabrá por qué falla, y su salida es iniciar sesión o recuperar la contraseña.
   *
   * <p>Aquí solo se resuelve el tipo; la unicidad la sostiene {@code uq_users_document}.
   */
  private UUID verificarDocumento(String abreviacion) {
    return documentos
        .findByAbbreviation(abreviacion)
        .filter(AssignableDocumentType.DocumentTypeRef::active)
        .map(AssignableDocumentType.DocumentTypeRef::id)
        .orElseThrow(() -> noProcede("documentType", "EX-007"));
  }

  /**
   * `RN-SP-042` y `EX-008` — la cuenta de broker, obligatoria solo en el enlace `BECA → BECA`.
   *
   * <p><b>La condición se comprueba DESPUÉS de resolver el producto</b> y no en la validación del
   * cuerpo, porque depende de un dato que hay que ir a buscar: no cabe en una anotación.
   *
   * <p><b>Hoy la condición se cumple siempre</b> —todo producto admisible aquí lleva a la membresía
   * gratuita, y `RN-PM-017` impide que un producto apunte por debajo de su origen—, y lo que compra
   * es el día que se abra el camino de pago: entonces entrarán productos cuyo acceso no depende de
   * ningún depósito, y pedir la cuenta de broker sería pedir un dato que no hace falta.
   *
   * <p><b>Son UNA O MÁS</b> (09-09-2026): una persona puede operar con varios brokers, y el
   * registro es hoy la única vía para declararlos — `RF-SP-053` sigue sin decidirse para todo lo
   * que no sea este formulario. Lo que la regla exige es <b>al menos una</b>, no exactamente una.
   *
   * <p><b>Las repetidas dentro de la MISMA petición se rechazan aquí</b> y no en el índice, aunque
   * el índice también las cazaría: la respuesta de `RN-SP-038` dice «ya está declarada por otra
   * persona», y en este caso la otra persona <b>es ella misma</b>, tres líneas más arriba del mismo
   * formulario. Un mensaje que miente confunde más que uno que falta.
   *
   * @return las cuentas verificadas, o la lista vacía si este enlace no exige ninguna
   */
  private List<BrokerAccount> verificarCuentasDeBroker(
      RegistrableProductView producto, SelfRegistrationRequest p) {

    boolean gratuitaAGratuita =
        MEMBRESIA_GRATUITA.equalsIgnoreCase(producto.sourceMembershipCode())
            && MEMBRESIA_GRATUITA.equalsIgnoreCase(producto.targetMembershipCode());

    List<BrokerAccount> cuentas = p.cuentas();

    if (!gratuitaAGratuita) {
      // El enlace no exige cuenta, y lo que llegue se declara igual: quien la
      // aporta sin que se le pida no está haciendo nada indebido, y descartarla
      // en silencio sería perder un dato que su titular quiso dar.
      return verificadas(cuentas);
    }

    if (cuentas.isEmpty()) {
      throw validacion("brokerAccounts", "VAL-012", "Debe indicar al menos una cuenta de broker.");
    }
    return verificadas(cuentas);
  }

  private List<BrokerAccount> verificadas(List<BrokerAccount> cuentas) {
    Set<String> vistas = new LinkedHashSet<>();

    for (BrokerAccount cuenta : cuentas) {
      if (cuenta == null || cuenta.brokerId() == null) {
        throw validacion(
            "brokerAccounts", "VAL-012", "Debe indicar el broker donde tiene su cuenta.");
      }
      if (cuenta.accountId() == null || cuenta.accountId().isBlank()) {
        throw validacion(
            "brokerAccounts", "VAL-013", "Debe indicar su identificador de cuenta en el broker.");
      }
      if (!vistas.add(cuenta.brokerId() + "|" + cuenta.accountId().trim())) {
        throw validacion(
            "brokerAccounts", "VAL-014", "No repita la misma cuenta de broker en el registro.");
      }

      // Inexistente e inactivo comparten respuesta: el catálogo ya dice cuáles
      // hay —es público— y distinguirlos solo permitiría averiguar con qué
      // brokers opera la plataforma probando identificadores.
      brokers
          .find(cuenta.brokerId())
          .filter(BrokerRef::active)
          .orElseThrow(() -> noProcede("brokerAccounts", "EX-008"));
    }
    return cuentas;
  }

  // ---------------------------------------------------------------------------
  // Auditoría
  // ---------------------------------------------------------------------------

  /**
   * Un evento de seguridad y el registro de cambio, <b>bajo la misma correlación</b>.
   *
   * <p><b>No se añade un tipo de evento nuevo</b>: el catálogo de {@code event_type} es un {@code
   * CHECK} y ampliarlo cuesta una migración. Lo que distingue este alta de la administrativa es
   * {@code selfRegistered} en el detalle, y el actor — que aquí es <b>la propia persona</b>.
   */
  private void auditar(
      User usuario,
      RegistrableProductView producto,
      String referrer,
      int cuentasDeBroker,
      String venta) {

    Map<String, Object> detalle = new LinkedHashMap<>();
    detalle.put("selfRegistered", true);
    detalle.put("product", producto.code());
    detalle.put("referrer", referrer);
    detalle.put("brokerAccounts", cuentasDeBroker);
    detalle.put("sale", venta);

    // El estado inicial, con las mismas claves que el alta administrativa: dos
    // altas de la misma tabla descritas con claves distintas no se pueden
    // comparar, que es para lo que existe este registro. Ningún campo derivado
    // de la credencial, ni siquiera su longitud (Art. IV.8).
    Map<String, Object> estado = new LinkedHashMap<>();
    estado.put("username", usuario.getUsername());
    estado.put("email", usuario.getEmail());
    estado.put("first_name", usuario.getFirstName());
    estado.put("last_name", usuario.getLastName());
    estado.put("country_id", usuario.getCountryId().toString());
    estado.put(
        "document_type_id",
        usuario.getDocumentTypeId() == null ? null : usuario.getDocumentTypeId().toString());
    estado.put("document_number", usuario.getDocumentNumber());
    estado.put("phone", usuario.getPhone());
    estado.put("status", usuario.getStatus().name());
    estado.put("must_change_password", usuario.isMustChangePassword());
    estado.put("self_registered", true);

    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, usuario.getId(), ChangeAction.CREATE, estado));

    auditoria.recordSecurity(
        new SecurityEvent(
            SecurityEventType.USER_CREATED,
            Severity.MEDIA,
            Outcome.SUCCESS,
            usuario.getId(),
            detalle));
  }

  // ---------------------------------------------------------------------------
  // Formato de los rechazos
  // ---------------------------------------------------------------------------

  /**
   * El rechazo que <b>no dice qué pasó</b>.
   *
   * <p>Todos comparten mensaje a propósito: es lo que impide que probando valores se averigüe qué
   * hay en el catálogo, quién trabaja aquí o en qué mercados se opera.
   */
  private static UnprocessableEntityException noProcede(String campo, String codigo) {
    String mensaje = "Los datos del enlace no son válidos.";
    return new UnprocessableEntityException(
        codigo, mensaje, List.of(new FieldError(campo, codigo, mensaje)));
  }

  private static ValidationException validacion(String campo, String codigo, String mensaje) {
    return new ValidationException(
        codigo, mensaje, List.of(new FieldError(campo, codigo, mensaje)));
  }
}
