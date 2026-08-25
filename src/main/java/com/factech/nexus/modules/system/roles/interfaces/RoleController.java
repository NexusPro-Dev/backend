package com.factech.nexus.modules.system.roles.interfaces;

import com.factech.nexus.modules.system.roles.application.CreateRoleRequest;
import com.factech.nexus.modules.system.roles.application.ListRolesRequest;
import com.factech.nexus.modules.system.roles.application.RoleDetailResponse;
import com.factech.nexus.modules.system.roles.application.RoleListItem;
import com.factech.nexus.modules.system.roles.application.RoleResponse;
import com.factech.nexus.modules.system.roles.domain.service.CreateRoleService;
import com.factech.nexus.modules.system.roles.domain.service.GetRoleDetailService;
import com.factech.nexus.modules.system.roles.domain.service.ListRolesService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

  public RoleController(
      CreateRoleService alta, ListRolesService listado, GetRoleDetailService detalleDelRol) {
    this.alta = alta;
    this.listado = listado;
    this.detalleDelRol = detalleDelRol;
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
}
