package com.factech.nexus.modules.system.auth.interfaces;

import com.factech.nexus.modules.system.auth.application.MfaConfirmationRequest;
import com.factech.nexus.modules.system.auth.application.MfaConfirmationResponse;
import com.factech.nexus.modules.system.auth.application.MfaEnrollmentResponse;
import com.factech.nexus.modules.system.auth.domain.service.MfaEnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El propio segundo factor (`RF-SP-071` a `RF-SP-075`).
 *
 * <p>Aparte de {@code UserController}: el segundo factor es del submódulo de credenciales, como la
 * contraseña, y vive en {@code auth} (`071` · `plan.md` §3).
 */
@RestController
@RequestMapping("/api/v1/users/me/mfa")
@Tag(name = "Segundo factor", description = "El authenticator de quien porta el token.")
public class MfaController {

  private final MfaEnrollmentService activacion;

  public MfaController(MfaEnrollmentService activacion) {
    this.activacion = activacion;
  }

  @PostMapping("/totp")
  @PreAuthorize("hasAuthority('users:start-own-mfa')")
  @Operation(
      summary = "Iniciar la activación del segundo factor",
      description =
          """
          Genera un secreto para la app autenticadora y lo deja **pendiente**: no se exige
          al entrar hasta que se confirme con el primer código, y caduca a los diez minutos.

          La respuesta trae la URI `otpauth://` que el cliente pinta como **código QR**, el
          mismo secreto escrito —para quien no pueda escanear— y su caducidad. Iniciar otra
          vez sustituye al pendiente anterior.

          **Con un factor ya activo es cambiar de teléfono**, y exige haber verificado el
          código hace cinco minutos o menos: si no, `403` con el tipo
          `reverificacion-requerida` y la ruta para hacerlo.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Pendiente creado"),
    @ApiResponse(
        responseCode = "403",
        description = "Sin el permiso, o cambio de teléfono sin verificación reciente",
        content = @Content(schema = @Schema(hidden = true)))
  })
  public ResponseEntity<MfaEnrollmentResponse> iniciar() {
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(activacion.iniciar());
  }

  @PostMapping("/totp/confirmation")
  @PreAuthorize("hasAuthority('users:confirm-own-mfa')")
  @Operation(
      summary = "Confirmar el segundo factor",
      description =
          """
          Activa el factor pendiente con el código que muestra la app en este momento.

          La respuesta trae **los diez códigos de recuperación**, y es **la única vez** que
          existen en claro: el servidor guarda su resumen y no puede volver a mostrarlos.

          Si la persona tenía otro factor activo, queda retirado y sus códigos dejan de
          servir. Un código equivocado no consume intentos de la cuenta y no mata el
          pendiente: se puede reintentar sin volver a escanear.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Factor activo; los códigos, una vez"),
    @ApiResponse(
        responseCode = "400",
        description = "El código no tiene seis dígitos",
        content = @Content(schema = @Schema(hidden = true))),
    @ApiResponse(
        responseCode = "409",
        description = "Otra confirmación terminó antes",
        content = @Content(schema = @Schema(hidden = true))),
    @ApiResponse(
        responseCode = "422",
        description = "Sin pendiente, caducado, o el código no vale",
        content = @Content(schema = @Schema(hidden = true)))
  })
  public ResponseEntity<MfaConfirmationResponse> confirmar(
      @RequestBody MfaConfirmationRequest peticion) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(activacion.confirmar(peticion == null ? null : peticion.code()));
  }
}
