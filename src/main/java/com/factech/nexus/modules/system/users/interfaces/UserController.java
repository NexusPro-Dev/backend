package com.factech.nexus.modules.system.users.interfaces;

import com.factech.nexus.modules.system.brokers.application.BrokerAccountsResponse;
import com.factech.nexus.modules.system.brokers.application.TeamBrokerAccountItem;
import com.factech.nexus.modules.system.brokers.domain.service.GetBrokerAccountsService;
import com.factech.nexus.modules.system.brokers.domain.service.GetTeamBrokerAccountsService;
import com.factech.nexus.modules.system.users.application.AssignMembershipRequest;
import com.factech.nexus.modules.system.users.application.AssignRolesRequest;
import com.factech.nexus.modules.system.users.application.AssignSupervisorRequest;
import com.factech.nexus.modules.system.users.application.ChangeUserStatusRequest;
import com.factech.nexus.modules.system.users.application.ClientSellersResponse;
import com.factech.nexus.modules.system.users.application.CommercialStructureResponse;
import com.factech.nexus.modules.system.users.application.DeleteUserRequest;
import com.factech.nexus.modules.system.users.application.ListUsersRequest;
import com.factech.nexus.modules.system.users.application.OwnProfileResponse;
import com.factech.nexus.modules.system.users.application.RegisterUserRequest;
import com.factech.nexus.modules.system.users.application.ResetPasswordRequest;
import com.factech.nexus.modules.system.users.application.RevokeRolesRequest;
import com.factech.nexus.modules.system.users.application.SellerClientItem;
import com.factech.nexus.modules.system.users.application.UpdateOwnProfileRequest;
import com.factech.nexus.modules.system.users.application.UpdateUserRequest;
import com.factech.nexus.modules.system.users.application.UserDetailResponse;
import com.factech.nexus.modules.system.users.application.UserListItem;
import com.factech.nexus.modules.system.users.application.UserMembershipResponse;
import com.factech.nexus.modules.system.users.application.UserResponse;
import com.factech.nexus.modules.system.users.application.UserStatusResponse;
import com.factech.nexus.modules.system.users.domain.service.AssignSupervisorService;
import com.factech.nexus.modules.system.users.domain.service.AssignUserMembershipService;
import com.factech.nexus.modules.system.users.domain.service.AssignUserRolesService;
import com.factech.nexus.modules.system.users.domain.service.ChangeUserStatusService;
import com.factech.nexus.modules.system.users.domain.service.DeleteUserService;
import com.factech.nexus.modules.system.users.domain.service.GetClientSellersService;
import com.factech.nexus.modules.system.users.domain.service.GetCommercialTeamService;
import com.factech.nexus.modules.system.users.domain.service.GetOwnProfileService;
import com.factech.nexus.modules.system.users.domain.service.GetSellerClientsService;
import com.factech.nexus.modules.system.users.domain.service.GetUserService;
import com.factech.nexus.modules.system.users.domain.service.ListUsersService;
import com.factech.nexus.modules.system.users.domain.service.RegisterUserService;
import com.factech.nexus.modules.system.users.domain.service.ResetUserPasswordService;
import com.factech.nexus.modules.system.users.domain.service.RevokeUserMembershipService;
import com.factech.nexus.modules.system.users.domain.service.RevokeUserRolesService;
import com.factech.nexus.modules.system.users.domain.service.UpdateOwnProfileService;
import com.factech.nexus.modules.system.users.domain.service.UpdateUserService;
import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Personas del sistema (`RF-SP-024`).
 *
 * <p><b>La cabecera {@code Location} sí se devuelve aquí</b>, al revés que en el alta de país, y
 * por un motivo concreto: {@code /api/v1/users/{id}} <b>resuelve</b>, porque `RF-SP-026` publica el
 * detalle. Una cabecera que lleva al recurso creado tiene sentido cuando ese recurso se puede
 * consultar.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Usuarios", description = "Alta y administración de las personas del sistema.")
public class UserController {

  private final RegisterUserService alta;
  private final AssignUserRolesService asignacion;
  private final RevokeUserRolesService retiro;
  private final AssignUserMembershipService membresia;
  private final RevokeUserMembershipService retiroDeMembresia;
  private final AssignSupervisorService superior;
  private final GetCommercialTeamService equipoACargo;
  private final ListUsersService listado;
  private final GetUserService detalleDeUsuario;
  private final UpdateUserService edicion;
  private final ChangeUserStatusService cambioDeEstado;
  private final DeleteUserService eliminacion;
  private final GetOwnProfileService perfilPropio;
  private final UpdateOwnProfileService edicionPropia;
  private final ResetUserPasswordService restablecimiento;

  // Los dos servicios viven en el submódulo de BROKERS, que es el dueño del
  // dato, y se orquestan desde aquí porque las dos rutas cuelgan de
  // `/api/v1/users`: las cuentas de broker no existen sin su titular
  // (`RF-SP-055` · `plan.md` §3).
  private final GetBrokerAccountsService cuentasDeBroker;
  private final GetTeamBrokerAccountsService cuentasDelEquipo;
  private final GetClientSellersService vendedoresDelCliente;
  private final GetSellerClientsService carteraDelVendedor;

