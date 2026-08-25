package com.factech.nexus.modules.system.roles.interfaces;

import com.factech.nexus.modules.system.roles.application.ChangeRoleParentRequest;
import com.factech.nexus.modules.system.roles.application.ChangeRoleStatusRequest;
import com.factech.nexus.modules.system.roles.application.CreateRoleRequest;
import com.factech.nexus.modules.system.roles.application.DeleteRoleRequest;
import com.factech.nexus.modules.system.roles.application.ListRolesRequest;
import com.factech.nexus.modules.system.roles.application.RoleDetailResponse;
import com.factech.nexus.modules.system.roles.application.RoleListItem;
import com.factech.nexus.modules.system.roles.application.RolePermissionsRequest;
import com.factech.nexus.modules.system.roles.application.RoleResponse;
import com.factech.nexus.modules.system.roles.application.UpdateRoleRequest;
import com.factech.nexus.modules.system.roles.domain.service.ChangeRoleParentService;
import com.factech.nexus.modules.system.roles.domain.service.ChangeRoleStatusService;
import com.factech.nexus.modules.system.roles.domain.service.CreateRoleService;
import com.factech.nexus.modules.system.roles.domain.service.DeleteRoleService;
import com.factech.nexus.modules.system.roles.domain.service.GetRoleDetailService;
import com.factech.nexus.modules.system.roles.domain.service.GrantRolePermissionsService;
import com.factech.nexus.modules.system.roles.domain.service.ListRolesService;
import com.factech.nexus.modules.system.roles.domain.service.RevokeRolePermissionsService;
import com.factech.nexus.modules.system.roles.domain.service.UpdateRoleService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Roles del sistema (`RF-SP-001` · `T-17`).
 *
 * <p><b>El permiso se declara sobre el método</b> y no sobre la clase (`security.md` §6): un
 * endpoint sin declaración queda inaccesible, no público, y declararlo por clase haría que un
 * método añadido más tarde heredara en silencio un permiso que quizá no le corresponde.
 *
 * <p><b>El {@code 403} lo produce la capa de seguridad antes de entrar al caso de uso.</b> La
 * comprobación de {@code @PreAuthorize} ocurre en el interceptor de seguridad de método, de modo
 * que `CA-SP-008` se satisface ahí y no en {@code CreateRoleService}; el evento de denegación lo
 * emite el manejador global, que es quien ve a la vez la excepción y el contexto de la petición.
 *
 * <p><b>El controlador no decide códigos de estado más allá del camino feliz.</b> Todo rechazo sale
 * como excepción del dominio y lo traduce el manejador global, que es el único lugar del código que
 * los decide (`development-guide.md` §7.1).
 */
@RestController
@RequestMapping("/api/v1/roles")
@Tag(name = "Roles", description = "Registro y administración de los roles del sistema.")
public class RoleController {

  private final CreateRoleService alta;
  private final ListRolesService listado;
  private final GetRoleDetailService detalleDelRol;
  private final UpdateRoleService edicion;
  private final ChangeRoleStatusService estado;
  private final ChangeRoleParentService padre;
  private final GrantRolePermissionsService concesion;
  private final RevokeRolePermissionsService revocacion;
  private final DeleteRoleService baja;

  public RoleController(
      CreateRoleService alta,
      ListRolesService listado,
      GetRoleDetailService detalleDelRol,
      UpdateRoleService edicion,
      ChangeRoleStatusService estado,
      ChangeRoleParentService padre,
      GrantRolePermissionsService concesion,
      RevokeRolePermissionsService revocacion,
      DeleteRoleService baja) {
    this.alta = alta;
    this.listado = listado;
    this.detalleDelRol = detalleDelRol;
    this.edicion = edicion;
    this.estado = estado;
    this.padre = padre;
    this.concesion = concesion;
    this.revocacion = revocacion;
    this.baja = baja;
  }

