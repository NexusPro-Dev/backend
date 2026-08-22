package com.factech.nexus.modules.system.permissions.interfaces;

import com.factech.nexus.modules.system.permissions.application.ListPermissionsRequest;
import com.factech.nexus.modules.system.permissions.application.PermissionCatalogResponse;
import com.factech.nexus.modules.system.permissions.domain.service.ListPermissionsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo de permisos (`RF-SP-010` · `T-09`).
 *
 * <p><b>Controlador propio y no un método de {@code RoleController}.</b> El recurso es {@code
 * /api/v1/permissions}, no un subrecurso de un rol, y `RF-SP-015` añadirá aquí el detalle. Colgarlo
 * de {@code RoleController} mezclaría dos recursos en una clase por el solo hecho de que ambos
 * pertenecen al mismo módulo.
 *
 * <p><b>No declara ningún manejador de escritura, y la ausencia es la implementación.</b>
 * `RN-SP-004` hace el catálogo inmutable por API: no se cumple con código que rechace, se cumple
 * porque no hay a qué llamar. Un {@code POST} sobre este recurso obtiene {@code 405} de Spring, sin
 * que nadie lo haya escrito.
 */
@RestController
@RequestMapping("/api/v1/permissions")
@Tag(name = "Permisos", description = "Catálogo de permisos del sistema. Solo lectura.")
public class PermissionController {

  private final ListPermissionsService permissions;

  public PermissionController(ListPermissionsService permissions) {
    this.permissions = permissions;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('permissions:read')")
  @Operation(
      summary = "Consultar el catálogo de permisos",
      description =
          """
          Devuelve el catálogo completo, sin paginar, ordenado por recurso y acción.

          Los tres filtros son opcionales y ninguno se valida: un valor que no
          corresponda a ningún permiso produce una colección vacía, no un error.
          Los parámetros de paginación no se admiten y se ignoran.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Catálogo devuelto. La colección puede venir vacía.",
        content = @Content(schema = @Schema(implementation = PermissionCatalogResponse.class))),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `permissions:read` (`AUTH-002`)"),
    @ApiResponse(responseCode = "500", description = "Fallo no controlado (`ERR-500`)")
  })
  public PermissionCatalogResponse list(
      @Parameter(description = "Filtro por recurso. Coincidencia exacta.") //
          @ModelAttribute
          ListPermissionsRequest request) {

    return PermissionCatalogResponse.from(permissions.list(request.toQuery()));
  }
}
