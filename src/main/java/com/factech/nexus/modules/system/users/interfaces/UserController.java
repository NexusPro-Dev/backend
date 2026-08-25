package com.factech.nexus.modules.system.users.interfaces;

import com.factech.nexus.modules.system.users.application.AssignMembershipRequest;
import com.factech.nexus.modules.system.users.application.AssignRolesRequest;
import com.factech.nexus.modules.system.users.application.AssignSupervisorRequest;
import com.factech.nexus.modules.system.users.application.ChangeUserStatusRequest;
import com.factech.nexus.modules.system.users.application.CommercialStructureResponse;
import com.factech.nexus.modules.system.users.application.DeleteUserRequest;
import com.factech.nexus.modules.system.users.application.ListUsersRequest;
import com.factech.nexus.modules.system.users.application.OwnProfileResponse;
import com.factech.nexus.modules.system.users.application.RegisterUserRequest;
import com.factech.nexus.modules.system.users.application.ResetPasswordRequest;
import com.factech.nexus.modules.system.users.application.RevokeRolesRequest;
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
import com.factech.nexus.modules.system.users.domain.service.GetCommercialTeamService;
import com.factech.nexus.modules.system.users.domain.service.GetOwnProfileService;
import com.factech.nexus.modules.system.users.domain.service.GetUserService;
import com.factech.nexus.modules.system.users.domain.service.ListUsersService;
import com.factech.nexus.modules.system.users.domain.service.RegisterUserService;
import com.factech.nexus.modules.system.users.domain.service.ResetUserPasswordService;
import com.factech.nexus.modules.system.users.domain.service.RevokeUserMembershipService;
import com.factech.nexus.modules.system.users.domain.service.RevokeUserRolesService;
import com.factech.nexus.modules.system.users.domain.service.UpdateUserService;
import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
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
  private final ResetUserPasswordService restablecimiento;

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
      ResetUserPasswordService restablecimiento) {
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

          **La membresía y el superior son condicionales en los dos sentidos.**
          Un rol de consumidor exige membresía y la membresía exige un rol de
          consumidor; lo mismo con el rol de vendedor y el superior comercial.
          Indicar uno sin el otro devuelve `409`, no se ignora.

          El superior debe portar el **rol padre inmediato** del rol vendedor de
          mayor rango de la persona, y estar activo.

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
            "Formato u obligatoriedad incumplidos, contraseña que no cumple la política, o campo"
                + " no admitido en el cuerpo",
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
            "Identidad ya en uso (`RN-SP-016`), rol que excede los privilegios del actor"
                + " (`RN-SEG-010`), consumidor sin membresía o al revés (`RN-SP-018`), vendedor sin"
                + " superior o al revés (`RN-SP-019`), o superior que no porta el rol padre"
                + " (`RN-SP-020`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "Algún rol no existe o no está activo (`EX-003`)",
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
  @PreAuthorize("hasAuthority('users:read')")
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

          **`roleId` y `membershipId` no se validan contra su catálogo.** Un
          filtro por algo inexistente devuelve la colección vacía y no es un
          error.

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
        description = "Autenticado sin `users:read` (`AUTH-002`)",
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
          Modifica el nombre, los apellidos y el correo. `PATCH` y no `PUT`:
          `PUT` obligaría a enviar el recurso completo —incluidos el nombre de
          usuario, el estado y los roles, que esta operación **no** puede
          modificar— y habría que decidir qué hacer si llegaran con otros valores.

          **Los tres campos son opcionales y ninguno admite vaciarse.** El campo
          ausente no se toca; el campo con nulo explícito o en blanco devuelve
          `400`. Es la diferencia con la edición de un rol, donde el nulo sí era
          una orden: aquí las columnas son `NOT NULL` y aceptarlo produciría un
          `500` en lugar del `400` que corresponde.

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
            "Ningún campo informado (`VAL-001`), campo vaciado (`VAL-002`), correo inválido"
                + " (`VAL-003`), longitud excedida (`VAL-005`) o campo desconocido",
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
  @PreAuthorize("hasAuthority('users:update')")
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
            "Autenticado sin `users:update` (`AUTH-002`), o es la cuenta del propio actor"
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

          `membershipId`, `membershipEndsAt` y `supervisorId` son
          **condicionales**: obligatorios exactamente cuando la operación
          convierte a la persona en consumidor o cambia su rango comercial, y no
          admitidos en cualquier otro caso. Su admisibilidad depende del estado
          de la persona y no del cuerpo, de modo que su incumplimiento es `422` y
          nunca `400`.

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
            "Rol inexistente (`EX-002`), rol inactivo (`EX-003`), consumidor sin membresía"
                + " (`RN-SP-018`), membresía indicada sin que corresponda (`EX-006`), vendedor sin"
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
  @PreAuthorize("hasAuthority('users:assign-roles')")
  @Operation(
      summary = "Retirar roles de una persona",
      description =
          """
          Retira roles y **arrastra las cascadas**: quedarse sin ningún rol de
          consumidor borra la membresía, y quedarse sin ningún rol de vendedor
          cierra la asignación de superior comercial —cerrarla, nunca borrarla:
          esa fila dice a quién se atribuía cada resultado—.

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
        description = "Autenticado sin `users:assign-roles` (`AUTH-002`)",
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
          final. La persona tiene una membresía o ninguna, de modo que enviar una
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
        responseCode = "409",
        description =
            "La persona no porta ningún rol de consumidor (`RN-SP-013`). El cuerpo indica que"
                + " primero corresponde asignarle uno",
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
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('users:assign-membership')")
  @Operation(
      summary = "Retirar la membresía de una persona",
      description =
          """
          **Rechaza a quien SÍ es consumidor**, que es lo contrario de lo que
          sugiere el nombre. No existe el estado «consumidor sin nivel», de modo
          que esta operación solo sirve para **corregir un estado incoherente**:
          alguien con membresía que ya no porta ningún rol de consumidor.

          Las dos salidas reales para un consumidor en activo son bajarlo de nivel
          con la operación de membresía, o retirarle el rol — el retiro arrastra la
          membresía por su cuenta. El cuerpo del `409` las cita.

          Sin membresía previa devuelve `204` igual: la operación es idempotente y
          su resultado ya se cumplía.

          **Conserva el `DELETE`** y no le alcanza la enmienda del retiro de
          roles: esta operación no lleva cuerpo, de modo que el problema que
          aquella evitaba no existe aquí.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Membresía retirada.", content = @Content),
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
        description = "Autenticado sin `users:assign-membership` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "La persona no existe o está eliminada (`VAL-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "La persona porta al menos un rol de consumidor (`RN-SP-018`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public void retirarMembresia(@PathVariable UUID id) {
    retiroDeMembresia.revoke(id);
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
  @PreAuthorize("hasAuthority('users:read')")
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

          **Sin filtros.** El listado general de usuarios ya filtra; replicar esa
          semántica sobre un subconjunto que cabe en una o dos páginas obligaría
          a mantener dos filtrados sincronizados sin responder nada nuevo.

          **Sin historial de superiores.**

          `supervisor` va **ausente**, no en nulo, cuando la persona es la cúspide
          comercial: es lo que distingue «no depende de nadie» de «no se pudo
          resolver».

          Quien no pertenece a la fuerza comercial recibe `200` con la estructura
          vacía, no `404` ni `409`.

          **El alcance es global** mientras la decisión D-22 siga abierta: quien
          posea el permiso ve el equipo de cualquiera, no solo el suyo.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La estructura, con el equipo paginado.",
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
        description = "Autenticado sin `users:read` (`AUTH-002`)",
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
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return equipoACargo.team(id, page, size);
  }
}
