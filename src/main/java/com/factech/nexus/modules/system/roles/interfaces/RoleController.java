package com.factech.nexus.modules.system.roles.interfaces;

import com.factech.nexus.modules.system.roles.application.CreateRoleRequest;
import com.factech.nexus.modules.system.roles.application.RoleResponse;
import com.factech.nexus.modules.system.roles.domain.service.CreateRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

  public RoleController(CreateRoleService alta) {
    this.alta = alta;
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
}