  /**
   * Registra un rol con sus permisos iniciales.
   *
   * <p>Devuelve {@code 201} con la cabecera {@code Location}, que es lo que el Art. VIII exige de
   * una creación: el cliente obtiene la dirección del recurso nuevo sin tener que componerla.
   */
  @PostMapping
  @PreAuthorize("hasAuthority('roles:create')")
  @Operation(
      summary = "Registrar un rol",
      description =
          """
          Registra un rol bajo un rol padre existente y activo, con los permisos que
          se declaren.

          Los permisos deben estar contenidos en los del rol padre (`RN-SEG-003`) y en
          los permisos efectivos de quien ejecuta el alta (`RN-SEG-010`). Declararlos es
          opcional: sin ellos el rol queda registrado y a la espera de `RF-SP-005`.

          El rol nace siempre **activo** y nunca de sistema. Ni `status` ni `isSystem`
          se admiten en el cuerpo: enviarlos devuelve `400`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Rol registrado. La cabecera `Location` lleva su dirección.",
        content = @Content(schema = @Schema(implementation = RoleResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Formato u obligatoriedad incumplidos (`VAL-001` a `VAL-004`, `VAL-007`, `VAL-008`),"
                + " o el cuerpo trae un campo que este endpoint no admite",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `roles:create` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "Código o nombre ya en uso (`RN-SEG-001`), permiso fuera del rol padre (`RN-SEG-003`)"
                + " o fuera del alcance del actor (`RN-SEG-010`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description =
            "El rol padre no existe o no está activo (`EX-002`), o algún permiso no está en el"
                + " catálogo (`EX-005`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public ResponseEntity<RoleResponse> registrar(@Valid @RequestBody CreateRoleRequest peticion) {
    RoleResponse creado = alta.create(peticion.toCommand());
    return ResponseEntity.created(URI.create("/api/v1/roles/" + creado.id())).body(creado);
  }

  /**
   * Listado paginado de roles (`RF-SP-002` · `T-10`).
   *
   * <p>Devuelve {@code 200} incluso sin coincidencias: la colección vacía es una respuesta, no un
   * error, y tratarla como {@code 404} obligaría al cliente a distinguir «no hay» de «falló».
   */
  @GetMapping
  @PreAuthorize("hasAuthority('roles:read')")
  @Operation(
      summary = "Consultar roles",
      description =
          """
          Listado paginado con filtros, búsqueda y ordenamiento. Es la entrada
          natural a la administración de accesos: de aquí se navega al detalle de
          cada rol.

          **El orden por defecto es `code,asc`**: el código es el identificador
          con el que se habla de un rol en la documentación y en la auditoría.
          Solo se puede ordenar por la lista blanca —`code`, `name`, `roleType`,
          `status`, `createdAt`, `updatedAt`—; cualquier otro campo devuelve
          `400` y **no llega a la base de datos**.

          **No incluye los permisos de cada rol** ni el número de usuarios
          asignados: la primera pregunta la responde el detalle
          (`GET /api/v1/roles/{id}`) y la segunda se hace sobre un rol concreto,
          no sobre la lista.

          **`parentRoleId` no se valida contra el catálogo.** Filtra por el padre
          **directo** —no por el subárbol— y uno inexistente devuelve la
          colección vacía, que no es un error.

          La búsqueda va sobre código y nombre, **sin distinguir acentos ni
          mayúsculas**, y por fragmento: `academico` encuentra
          `LIDER_ACADEMICO`. Un término en blanco equivale a no filtrar.

          **Los eliminados quedan fuera salvo que se pidan** con
          `includeDeleted=true`; cuando se piden, `deletedAt` es lo que permite
          distinguirlos.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Página de roles.",
        content = @Content(schema = @Schema(implementation = PageResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Paginación fuera de límites, campo de ordenamiento no admitido (`VAL-003`), o"
                + " `status`/`roleType` fuera de su dominio (`VAL-004`). Se evalúan y se devuelven"
                + " **juntos** en `errors`",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `roles:read` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public PageResponse<RoleListItem> listar(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute ListRolesRequest filtros) {
    return listado.list(filtros);
  }

  /**
   * Detalle de un rol (`RF-SP-003`).
   *
   * <p><b>El identificador se convierte con {@code CanonicalUuidConverter}</b> y no con la
   * conversión por omisión: {@code UUID.fromString} del JDK acepta formas no canónicas —{@code
   * 1-1-1-1-1} se convierte sin error—, de modo que un identificador que es basura sintáctica
   * llegaría a la consulta y devolvería {@code 404} donde `spec.md` §13 exige {@code 400}.
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('roles:read')")
  @Operation(
      summary = "Consultar el detalle de un rol",
      description =
          """
          Devuelve el alcance exacto de un rol: **la lista completa de los
          permisos que declara**, su rol padre, **cuántos** roles cuelgan de él y
          **cuántas** personas lo tienen asignado.

          **Los permisos son los declarados, sin recorrer la cadena de
          ancestros** (`RN-SEG-004`): el modelo no usa herencia, y esa decisión
          existe precisamente para que esta pregunta se responda leyendo una sola
          lista. Van completos y sin paginar, ordenados por código.

          **`childRoleCount` es un número, nunca una lista**: el listado de hijos
          se obtiene con `GET /api/v1/roles?parentRoleId={id}`, que ya está
          paginado. Así el tamaño de la respuesta no depende de cuántos hijos
          tenga el rol.

          **`assignedUserCount` cuenta personas distintas**, en cualquier estado
          —incluidas las inactivas y las bloqueadas— y **excluyendo las
          eliminadas**: alguien bloqueado sigue portando el rol, y su existencia
          es lo que impide borrarlo. Cero es un dato, no una ausencia. **No exige
          `users:read`**: es un agregado sin identidad, y quien quiera saber
          quiénes son usa `GET /api/v1/users?roleId={id}`.

          Un rol **eliminado devuelve el mismo `404`** que uno inexistente, sin
          ninguna pista de que existió.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Detalle del rol.",
        content = @Content(schema = @Schema(implementation = RoleDetailResponse.class))),
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
        description = "Autenticado sin `roles:read` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe el rol, o está eliminado lógicamente (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public RoleDetailResponse detalle(@PathVariable UUID id) {
    return detalleDelRol.detail(id);
  }

  // ---------------------------------------------------------------------------
  // Escrituras (`RF-SP-004` a `RF-SP-009`)
  //
  // Las seis cruzan las MISMAS tres puertas —el rol existe, no es de sistema y
  // no lo tiene asignado el actor—, y por eso las tres viven en un solo
  // componente: seis copias de la misma comprobación divergen, y la que se
  // queda atrás no falla, concede.
  // ---------------------------------------------------------------------------

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('roles:update')")
  @Operation(
      summary = "Editar un rol",
      description =
          """
          Modifica el **nombre** y la **descripción**. Nada más: el código es
          estable por diseño —cambiarlo rompería cualquier referencia externa— y
          la clasificación es **inmutable**, porque determina si el rol puede
          llevar membresía y cambiarla dejaría membresías colgando de un rol que
          ya no es consumidor. Los permisos, el estado y el rol padre tienen cada
          uno su operación.

          Al menos uno de los dos campos debe venir informado. `description`
          admite `null` explícito —borrarla es una orden legítima—; `name` no,
          porque un rol sin nombre no existe.

          **Reenviar los valores actuales no es un error ni registra evento**: si
          nada cambió, no hay nada que auditar.

          **Un rol de sistema devuelve `409` y un rol que usted tiene asignado,
          `403`**. Lo segundo impide ampliar el alcance del rol con el que se
          está entrando; alcanza solo a los roles asignados **directamente**, de
          modo que un ancestro del propio sí puede editarse.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Rol actualizado.",
        content = @Content(schema = @Schema(implementation = RoleResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Ningún campo informado (`VAL-001`), nombre vacío (`VAL-002`) o longitud excedida"
                + " (`VAL-004`)",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description =
            "Sin `roles:update` (`AUTH-002`), o el rol está asignado al propio actor"
                + " (`RN-SEG-011`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El rol no existe o está eliminado (`EX-004`)"),
    @ApiResponse(
        responseCode = "409",
        description = "Rol de sistema (`RN-SEG-012`) o nombre ya en uso (`RN-SEG-001`)"),
    @ApiResponse(responseCode = "500", description = "Fallo no controlado (`ERR-500`)")
  })
  public RoleResponse editar(
      @PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest peticion) {
    return edicion.update(id, peticion);
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAuthority('roles:update')")
  @Operation(
      summary = "Activar o desactivar un rol",
      description =
          """
          **Desactivar no es retirar.** Las asignaciones a personas se conservan
          intactas; lo que cambia es que el rol **deja de conceder permisos de
          inmediato**, sin esperar a que ningún token expire.

          Se pide el **estado destino** —`ACTIVO` o `INACTIVO`— y no una acción,
          de modo que repetir la petición deja el mismo resultado y no registra
          evento.

          **El rol raíz no admite esta operación**, aparte de la prohibición
          general sobre los roles de sistema: un rol raíz inactivo no concede
          nada, y eso dejaría al sistema sin su última vía de administración.

          El cuerpo no admite motivo: el Art. V.13 lo exige solo en las
          eliminaciones.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Rol con su estado actualizado.",
        content = @Content(schema = @Schema(implementation = RoleResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Estado ausente o fuera del dominio (`VAL-001`)",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `roles:update`, o el rol está asignado al propio actor (`RN-SEG-011`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El rol no existe o está eliminado (`EX-003`)"),
    @ApiResponse(
        responseCode = "409",
        description = "Rol de sistema (`RN-SEG-012`) o rol raíz (`RN-SEG-007`)"),
    @ApiResponse(responseCode = "500", description = "Fallo no controlado (`ERR-500`)")
  })
  public RoleResponse cambiarEstado(
      @PathVariable UUID id, @Valid @RequestBody ChangeRoleStatusRequest peticion) {
    return estado.change(id, peticion);
  }

  @PatchMapping("/{id}/parent")
  @PreAuthorize("hasAuthority('roles:update')")
  @Operation(
      summary = "Cambiar el rol padre",
      description =
          """
          Reubica el rol bajo otro padre y **revalida la contención contra el
          nuevo** (`RN-SEG-013`): si el rol declara permisos que el nuevo padre
          no posee, la operación se rechaza **entera** enumerándolos, para que
          usted decida cuáles retirar. **Nunca se recorta nada en silencio.**

          **Los hijos acompañan al rol** y no hay que revisarlos: si este cabe en
          el nuevo padre, ellos caben en este por transitividad.

          El nuevo padre debe existir y estar **activo**, y no puede ser el
          propio rol ni uno de sus descendientes —eso formaría un ciclo—. Su
          clasificación es indiferente: un rol comercial puede colgar de uno
          funcionario, porque el padre acota privilegios, no clasifica.

          **El rol raíz no admite padre** y ningún rol puede quedarse sin él:
          `RN-SEG-007` exige exactamente una cima.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Rol con su nuevo rol padre.",
        content = @Content(schema = @Schema(implementation = RoleResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Rol padre ausente o mal formado (`VAL-001`)",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `roles:update`, o el rol está asignado al propio actor (`RN-SEG-011`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El rol no existe o está eliminado (`EX-006`)"),
    @ApiResponse(
        responseCode = "409",
        description =
            "Rol de sistema (`RN-SEG-012`), rol raíz (`RN-SEG-007`), ciclo en la jerarquía"
                + " (`RN-SEG-006`) o permisos que el nuevo padre no concede (`RN-SEG-013`)"),
    @ApiResponse(
        responseCode = "422",
        description = "El rol padre indicado no existe o no está activo (`EX-004`)"),
    @ApiResponse(responseCode = "500", description = "Fallo no controlado (`ERR-500`)")
  })
  public RoleResponse cambiarPadre(
      @PathVariable UUID id, @Valid @RequestBody ChangeRoleParentRequest peticion) {
    return padre.change(id, peticion);
  }

  @PostMapping("/{id}/permissions")
  @PreAuthorize("hasAuthority('roles:update')")
  @Operation(
      summary = "Agregar permisos a un rol",
      description =
          """
          Asocia permisos al rol. **La operación se aplica entera o no se
          aplica**: un solo permiso que incumpla la contención rechaza la
          petición completa, porque aplicar los válidos dejaría el rol en un
          estado que nadie pidió.

          Cada permiso debe estar **contenido en los del rol padre**
          (`RN-SEG-003`) y **en los permisos efectivos de quien ejecuta la
          operación** (`RN-SEG-010`). Lo segundo no lo concede `roles:update`:
          ese permiso habilita a modificar roles, no a decidir con qué alcance.

          **El rol raíz omite la primera comprobación** —no tiene cota superior—
          y conserva la segunda.

          **Es idempotente y nunca retira nada**: los permisos ya declarados se
          ignoran sin error, y si no queda ninguno por agregar no se registra
          evento. Hasta 100 por petición.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Rol con sus permisos actualizados.",
        content = @Content(schema = @Schema(implementation = RoleResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Sin permisos (`VAL-001`) o más de 100 (`VAL-006`)",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `roles:update`, o el rol está asignado al propio actor (`RN-SEG-011`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El rol no existe o está eliminado (`EX-006`)"),
    @ApiResponse(
        responseCode = "409",
        description =
            "Rol de sistema (`RN-SEG-012`), permiso fuera del rol padre (`RN-SEG-003`) o fuera del"
                + " alcance del actor (`RN-SEG-010`)"),
    @ApiResponse(
        responseCode = "422",
        description = "Uno o más permisos no existen en el catálogo (`EX-003`)"),
    @ApiResponse(responseCode = "500", description = "Fallo no controlado (`ERR-500`)")
  })
  public RoleResponse agregarPermisos(
      @PathVariable UUID id, @Valid @RequestBody RolePermissionsRequest peticion) {
    return concesion.grant(id, peticion);
  }

  @PostMapping("/{id}/permissions/revocations")
  @PreAuthorize("hasAuthority('roles:update')")
  @Operation(
      summary = "Retirar permisos de un rol",
      description =
          """
          Desasocia permisos del rol, con **eliminación física** de la asociación
          y **sin exigir motivo**: es una asociación y no una entidad de negocio.

          **Se rechaza si un rol hijo directo declara alguno de los permisos que
          se retiran** (`RN-SEG-005`), y el error dice **qué rol** declara **qué
          permiso**: sin ese detalle no habría forma de saber qué corregir. Los
          hijos **inactivos cuentan igual** que los activos —el invariante vale
          siempre—, y los eliminados no.

          **No hay cascada**: nunca se retiran permisos de los descendientes.
          Usted decide en qué orden hacerlo.

          **Es idempotente**: los permisos que el rol no declaraba se ignoran sin
          error. Hasta 100 por petición, el mismo límite que al agregarlos.

          **`POST` sobre un subrecurso y no `DELETE`**: la operación recibe una
          lista en el cuerpo, y RFC 9110 no define semántica para el cuerpo de un
          `DELETE`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Rol con sus permisos actualizados.",
        content = @Content(schema = @Schema(implementation = RoleResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Sin permisos (`VAL-001`) o más de 100 (`VAL-004`)",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `roles:update`, o el rol está asignado al propio actor (`RN-SEG-011`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El rol no existe o está eliminado (`EX-004`)"),
    @ApiResponse(
        responseCode = "409",
        description =
            "Rol de sistema (`RN-SEG-012`), o un rol dependiente declara el permiso"
                + " (`RN-SEG-005`)"),
    @ApiResponse(responseCode = "500", description = "Fallo no controlado (`ERR-500`)")
  })
  public RoleResponse retirarPermisos(
      @PathVariable UUID id, @Valid @RequestBody RolePermissionsRequest peticion) {
    return revocacion.revoke(id, peticion);
  }

  @PostMapping("/{id}/deletion")
  @PreAuthorize("hasAuthority('roles:delete')")
  @Operation(
      summary = "Eliminar un rol",
      description =
          """
          Elimina lógicamente el rol. **El motivo es obligatorio** y se verifica
          **antes de ejecutar nada** (Art. V.13); no se admite un valor generado
          por el sistema.

          **Se rechaza si el rol tiene roles hijos vigentes** —diciendo cuáles— o
          **personas asignadas** —diciendo cuántas— (`RN-SEG-008`). En el segundo
          caso la respuesta sugiere **desactivar** el rol, que suele ser lo que
          en realidad se quería: retirar el acceso sin perder el rol. El conteo
          incluye a las personas inactivas y bloqueadas, y excluye a las
          eliminadas.

          Tras eliminarlo, **su código y su nombre quedan libres** para un rol
          nuevo, y el rol deja de aparecer en el listado por defecto. La
          auditoría de eliminación conserva el motivo y el rol completo —con sus
          permisos por código— para poder reconstruir qué era.

          **`POST` sobre un subrecurso y no `DELETE`**: el motivo viaja en el
          cuerpo, y un intermediario puede descartar el cuerpo de un `DELETE`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Rol eliminado. Sin cuerpo."),
    @ApiResponse(
        responseCode = "400",
        description = "Motivo ausente o vacío (`VAL-001`, `VAL-002`)",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `roles:delete`, o el rol está asignado al propio actor (`RN-SEG-011`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El rol no existe o ya está eliminado (`EX-006`)"),
    @ApiResponse(
        responseCode = "409",
        description =
            "Rol de sistema (`RN-SEG-012`), rol raíz (`RN-SEG-007`), o el rol tiene hijos o"
                + " personas asignadas (`RN-SEG-008`)"),
    @ApiResponse(responseCode = "500", description = "Fallo no controlado (`ERR-500`)")
  })
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void eliminar(@PathVariable UUID id, @RequestBody DeleteRoleRequest peticion) {
    baja.delete(id, peticion);
  }
}