  public UserController(
      RegisterUserService alta,
      AssignUserRolesService asignacion,
      RevokeUserRolesService retiro,
      AssignUserMembershipService membresia,
      RevokeUserMembershipService retiroDeMembresia,
      AssignSupervisorService superior,
      GetCommercialTeamService equipoACargo,
      ListUsersService listado,
      GetUserService detalleDeUsuario,
      UpdateUserService edicion,
      ChangeUserStatusService cambioDeEstado,
      DeleteUserService eliminacion,
      GetOwnProfileService perfilPropio,
      UpdateOwnProfileService edicionPropia,
      ResetUserPasswordService restablecimiento,
      GetBrokerAccountsService cuentasDeBroker,
      GetTeamBrokerAccountsService cuentasDelEquipo,
      GetClientSellersService vendedoresDelCliente,
      GetSellerClientsService carteraDelVendedor) {
    this.cuentasDeBroker = cuentasDeBroker;
    this.cuentasDelEquipo = cuentasDelEquipo;
    this.vendedoresDelCliente = vendedoresDelCliente;
    this.carteraDelVendedor = carteraDelVendedor;
    this.alta = alta;
    this.asignacion = asignacion;
    this.retiro = retiro;
    this.membresia = membresia;
    this.retiroDeMembresia = retiroDeMembresia;
    this.superior = superior;
    this.equipoACargo = equipoACargo;
    this.listado = listado;
    this.detalleDeUsuario = detalleDeUsuario;
    this.edicion = edicion;
    this.cambioDeEstado = cambioDeEstado;
    this.eliminacion = eliminacion;
    this.perfilPropio = perfilPropio;
    this.edicionPropia = edicionPropia;
    this.restablecimiento = restablecimiento;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('users:create')")
  @Operation(
      summary = "Registrar una persona",
      description =
          """
          Registra una persona con sus roles iniciales.

          La cuenta nace **activa** y **marcada para cambio obligatorio de
          contraseña**: quien prepara el alta conoce la credencial, y esa ventana
          se cierra en el primer inicio de sesión. Ni el estado ni la marca se
          envían; enviarlos devuelve `400`.
          **`membershipId` es OPCIONAL, y el superior es condicional.** Desde el
          05-09-2026 toda persona nace con nivel: si no se indica ninguno, se le
          concede el de arranque (`BECA`). Ya no hay rechazo por indicarlo sin rol
          de consumidor ni por omitirlo teniéndolo — esa exigencia murió con
          `RN-SP-013`.

          El rol de vendedor y el superior comercial **sí siguen siendo
          condicionales en los dos sentidos**: indicar uno sin el otro devuelve
          `409`, no se ignora.

          El superior debe portar el **rol padre inmediato** del rol vendedor de
          mayor rango de la persona, y estar activo.

          ## Identidad, contacto y país

          **`countryId`, `documentTypeId`, `documentNumber` y `phone` son
          obligatorios**, y a diferencia de la membresía y el superior **no
          dependen de qué roles se concedan**: su ausencia es `400`, nunca un
          `409` condicional.

          **`companyPhone`, `addressLine1`, `addressLine2` y `city` son
          opcionales.** Exigir una dirección postal a un funcionario interno
          bloquearía su alta sin que nadie la necesite. `city` es **texto
          libre**: no hay catálogo de ciudades.

          **El tipo de documento sale de `GET /api/v1/document-types`, y ese
          catálogo contiene SOLO documentos de persona mayor de edad.** Ahí está
          la validación de mayoría de edad del sistema entero: no hay ninguna
          marca que diga cuáles acreditan y cuáles no, porque **los que no
          acreditan no están**. Registrar a un menor no se rechaza — no se puede
          expresar, porque no hay identificador que enviar. Un desplegable
          alimentado por ese catálogo no puede ofrecer una opción que el alta vaya
          a rechazar.

          **El par tipo + número es único entre TODAS las personas, incluidas las
          eliminadas** (`RN-SP-035`), igual que el nombre de usuario y el correo.
          El `409` que produce **no dice de quién es** el documento, ni si esa
          persona sigue vigente.

          El número de documento se guarda **recortado y en mayúsculas**, y
          los **dos teléfonos** —`phone`, el personal, y `companyPhone`, el de la
          empresa— **normalizados a dígitos con un `+` opcional**, fuera espacios,
          guiones y paréntesis. La dirección y la ciudad solo se recortan.

          **`companyPhone` es OPCIONAL y `phone` no** (`RN-SP-037`): exigir un
          teléfono de empresa bloquearía el alta de todo el que no tenga una. Sale
          **presente y en nulo** cuando no se declara, nunca ausente.

          **Ningún teléfono se valida contra el país**: eso exigiría un catálogo de
          prefijos que no existe.

          La respuesta agrupa `country`, `document` y `contact` en tres objetos, y
          devuelve el **tipo de documento resuelto** —abreviación y nombre— para
          que no haga falta una segunda llamada al catálogo, que además exige otro
          permiso.

          La contraseña no se recorta: un espacio al principio o al final es parte
          de ella.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Persona registrada. `Location` apunta a su detalle.",
        content = @Content(schema = @Schema(implementation = UserResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Formato u obligatoriedad incumplidos —incluidos país, tipo y número de documento y"
                + " teléfono, que son obligatorios (`VAL-014` a `VAL-017`)—, contraseña que no"
                + " cumple la política, o campo no admitido en el cuerpo",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso de creación de usuarios (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "Identidad ya en uso (`RN-SP-016`), documento ya registrado por otra persona —vigente"
                + " o eliminada— o tipo de documento inactivo (`RN-SP-035`), país inactivo"
                + " (`RN-SP-034`), rol que excede los privilegios del actor (`RN-SEG-010`),"
                + " vendedor sin superior o al revés (`RN-SP-019`), o superior que no porta el rol"
                + " padre (`RN-SP-020`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description =
            "Algún rol no existe o no está activo (`EX-003`), el país no existe (`EX-009`) o el"
                + " tipo de documento no existe (`EX-010`) — referencias que no resuelven, frente"
                + " al `409` de las que resuelven y una regla rechaza",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public ResponseEntity<UserResponse> registrar(@Valid @RequestBody RegisterUserRequest peticion) {
    UserResponse creado = alta.register(peticion.toCommand());
    return ResponseEntity.created(URI.create("/api/v1/users/" + creado.id())).body(creado);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('users:list')")
  @Operation(
      summary = "Consultar personas",
      description =
          """
          Listado paginado con filtros, búsqueda y ordenamiento.

          **El orden por defecto es `lastName,asc`** y no el nombre de usuario:
          esta es la lista desde la que se administra el acceso, y quien la mira
          busca a alguien por su apellido.

          Solo se puede ordenar por la lista blanca —`username`, `email`,
          `firstName`, `lastName`, `status`, `createdAt`, `updatedAt`—. **No se
          admite ordenar por ningún campo de la credencial**: ordenar por la marca
          de cambio obligatorio produciría la lista de quien no ha cambiado su
          contraseña inicial.

          **`roleId`, `membershipId` y `countryId` no se validan contra su
          catálogo.** Un filtro por algo inexistente devuelve la colección vacía y
          no es un error.

          **`countryId` NO se acota a países activos**, y va contra la intuición:
          filtrar solo por los activos convertiría desactivar un país en una forma
          de **esconder a su gente**, y este listado es justamente la herramienta
          con la que se busca a quien quedó dentro para moverlo. Quien esté en un
          país retirado aparece con normalidad.

          Cada fila trae su `country` **resuelto y nunca nulo** — es el único
          objeto anidado de la fila del que se puede decir eso: `roles` puede venir
          vacía y `membership` y `deletedAt` pueden venir nulos.

          **El listado NO publica el documento ni los datos de contacto**, y no se
          puede buscar por ellos. Es deliberado: exponer un documento en un listado
          paginado alcanza a mucha más gente que devolverlo en un detalle. Quien
          necesite el documento de una persona usa `GET /api/v1/users/{id}`.

          La búsqueda va sobre nombre de usuario, correo y nombre completo,
          **sin distinguir acentos ni mayúsculas**, y por fragmento.

          `membership` **no es nula cuando está vencida**: vencer no es lo mismo
          que no tener. `current` dice cuál de los dos casos es.

          Un filtro sin coincidencias devuelve `200` con la colección vacía. No
          hay `404` ni `422`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Página de personas.",
        content = @Content(schema = @Schema(implementation = PageResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Paginación fuera de límites (`VAL-001`, `VAL-002`), campo de ordenamiento no admitido"
                + " (`VAL-003`), o estado fuera de su dominio (`VAL-004`). Se devuelven **juntos**",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `users:list` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public PageResponse<UserListItem> listar(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute ListUsersRequest filtros) {
    return listado.list(filtros);
  }

  @GetMapping("/me")
  @Operation(
      summary = "Consultar el propio perfil",
      description =
          """
          Devuelve el perfil de **quien porta el token**, con sus **permisos
          efectivos**.

          Su `contact` lleva **dos teléfonos** —`phone`, el personal, y
          `companyPhone`, el de la empresa, este último **opcional** y por tanto
          presente y en nulo cuando no se declaró— junto a la dirección. Es lo que
          permite precargar el formulario de `PATCH /api/v1/users/me` sin
          reescribir nada de memoria.

          Su `membership` —**ausente**, no nula, cuando la persona no tiene una
          vigente— trae `code`, `name`, `level`, `color` y `endsAt`. `name` y
          `color` entran el 14-09-2026: son lo que la pantalla de «mi perfil»
          necesita para pintar el nivel con su color y llamarlo por su nombre sin
          pedir la cadena entera de membresías. El color son seis hexadecimales
          sin `#`.

          `me` es un **literal**, no un identificador: no se admite pedir el
          propio detalle por la ruta con identificador, que es otra operación y
          exige permiso de lectura de usuarios.

          **Sin parámetros de ningún tipo** — ni de ruta, ni de consulta, ni de
          cuerpo.

          Los permisos salen del **mismo componente que autoriza**, de modo que lo
          que esta pantalla dice que la persona puede hacer es exactamente lo que
          el sistema le dejará hacer. Es lo que permite a una interfaz decidir qué
          mostrar sin duplicar en el navegador una regla que vive en el servidor.

          **No exige permiso alguno**, solo estar autenticado: no hay recurso
          ajeno que proteger.

          Devuelve **solo el superior comercial, nunca el equipo**: a quién
          reporta uno es un dato del actor; quiénes dependen de uno es un conjunto
          de terceros.

          Trae además el `country`, el `document` y el `contact` del actor.
          **`country` nunca falta y `document` sí puede faltar**: las personas
          registradas antes de que el documento fuera obligatorio no lo tienen, y
          el campo llega ausente. El navegador tiene que contemplarlo.

          **El `contact` se publica aquí para que la pantalla de edición pueda
          precargarse**: es exactamente lo que `PATCH /api/v1/users/me` deja
          corregir. El `document` viaja en la misma respuesta y **no** es
          editable por el titular — esa diferencia es la razón de que los dos
          vayan en objetos separados en lugar de como campos sueltos.

          `lastLoginAt` es un dato **informativo de la sesión en curso**, no una
          señal de intrusión: el inicio de sesión sobrescribe ese valor al entrar.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El perfil.",
        content = @Content(schema = @Schema(implementation = OwnProfileResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description =
            "Sin credencial válida, o la cuenta fue eliminada tras emitirse el token (`AUTH-001`)"
                + " — lo que dejó de valer es la sesión, no la ruta, y por eso no es `404`",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public OwnProfileResponse miPerfil() {
    return perfilPropio.profile();
  }

  // SIN `@PreAuthorize`, y es deliberado (`RF-SP-059` · `plan.md` §5): el
  // cliente sale del token y no hay nada que autorizar más allá de estar
  // autenticado. La ruta consta en `EndpointPermissionsIT` con este motivo.
  @GetMapping("/me/sellers")
  @Operation(
      summary = "Consultar mis vendedores",
      description =
          """
          Devuelve **los vendedores del actor**: quien lo registró —su **principal**,
          origen `REGISTRO`— y los vendedores por cuyo enlace compró —origen
          `HOTLINK`—. **El principal va primero**, después por fecha de vínculo.

          Es la respuesta a «¿a qué agente estoy asignado?» (`RF-SP-059`,
          `RN-SP-049`). **El principal es quien lo registró y no se cambia**:
          no hay operación que lo reasigne ni historial que cerrar. Un vínculo
          es un hecho, y por eso tampoco se quita: un vendedor desactivado o
          eliminado **sigue saliendo**.

          **Hoy la lista tiene un solo elemento**, y no es un defecto: las filas
          `HOTLINK` las escribirá la compra por hotlink (`RF-MV-011`,
          `RF-MV-013`), que no está construida. El contrato ya es el definitivo.

          De cada vendedor se publica lo mismo que su hotlink (`RN-PM-022`):
          **nombre y apellido**, más el nombre de usuario, que el cliente ya
          conoce. **Ni identificador, ni correo, ni estado, ni roles.**

          `linkedAt` es desde cuándo es su vendedor; en los vínculos anteriores al
          18-09-2026 —traídos por `V20` desde `user_supervisors`— es desde cuándo
          colgaba de él allí.

          Quien no es cliente —un vendedor, un funcionario— recibe `200` con la
          colección vacía: nadie lo registró por enlace. Un cliente dado de alta
          por un funcionario, también.

          **Desde el 18-09-2026 el cliente no cuelga de `user_supervisors`**, de
          modo que `GET /api/v1/users/me` **no devuelve `supervisor`** a un
          cliente: su vía es esta.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Los vendedores del actor, principal primero; vacío si no tiene",
        content = @Content(schema = @Schema(implementation = ClientSellersResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public ClientSellersResponse misVendedores() {
    return vendedoresDelCliente.mine();
  }

  @GetMapping("/{id}/sellers")
  @PreAuthorize("hasAuthority('users:read-sellers')")
  @Operation(
      summary = "Consultar los vendedores de un cliente",
      description =
          """
          La misma lista que `GET /api/v1/users/me/sellers`, sobre **cualquier
          persona** y con **`users:read-sellers`**, permiso propio de esta
          operación (`RN-SEG-014`, un permiso por operación; `V29` lo siembra a
          `SUPERADMIN` y `ADMIN`). Nació con `users:read` el 18-09-2026 —«los
          vendedores son un dato de la persona»— y cambió al integrarse, el
          21-09-2026, porque desde `RF-SP-060` ningún código gobierna dos
          operaciones.

          **`403` sin el permiso y `404` con el permiso y una persona que no
          existe**, el modelo general de `security.md` §5. Aquí no hay actor
          autorizado por estructura al que proteger de un oráculo de
          identificadores —a diferencia de `GET /users/{id}/broker-accounts`—,
          y quien porta este permiso porta normalmente `users:list`.

          Una persona que no es cliente devuelve `200` con la colección vacía.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Los vendedores de la persona, principal primero; vacío si no tiene",
        content = @Content(schema = @Schema(implementation = ClientSellersResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Identificador malformado (`VAL-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `users:read-sellers` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "La persona no existe o está eliminada (`VAL-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public ClientSellersResponse vendedoresDe(@PathVariable UUID id) {
    return vendedoresDelCliente.of(id);
  }

  // SIN `@PreAuthorize`, y es deliberado (`RF-SP-061` · `plan.md` §5): el
  // vendedor sale del token y no hay nada que autorizar más allá de estar
  // autenticado. La ruta consta en `EndpointPermissionsIT` con este motivo.
  @GetMapping("/me/clients")
  @Operation(
      summary = "Consultar mis clientes",
      description =
          """
          Devuelve **la cartera del actor**, paginada: los clientes que
          registró —origen `REGISTRO`, de los que es el **principal**— y los
          que le compraron por su enlace —origen `HOTLINK`—. **Los vínculos más
          recientes primero**, después por nombre de usuario.

          Es la lectura inversa de `GET /api/v1/users/me/sellers` (`RF-SP-061`,
          `RN-SP-049`): la misma tabla mirada desde el vendedor. Y es la que
          sustituye a `GET /users/{id}/team?roles=CLIENTE`, que desde el
          18-09-2026 devuelve vacío porque **el equipo es solo fuerza
          comercial** y la cartera no cuelga de él.

          **Cada fila lleva `id` y `status`**, al contrario que los vendedores
          de un cliente y por la razón inversa: desde la cartera se abre la ficha
          del cliente (`GET /api/v1/users/{id}`), y una cartera se trabaja —quien
          se registró y **todavía no depositó** está en `FTD_PENDIENTE`—. Lo que
          se publica es lo que el vendedor ya ve de esa persona en su detalle:
          ni correo, ni roles, ni membresía.

          **Un cliente desactivado o bloqueado sigue saliendo**, con su estado:
          el vínculo es un hecho. **Uno eliminado no**: para el sistema no existe
          y su `id` no abriría nada.

          **Hoy toda la cartera es `REGISTRO`**, y no es un defecto: las filas
          `HOTLINK` las escribirá la compra por hotlink (`RF-MV-011`,
          `RF-MV-013`), que no está construida. El contrato y el filtro
          `origin` ya las contemplan.

          `linkedAt` es desde cuándo es su cliente; en los vínculos anteriores al
          18-09-2026 —traídos por `V20` desde `user_supervisors`— es desde cuándo
          colgaba de él allí.

          Quien no es vendedor —un cliente, un funcionario— recibe `200` con la
          página vacía: nadie se registró con su enlace.
          """)
  @ApiResponses({
    // SIN `@Schema(implementation = PageResponse.class)`, por lo que
    // `GET /users/me/team/broker-accounts` dejó escrito: el anotado publica la
    // envoltura cruda y springdoc, dejado solo, emite `PageResponseSellerClientItem`.
    @ApiResponse(
        responseCode = "200",
        description =
            "Página con los clientes del actor, los vínculos más recientes primero; vacía si no"
                + " tiene cartera."),
    @ApiResponse(
        responseCode = "400",
        description =
            "`origin` fuera de `REGISTRO`/`HOTLINK` (`VAL-001`) o paginación fuera de"
                + " límites (`VAL-003`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public PageResponse<SellerClientItem> misClientes(
      @Parameter(
              description =
                  "Acota a los propios (`REGISTRO`) o a los vinculados (`HOTLINK`). Ausente,"
                      + " todos.")
          @RequestParam(required = false)
          String origin,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return carteraDelVendedor.mine(origin, page, size);
  }

  @GetMapping("/{id}/clients")
  @PreAuthorize("hasAuthority('users:read-clients')")
  @Operation(
      summary = "Consultar los clientes de un vendedor",
      description =
          """
          La misma página que `GET /api/v1/users/me/clients`, sobre **cualquier
          persona** y con **`users:read-clients`**, permiso propio de esta
          operación (`RN-SEG-014`, un permiso por operación; `V30` lo siembra a
          `SUPERADMIN` y `ADMIN`). Ni `users:read`, ni `users:read-team`, ni
          `users:read-sellers` la abren: el frontend decide qué vista mostrar por
          un solo código, y «los vendedores de un cliente» y «los clientes de un
          vendedor» son dos vistas.

          **No se autoriza por estructura.** El director de un agente no ve la
          cartera del agente por ser su director (D-22 sigue con su única
          excepción, las cuentas de broker de `RN-SP-046`); quien deba ver
          carteras ajenas porta el permiso, y ese día las ve todas.

          **`403` sin el permiso y `404` con el permiso y una persona que no
          existe**, el modelo general de `security.md` §5: el `403` sale antes
          de tocar la base y no revela si el identificador existe.

          Una persona que no es vendedor devuelve `200` con la página vacía.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description =
            "Página con los clientes de la persona, los vínculos más recientes primero; vacía si"
                + " no tiene cartera."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador malformado o `origin` fuera de `REGISTRO`/`HOTLINK` (`VAL-001`),"
                + " o paginación fuera de límites (`VAL-003`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `users:read-clients` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "La persona no existe o está eliminada (`VAL-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public PageResponse<SellerClientItem> clientesDe(
      @PathVariable UUID id,
      @Parameter(
              description =
                  "Acota a los propios (`REGISTRO`) o a los vinculados (`HOTLINK`). Ausente,"
                      + " todos.")
          @RequestParam(required = false)
          String origin,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return carteraDelVendedor.of(id, origin, page, size);
  }

  @PatchMapping("/me")
  @Operation(
      summary = "Editar el propio perfil",
      description =
          """
          Corrige el **propio** nombre, apellidos, correo y **datos de contacto**
          —los **dos teléfonos**, dirección, complemento y ciudad—. Autenticado y **sin
          ningún permiso**: `RF-SP-027` hace el mismo cambio pero exige
          `users:update`, que es un permiso de administración, de modo que
          concedérselo a alguien para que arregle su propio apellido le daría de
          paso la capacidad de editar el de cualquiera.

          `me` es un **literal**, no un identificador, y el cuerpo **no admite
          ninguno**: la operación no se puede desviar hacia otra persona.

          **`currentPassword` es obligatorio si y solo si se envía `email`.**
          Desde `RF-SP-040` el correo es la vía por la que se recupera una
          contraseña olvidada, así que cambiarlo es cambiar **quién puede
          recuperar la cuenta**. Una sesión robada no lleva la contraseña, y
          exigirla convierte el robo de sesión en algo que **caduca** en lugar de
          en una apropiación permanente. Cambiar solo el nombre no la pide,
          porque equivocar un apellido no abre ninguna puerta.

          **Enviar el correo que ya se tiene sigue exigiendo la contraseña.** Que
          el valor no cambie se sabe después de mirarlo, y condicionar la
          exigencia a eso daría una forma de averiguar el correo vigente probando
          valores.

          ## Qué se puede cambiar aquí y qué no

          **El contacto sí; la identidad no.** El tipo y el número de documento y
          el país **no están en este cuerpo**: son identidad, no contacto, y los
          corrige un administrador por `PATCH /api/v1/users/{id}`. Enviarlos
          devuelve `400` por propiedad desconocida, no se ignoran.

          La línea no es técnica: un teléfono nuevo o una mudanza son hechos que
          la persona conoce mejor que nadie y no deberían costar un ticket; el
          documento es con lo que figura en la auditoría, y el país decide qué
          medios de pago se le ofrecen.

          **Ningún teléfono exige `currentPassword`**, al contrario que el correo.
          La contraseña se pide cuando el campo **es una vía de acceso**, y el
          teléfono hoy no lo es. El día que exista verificación por SMS o segundo
          factor telefónico, esta decisión se revisa.

          **El nulo explícito no significa lo mismo en todo el cuerpo.**
          `addressLine1`, `addressLine2`, `city` y `companyPhone` **lo aceptan y vacían**
          —«ya no vivo ahí» es un hecho que hay que poder registrar—; el nombre,
          los apellidos, el correo y el **teléfono personal** lo rechazan con `400`.

          **`companyPhone` cae del lado que vacía aunque sea un teléfono**, y ahí
          está toda la diferencia: lo que decide no es qué dato es, sino si
          `RN-SP-037` lo exige. «Ya no tengo teléfono de empresa» es un hecho que
          hay que poder registrar.

          Devuelve el perfil ya actualizado, con **la misma forma** que
          `GET /api/v1/users/me`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El perfil actualizado.",
        content = @Content(schema = @Schema(implementation = OwnProfileResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Ningún campo informado (`VAL-001`), campo vaciado (`VAL-002`), correo inválido"
                + " (`VAL-003`), longitud excedida (`VAL-005`), falta la contraseña actual habiendo"
                + " correo (`VAL-006`) o campo desconocido",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Sin credencial válida, o la cuenta fue eliminada tras emitirse el token",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description =
            "Hay un cambio obligatorio de contraseña pendiente: se atiende primero. Esta ruta"
                + " **no** figura entre las alcanzables con esa marca",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Ese correo ya está en uso por otra persona (`RN-SP-016`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "La contraseña actual no es correcta (`VAL-007`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public OwnProfileResponse editarMiPerfil(@RequestBody UpdateOwnProfileRequest peticion) {
    return edicionPropia.update(peticion);
  }

  @PostMapping("/{id}/password-reset")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('users:reset-password')")
  @Operation(
      summary = "Restablecer la contraseña de una persona",
      description =
          """
          Fija una credencial **provisional** sobre la cuenta indicada. `POST`
          sobre un subrecurso y no `PATCH` sobre el usuario: cada petición **crea**
          un restablecimiento, que es un hecho con fecha y con caducidad propia.

          **La credencial CADUCA.** Sin plazo, una cuenta restablecida y nunca
          usada conserva indefinidamente una contraseña que otra persona conoce, y
          nadie se entera porque no falla nada. Quien comprueba el plazo es el
          inicio de sesión.

          La cuenta queda marcada para **cambio obligatorio**: la ventana en que
          dos personas conocen la misma contraseña se cierra en el primer inicio
          de sesión.

          **No toca el estado ni el bloqueo**: restablecer no es reactivar. Una
          cuenta desactivada sigue desactivada.

          **La contraseña asignada no se devuelve**: la conoce quien la escribió, y
          repetirla la expondría a cualquier registro de la operación. Tampoco la
          fecha de caducidad — quien la necesite consulta el detalle.

          Revoca **todas** las sesiones de la persona.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Credencial fijada.", content = @Content),
    @ApiResponse(
        responseCode = "400",
        description = "Contraseña ausente (`VAL-001`) o que no cumple la política (`VAL-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `users:reset-password` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "La persona no existe o está eliminada (`VAL-004`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "Es la cuenta del propio actor (`RN-SP-017`). El mensaje indica cuál es la operación"
                + " correcta: cambiar la propia contraseña, que exige conocer la actual",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public void restablecerContrasena(
      @PathVariable UUID id, @RequestBody ResetPasswordRequest peticion) {
    restablecimiento.reset(id, peticion);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('users:read')")
  @Operation(
      summary = "Consultar el detalle de una persona",
      description =
          """
          Sus roles **con el estado de cada uno**, sus permisos efectivos, su
          membresía y el contexto de su acceso.

          Las dos primeras juntas son lo único que explica por qué una persona
          **con roles** no puede hacer nada: porque todos están inactivos y la
          lista de permisos llega vacía.

          Los permisos efectivos salen del **mismo componente que autoriza**, de
          modo que esta respuesta no puede contradecir a lo que el sistema hará
          con la siguiente petición de esa persona.

          `lockedUntil` nulo significa dos cosas distintas y eso es información:
          la cuenta no está bloqueada, o lo está **por decisión de un actor** y
          por tanto sin expiración. El estado desambigua.

          Trae el `country`, el `document` y el `contact` de la persona. El
          contacto lleva **dos teléfonos**: `phone`, el personal, y `companyPhone`,
          el de la empresa — este último **opcional**, de modo que llega presente y
          en nulo cuando la persona no lo declaró.

          **`country` nunca es nulo y `document` sí puede serlo**: quienes se
          registraron antes de que el documento fuera obligatorio no lo tienen, y
          **esta pantalla es donde esa ausencia se ve** — es la que sirve para
          saber a quién hay que completar.

          **El país y el tipo de documento se devuelven aunque estén inactivos.**
          Retirarlos del catálogo los quita de los desplegables del alta; no
          cambia dónde está ni con qué se identifica quien ya los tenía, ni lo
          oculta a quien administra.

          **No devuelve intentos fallidos** —diría cuántos le quedan a una cuenta
          antes de bloquearse—, **ni dato alguno de la credencial**, **ni el
          superior comercial**, que tiene su propio endpoint.

          Una persona eliminada devuelve el **mismo** `404` que una inexistente,
          sin ninguna pista de que existió.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La persona.",
        content = @Content(schema = @Schema(implementation = UserDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "El identificador no es un UUID en forma canónica (`VAL-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `users:read` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe, o está eliminada (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public UserDetailResponse detalle(@PathVariable UUID id) {
    return detalleDeUsuario.detail(id);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('users:update')")
  @Operation(
      summary = "Editar los datos de una persona",
      description =
          """
          Modifica el nombre, los apellidos, el correo, el país, la **identidad
          documental** y los **datos de contacto**. `PATCH` y no `PUT`:
          `PUT` obligaría a enviar el recurso completo —incluidos el nombre de
          usuario, el estado y los roles, que esta operación **no** puede
          modificar— y habría que decidir qué hacer si llegaran con otros valores.

          **Todos los campos son opcionales, y el nulo explícito NO significa lo
          mismo en todos.** El campo ausente nunca se toca; con el nulo hay **dos
          familias**, y la línea que las separa es la de lo obligatorio y lo
          opcional:

          - **Lo rechazan con `400`**: `firstName`, `lastName`, `email`,
            `countryId`, `documentTypeId`, `documentNumber` y `phone`. Su columna
            no admite ausencia, y aceptarlo produciría un `500` en lugar del `400`
            que corresponde.
          - **Lo aceptan y VACÍAN el campo**: `addressLine1`, `addressLine2`,
            `city` y `companyPhone`. Son opcionales, y «ya no vive ahí» —o «ya no
            tiene ese número»— es un hecho que hay que poder registrar.

          **`companyPhone` cae del lado que vacía aunque sea un teléfono**, y es la
          comprobación de que la línea está bien trazada: comparte forma y
          validación con `phone` y aun así va del otro lado, porque lo que decide
          no es qué dato es sino si `RN-SP-037` lo exige.

          **El tipo y el número de documento se envían juntos o no se envían.**
          Enviar uno solo devuelve `400`: un número sin decir de qué documento es
          no significa nada.

          **Esta es la ÚNICA operación que cambia el documento y el país.** El
          titular no puede tocarlos desde `PATCH /api/v1/users/me`.

          **Se comprueba el destino, nunca el actual.** Un país o un tipo de
          documento inactivos se rechazan **como destino**; que los vigentes de
          la persona lo estén no impide editarla — es justamente para eso que
          existe esta operación.

          **Corregir el documento no libera el anterior.** Es la diferencia
          deliberada con el correo, que sí queda libre: un documento identifica a
          alguien en el mundo real, y liberarlo permitiría que otra ficha lo
          tomara.

          **El nombre de usuario no se puede cambiar**, y enviarlo devuelve `400`
          por propiedad desconocida en lugar de ignorarse en silencio. Lo mismo
          con el estado, los roles, la membresía y la contraseña: cada uno tiene
          su operación.

          El correo se normaliza —recorte y minúsculas— **antes** de compararse,
          de modo que reenviar el propio en mayúsculas es un cambio sin efecto y
          no un conflicto consigo mismo.

          **El actor sí puede editarse a sí mismo**: corregir el propio apellido
          no concede ningún privilegio.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La persona, con los datos actualizados.",
        content = @Content(schema = @Schema(implementation = UserResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Ningún campo informado (`VAL-001`), campo vaciado que no admite vaciarse (`VAL-002`,"
                + " `VAL-006` país, `VAL-007` documento, `VAL-008` teléfono), tipo y número de"
                + " documento sin su pareja (`VAL-007`), correo inválido (`VAL-003`), longitud"
                + " excedida (`VAL-005`) o campo desconocido —incluidos el nombre de usuario, el"
                + " estado, los roles y la contraseña",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `users:update` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe, o está eliminada (`EX-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "El correo ya está en uso (`RN-SP-016`). **El mensaje no dice de quién es**: puede ser"
                + " de alguien eliminado, y decirlo revelaría una cuenta",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public UserResponse editar(@PathVariable UUID id, @RequestBody UpdateUserRequest peticion) {
    return edicion.update(id, peticion);
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAuthority('users:change-status')")
  @Operation(
      summary = "Retirar o devolver el acceso de una persona",
      description =
          """
          Subrecurso propio y no un campo de la edición: el estado tiene reglas de
          rechazo que la edición no tiene y exige un motivo que la edición no
          admite.

          **Se envía el estado destino y no una acción**, lo que hace la operación
          idempotente por construcción. Pedir el estado que ya se tiene no cambia
          nada y no deja evento.

          **Salvo en un caso, y es el que da sentido al requerimiento:** pasar de
          bloqueo **automático** a bloqueo **manual** sí es un cambio aunque el
          estado sea el mismo — `lockedUntil` pasa de informado a nulo, y con ello
          el bloqueo deja de levantarse solo.

          **El motivo es condicional en los dos sentidos:** obligatorio al retirar
          el acceso, **rechazado** al devolverlo.

          **`PENDIENTE` no se admite**, aunque el esquema lo acepte: ningún
          requerimiento lo produce y sería el único camino hacia un estado del que
          nadie sabe salir.

          **Reactivar nunca falla por regla.** Devolver el acceso no puede dejar a
          nadie sin administración ni a ningún equipo huérfano.

          Retirar el acceso **revoca todas las sesiones** de la persona.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El estado resultante. `lockedUntil` nulo con BLOQUEADO significa manual.",
        content = @Content(schema = @Schema(implementation = UserStatusResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Estado ausente, fuera del dominio o `PENDIENTE` (`VAL-001`); motivo ausente al retirar"
                + " (`VAL-005`) o presente al devolver (`VAL-006`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description =
            "Autenticado sin `users:change-status` (`AUTH-002`), o es la cuenta del propio actor"
                + " (`RN-SP-017`) — dos casos distintos con `error_code` distinto",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe, o está eliminada (`EX-005`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "Es el último superadministrador activo (`RN-SP-001`), o tiene personas a cargo"
                + " (`RN-SP-022`) — este último dice **cuántas**, nunca quiénes",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public UserStatusResponse cambiarEstado(
      @PathVariable UUID id, @RequestBody ChangeUserStatusRequest peticion) {
    return cambioDeEstado.change(id, peticion);
  }

  @PostMapping("/{id}/deletion")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('users:delete')")
  @Operation(
      summary = "Eliminar a una persona",
      description =
          """
          Eliminación **lógica** y con motivo declarado. `POST` sobre un
          subrecurso y no `DELETE` con cuerpo: RFC 9110 no define semántica para
          el cuerpo de un `DELETE` y un intermediario puede descartarlo,
          convirtiendo la petición en un rechazo por motivo ausente que el actor
          no puede entender. Y tampoco por *query string*, o el motivo acabaría en
          los registros de acceso de los proxies.

          **El motivo se verifica el primero de todo**, antes incluso de saber si
          la persona existe.

          La operación **retira sus roles y su membresía**, **cierra** su
          asignación de superior —cerrarla, nunca borrarla: es historial de
          mando— y **revoca sus sesiones**, todo en la misma transacción y con la
          misma marca de tiempo.

          **El estado NO se toca**: se conserva como estaba para que el registro
          de eliminación diga en qué situación estaba la persona cuando se la
          eliminó.

          El `404` **no distingue** «nunca existió» de «ya estaba eliminada».
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Persona eliminada.", content = @Content),
    @ApiResponse(
        responseCode = "400",
        description = "Motivo ausente o vacío (`VAL-001`), o campo desconocido",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description =
            "Autenticado sin `users:delete` (`AUTH-002`), o es la cuenta del propio actor"
                + " (`RN-SP-017`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe, o ya estaba eliminada (`EX-004`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "Es el último superadministrador activo (`RN-SP-001`), o tiene personas a cargo"
                + " (`RN-SP-022`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public void eliminar(@PathVariable UUID id, @RequestBody DeleteUserRequest peticion) {
    eliminacion.delete(id, peticion);
  }

  @PostMapping("/{id}/roles")
  @PreAuthorize("hasAuthority('users:assign-roles')")
  @Operation(
      summary = "Asignar roles a una persona",
      description =
          """
          Agrega roles. **No reemplaza la lista**: por eso es un `POST` sobre un
          subrecurso y no un `PUT`. Un reemplazo haría retiros implícitos que se
          saltarían tres reglas cuyo incumplimiento nadie vería.

          Pedir un rol que la persona ya tiene no es un error: no cambia nada y
          no deja rastro en la auditoría.

          **Esta operación no toca la membresía**, y desde el 05-09-2026 tampoco
          la admite: `membershipId` y `membershipEndsAt` se retiraron del cuerpo.
          Toda persona tiene nivel desde el alta, de modo que cuando llega esta
          petición ya lo tiene; cambiarlo es la operación de membresía, que tiene
          su propio permiso.

          `supervisorId` es **condicional**: obligatorio exactamente cuando la
          operación cambia el rango comercial de la persona, y no admitido en
          cualquier otro caso. Su admisibilidad depende del estado de la persona
          y no del cuerpo, de modo que su incumplimiento es `422` y nunca `400`.

          Un **ascenso** —que cambia el rol vendedor de mayor rango— exige
          declarar de nuevo el superior: el anterior puede haber dejado de ser
          admisible sin que nadie tocara esa fila.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La persona con su estructura actualizada.",
        content = @Content(schema = @Schema(implementation = UserResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Lista vacía, identificador malformado o más de 100 elementos",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `users:assign-roles` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "La persona no existe o está eliminada (`VAL-006`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "Algún rol concede permisos que el actor no posee (`RN-SEG-010`). El cuerpo enumera"
                + " cuáles",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description =
            "Rol inexistente (`EX-002`), rol inactivo (`EX-003`), vendedor sin"
                + " superior (`RN-SP-019`), o superior inadmisible (`VAL-007`, `RN-SP-020`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public UserResponse asignarRoles(
      @PathVariable UUID id, @Valid @RequestBody AssignRolesRequest peticion) {
    return asignacion.assign(id, peticion);
  }

  @PostMapping("/{id}/roles/revocations")
  @PreAuthorize("hasAuthority('users:revoke-roles')")
  @Operation(
      summary = "Retirar roles de una persona",
      description =
          """
          Retira roles y **arrastra UNA cascada**: quedarse sin ningún rol de
          vendedor cierra la asignación de superior comercial —cerrarla, nunca
          borrarla: esa fila dice a quién se atribuía cada resultado—.

          **La membresía ya no se arrastra** (05-09-2026). Quien deja de ser
          consumidor **conserva el nivel que tenía**, incluido uno comprado: toda
          persona debe tener membresía, y bajarla al suelo sería quitarle algo
          que pagó.

          **Revoca todas las sesiones de la persona.** Asignar no lo hace;
          retirar sí, porque el refresh token sobrevive al cambio de permisos y
          dejaría vivo hasta siete días el acceso que se acaba de quitar.

          Es un `POST` sobre un subrecurso y no un `DELETE`: la lista viaja en el
          cuerpo, y RFC 9110 no define semántica para el cuerpo de un `DELETE` —
          un intermediario puede descartarlo sin avisar, y el retiro llegaría sin
          roles.

          **No se pide motivo** y **no se comprueba que los roles existan**:
          retirar un rol eliminado del catálogo es legítimo, porque la asignación
          sigue ahí y debe poder soltarse.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La persona con su estructura actualizada.",
        content = @Content(schema = @Schema(implementation = UserResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Lista vacía, identificador malformado o más de 100 elementos",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `users:revoke-roles` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "La persona no existe o está eliminada (`VAL-006`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "El retiro dejaría al sistema sin superadministrador activo (`RN-SP-001`), algún rol"
                + " excede los permisos del actor (`RN-SEG-010`), o la persona tiene equipo a cargo"
                + " (`RN-SP-022`) — este último informa cuántas personas, nunca quiénes",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public UserResponse retirarRoles(
      @PathVariable UUID id, @Valid @RequestBody RevokeRolesRequest peticion) {
    return retiro.revoke(id, peticion);
  }

  @PutMapping("/{id}/membership")
  @PreAuthorize("hasAuthority('users:assign-membership')")
  @Operation(
      summary = "Fijar la membresía de una persona",
      description =
          """
          **`PUT` y no `POST`**, al revés que la asignación de roles, y la
          diferencia no es de gusto: aquí el cuerpo **sí** representa el estado
          final. La persona tiene siempre exactamente una, de modo que enviar una
          la deja como la única — y de ahí sale gratis la idempotencia.

          `endsAt` es opcional. **Ausente significa indefinida**: enviarlo ausente
          sobre una membresía que tenía fecha la convierte en indefinida, y es un
          caso normal, no un olvido que haya que interpretar. Presente, la
          membresía deja de estar vigente **al llegar** ese instante, no después.

          Repetir la petición idéntica no escribe ni deja auditoría. Cambiar solo
          la fecha sí es un cambio y sí se registra.

          Devuelve `200` incluso la primera vez: `PUT` sobre una ruta fija no crea
          un recurso direccionable nuevo.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La membresía, con su nivel y su vigencia.",
        content = @Content(schema = @Schema(implementation = UserMembershipResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Membresía ausente o malformada (`VAL-001`), o fecha de fin igual o anterior al momento"
                + " de la asignación (`VAL-005`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `users:assign-membership` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "La persona no existe o está eliminada (`VAL-004`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "La membresía indicada no existe en la cadena (`VAL-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public UserMembershipResponse fijarMembresia(
      @PathVariable UUID id, @Valid @RequestBody AssignMembershipRequest peticion) {
    return membresia.assign(id, peticion);
  }

  @DeleteMapping("/{id}/membership")
  @PreAuthorize("hasAuthority('users:revoke-membership')")
  @Operation(
      summary = "Devolver la membresía de una persona al suelo",
      description =
          """
          **Devuelve al nivel de arranque, y no deja a nadie sin nivel.** Desde el
          05-09-2026 `RN-SP-018` exige que **toda** persona tenga membresía, de
          modo que esta operación cierra la que tenga y le abre una `BECA`.

          Existe para **corregir un nivel concedido por error**. Bajar a alguien a
          un nivel intermedio es la operación de membresía, que admite indicar
          cuál.

          **Responde `200` con la membresía resultante**, no `204`: devolver un
          cuerpo vacío diría que no queda nada, y queda el nivel de arranque —
          quien llama necesita saber en qué quedó la persona sin volver a
          preguntar.

          **Es idempotente.** Aplicada sobre quien ya está en el suelo no escribe
          ni audita, y devuelve lo mismo.

          **Ya no rechaza a los consumidores.** Hasta el 05-09-2026 exigía que la
          persona **no** portara ningún rol de consumidor, porque la regla de
          entonces no admitía consumidores sin nivel; retirada `RN-SP-013`, esa
          precondición no protege nada.

          **Conserva el `DELETE`** y no le alcanza la enmienda del retiro de
          roles: esta operación no lleva cuerpo, de modo que el problema que
          aquella evitaba no existe aquí.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La persona queda en el nivel de arranque, que se devuelve."),
    @ApiResponse(
        responseCode = "400",
        description = "Identificador malformado (`VAL-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `users:revoke-membership` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "La persona no existe o está eliminada (`VAL-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public UserMembershipResponse devolverMembresiaAlSuelo(@PathVariable UUID id) {
    return retiroDeMembresia.resetToFloor(id);
  }

  @PatchMapping("/{id}/supervisor")
  @PreAuthorize("hasAuthority('users:assign-supervisor')")
  @Operation(
      summary = "Establecer o cambiar el superior comercial",
      description =
          """
          `PATCH` sobre el subrecurso y no `PUT`: el cuerpo no representa el
          estado completo —falta el periodo, que lo fija el sistema— y `PUT`
          invitaría a pensar que se puede enviar.

          **No admite fecha de inicio.** La asignación rige desde que se ejecuta,
          siempre. Declararla obligaría a especificar solapamientos, huecos entre
          tramos y correcciones retroactivas sobre periodos ya liquidados.

          **No admite retirar el superior.** El estado «vendedor sin superior» no
          existe: la única salida es dejar de portar rol comercial retirándolo.

          Cada cambio **cierra** el tramo vigente con su fecha y **abre** otro:
          nunca se sobrescribe el superior de una fila. La respuesta devuelve el
          **anterior con su fecha de cierre**, que es lo que permite confirmar de
          un vistazo que se cerró el tramo que se creía cerrar.

          **El equipo se mueve con el reasignado**: sus subordinados conservan su
          superior y solo cambia de quién depende la rama.

          Repetir la operación con el mismo superior no cierra ni abre nada y no
          deja auditoría — pero el motivo se exige igual, antes de saberlo.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La estructura, con el superior nuevo y el anterior.",
        content = @Content(schema = @Schema(implementation = CommercialStructureResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Identificador malformado, o motivo ausente o vacío (`VAL-001`, `VAL-008`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `users:assign-supervisor` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "Alguna de las dos personas no existe o está eliminada (`VAL-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "El actor es el propio subordinado (`RN-SP-017`), el subordinado no pertenece a la"
                + " fuerza comercial (`VAL-003`) o es la cúspide (`VAL-004`), el superior no porta"
                + " el rol que exige el orden de mando (`RN-SP-020`) —el mensaje dice cuál—, no"
                + " está activo (`VAL-006`), o sería su propio superior (`VAL-007`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public CommercialStructureResponse asignarSuperior(
      @PathVariable UUID id, @Valid @RequestBody AssignSupervisorRequest peticion) {
    return superior.assign(id, peticion);
  }

  @GetMapping("/{id}/team")
  @PreAuthorize("hasAuthority('users:read-team')")
  @Operation(
      summary = "Consultar el superior y el equipo a cargo",
      description =
          """
          Devuelve el superior inmediato y el **equipo directo** de la persona,
          paginado.

          **Un solo nivel, nunca el árbol descendente**, y **sin conteo
          indirecto**: `totalElements` cuenta a quienes reportan directamente.
          Devolver la rama completa publicaría de una vez la estructura de la
          empresa por un permiso de lectura de usuarios.

          **Cada persona lleva `roles`: TODOS los que porta**, con `id`, `code`
          y `name`, ordenados por código y **presentes aunque la lista vaya
          vacía**. Es el mismo objeto que devuelve `GET /api/v1/users` en cada
          fila. **Sustituye a `roleCode`** (10-09-2026), que traía uno solo y
          únicamente si era de la fuerza comercial: la cartera de clientes
          llegaba con el rol en nulo y era indistinguible de un vendedor sin rol.

          **`roles` es además el único filtro**, y acota **el equipo**. Se pasan
          **códigos**, varios admitidos —`?roles=AGENTE,CLIENTE` o repitiendo el
          parámetro—, y entra quien porte **alguno** de ellos. `totalElements`
          **cuenta lo filtrado**. Un código que no existe devuelve el equipo
          vacío con `200`, **no un error**: es el mismo criterio que el filtro
          por rol de `GET /api/v1/users`.

          **El filtro NO toca al superior ni a la persona consultada.** Los dos
          se devuelven igual aunque no porten ninguno de los roles pedidos, y eso
          es contrato: `supervisor` va **ausente**, no en nulo, **solo** cuando
          la persona es la cúspide comercial, que es lo que distingue «no depende
          de nadie» de «no se pudo resolver».

          **Ningún otro filtro.** Ni búsqueda por nombre, ni estado, ni país: el
          listado general de usuarios ya los tiene, y replicarlos aquí obligaría
          a mantener dos semánticas sincronizadas.

          **Sin historial de superiores.**

          Quien no pertenece a la fuerza comercial recibe `200` con la estructura
          vacía, no `404` ni `409` — **con sus roles a la vista**: no tener
          estructura comercial no es no tener roles.

          **El alcance es global** mientras la decisión D-22 siga abierta: quien
          posea el permiso ve el equipo de cualquiera, no solo el suyo.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description =
            "La estructura, con el equipo paginado y los roles de cada persona. El filtro por"
                + " roles acota el equipo y su total, nunca al superior.",
        content = @Content(schema = @Schema(implementation = CommercialStructureResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Identificador malformado o paginación fuera de límites (`VAL-003`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `users:read-team` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "La persona no existe o está eliminada (`VAL-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public CommercialStructureResponse equipo(
      @PathVariable UUID id,
      // SIN validar contra el catálogo de roles, a propósito: un código que no
      // existe devuelve el equipo vacío y no un 400. Validarlo añadiría una
      // consulta por petición para producir un fallo que la especificación no
      // quiere, y `RF-SP-025` ya decidió lo mismo para su filtro por rol.
      @Parameter(
              description =
                  "Códigos de rol que acotan el equipo. Varios admitidos, con semántica O.")
          @RequestParam(required = false)
          List<String> roles,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return equipoACargo.team(id, roles, page, size);
  }

  @GetMapping("/me/team/broker-accounts")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Consultar las cuentas de broker de mi equipo",
      description =
          """
          Devuelve, **paginadas**, las cuentas de broker de todas las personas
          que dependen **directamente** del actor, **cada fila con su titular**.

          **No hay identificador en la ruta y no lo habrá**: el conjunto de datos
          lo determina el sistema a partir de quién pregunta. Quien deba ver las
          cuentas de otra persona usa `GET /api/v1/users/{id}/broker-accounts`
          con `broker-accounts:read`.

          **No exige ningún permiso**, solo estar autenticado: el alcance lo pone
          la estructura comercial (`RN-SP-046`). Quien **no tiene equipo** recibe
          `200` con la página vacía, no `404` ni `403`.

          **Un solo nivel**, el equipo directo. Las cuentas de quien depende de
          un subordinado del actor **no aparecen**: devolver la rama completa
          publicaría de una vez la estructura de la empresa.

          **Es un listado de CUENTAS y no de personas.** Quien está en el equipo
          y no declaró ninguna cuenta **no aparece**, y quien declaró dos aparece
          **dos veces**. Quién hay en el equipo lo responde
          `GET /api/v1/users/{id}/team`.

          **Filtros, ambos opcionales y combinables con Y:**

          - `status` — `REGISTER` o `FIRST_DEPOSIT`. **Cualquier otro valor es
            `400`**, no una página vacía: una página vacía sería indistinguible
            de «nadie está en ese estado».
          - `brokerId` — un broker del catálogo. **Un identificador inexistente
            devuelve la página vacía sin error**, al revés que `status`: es una
            pregunta legítima con respuesta vacía.

          `totalElements` **cuenta lo filtrado**.

          **Hoy todas las cuentas están en `REGISTER`**, y no es un fallo de esta
          consulta: quien mueve una cuenta a `FIRST_DEPOSIT` es el webhook del
          broker, que todavía no existe. Mientras no exista, ninguna cuenta tiene
          depósito confirmado.

          **`brokerUsername` llega en nulo** mientras el broker no lo haya
          confirmado, y el campo **está presente**: su nulo significa «aún no
          confirmado», no «sin nombre».
          """)
  @ApiResponses({
    // SIN `@Schema(implementation = PageResponse.class)`, y la ausencia importa:
    // ese anotado publica la envoltura CRUDA —`content` sin tipo—, de modo que
    // el cliente generado no sabría qué hay en cada fila. Dejando que springdoc
    // use el tipo de retorno emite `PageResponseTeamBrokerAccountItem`, con la
    // fila dentro. Es lo que ya hace `GET /api/v1/movements/mine`.
    @ApiResponse(
        responseCode = "200",
        description =
            "Página de cuentas del equipo directo, ordenada por titular, broker e identificador"
                + " de cuenta. Vacía si el actor no tiene equipo."),
    @ApiResponse(
        responseCode = "400",
        description =
            "`status` fuera de `REGISTER`/`FIRST_DEPOSIT` (`VAL-001`), `brokerId` malformado o"
                + " paginación fuera de límites (`VAL-003`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public PageResponse<TeamBrokerAccountItem> cuentasDeBrokerDelEquipo(
      @Parameter(description = "Estado de la cuenta: `REGISTER` o `FIRST_DEPOSIT`.")
          @RequestParam(required = false)
          String status,
      @Parameter(description = "Broker del catálogo. Uno inexistente devuelve la página vacía.")
          @RequestParam(required = false)
          UUID brokerId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return cuentasDelEquipo.ofMyTeam(status, brokerId, page, size);
  }

  // SIN @PreAuthorize A PROPÓSITO, y no es un olvido: la autorización de esta
  // ruta no es una función del actor sino DEL PAR (actor, persona consultada)
  // —el permiso, o ser su superior vigente—, y expresarla en SpEL metería una
  // consulta a la base dentro de una anotación, donde no se prueba ni se
  // depura. Vive en `GetBrokerAccountsService`. Consta así en
  // `EndpointPermissionsIT`.
  @GetMapping("/{id}/broker-accounts")
  @Operation(
      summary = "Consultar las cuentas de broker de una persona",
      description =
          """
          Devuelve las cuentas de broker de esa persona: **broker, identificador
          de cuenta, nombre de usuario en el broker y estado**, ordenadas por
          nombre de broker e identificador de cuenta.

          **Quién puede consultarlas** (`RN-SP-046`): **su superior comercial
          vigente**, sin ningún permiso, o quien traiga **`broker-accounts:read`**
          sobre cualquiera. Es la primera lectura del sistema cuyo alcance sale
          de la estructura comercial, y **solo alcanza a esta**.

          **Quien no es ninguna de las dos cosas recibe `404`, no `403`**, y es
          deliberado: el mismo `404` que si la persona no existiera. Un `403`
          dejaría a cualquier vendedor recorrer identificadores y averiguar
          cuáles corresponden a personas reales. Los dos cuerpos son idénticos.

          **Quien FUE su superior y ya no lo es recibe `404`**: el historial de
          la estructura no concede lectura.

          **El titular NO ve aquí sus propias cuentas** salvo que traiga el
          permiso: esta lectura se definió sobre el equipo.

          **Una persona sin cuentas devuelve `200` con la colección vacía**, no
          `404`: solo el registro por un enlace de beca obliga a declararlas.

          **Hoy el estado es siempre `REGISTER`**, y no es un fallo: quien mueve
          una cuenta a `FIRST_DEPOSIT` es el webhook del broker, que todavía no
          existe.

          **`brokerUsername` llega en nulo** mientras el broker no lo haya
          confirmado, y el campo **está presente**: su nulo significa «aún no
          confirmado», no «sin nombre».

          **No se pagina**: una persona tiene unas pocas cuentas. El listado
          paginado es `GET /api/v1/users/me/team/broker-accounts`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Las cuentas de la persona, ordenadas. Vacía si no declaró ninguna.",
        content = @Content(schema = @Schema(implementation = BrokerAccountsResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Identificador malformado (`VAL-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description =
            "La persona no existe, está eliminada, **o el actor no es su superior vigente ni"
                + " posee `broker-accounts:read`** (`VAL-002`). Los tres casos responden lo"
                + " mismo, a propósito.",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public BrokerAccountsResponse cuentasDeBrokerDe(@PathVariable UUID id) {
    return cuentasDeBroker.of(id);
  }
}
