package com.factech.nexus.modules.system.auth.interfaces;

import com.factech.nexus.modules.system.auth.application.MfaResetRequest;
import com.factech.nexus.modules.system.auth.domain.service.MfaResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * El segundo factor de <b>otra</b> persona (`RF-SP-076`).
 *
 * <p>Aparte de {@link MfaController}, que es el del propio: las rutas cuelgan de {@code
 * /users/{id}} y no de {@code /users/me}, y quien las llama administra a otros.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Segundo factor", description = "El authenticator de quien porta el token.")
public class MfaAdministrationController {

  private final MfaResetService restablecimiento;

  public MfaAdministrationController(MfaResetService restablecimiento) {
    this.restablecimiento = restablecimiento;
  }

  @PostMapping("/{id}/mfa/reset")
  @PreAuthorize("hasAuthority('users:reset-mfa')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Restablecer el segundo factor de un usuario",
      description =
          """
          Retira el authenticator de otra persona —el activo y el pendiente— y sus códigos
          de recuperación (`RF-SP-076`): es la salida de quien perdió el teléfono **y** los
          códigos. Cuerpo `{ reason }`, obligatorio: queda en la auditoría de eliminación.

          **Cierra todas las sesiones de la persona.** No toca su contraseña. Si un rol suyo
          exige el factor, su próximo inicio de sesión entra retenido hasta activar uno nuevo.

          **Nunca sobre uno mismo, ni sobre quien tiene algún permiso que el actor no tiene**
          (`RN-SP-065`): un `ADMIN` no puede restablecer el del superadministrador. Las dos
          son `403`, sin decir qué permiso falta. Es **sensible**: exige verificación
          reciente.

          Cómo se comprueba que quien llama es de verdad el titular queda fuera del sistema:
          lo decide la empresa, y el sistema lo deja escrito.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Restablecido"),
    @ApiResponse(
        responseCode = "400",
        description = "Sin motivo, o de más de 500 caracteres",
        content = @Content(schema = @Schema(hidden = true))),
    @ApiResponse(
        responseCode = "403",
        description =
            "Sin el permiso; sobre uno mismo; sobre quien tiene más privilegios; o sin"
                + " verificación reciente",
        content = @Content(schema = @Schema(hidden = true))),
    @ApiResponse(
        responseCode = "404",
        description = "El usuario no existe o está eliminado",
        content = @Content(schema = @Schema(hidden = true))),
    @ApiResponse(
        responseCode = "409",
        description = "La persona no tiene segundo factor",
        content = @Content(schema = @Schema(hidden = true)))
  })
  public void restablecer(@PathVariable UUID id, @RequestBody MfaResetRequest peticion) {
    restablecimiento.restablecer(id, peticion);
  }
}
