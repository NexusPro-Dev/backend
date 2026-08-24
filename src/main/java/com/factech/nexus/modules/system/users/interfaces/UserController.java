package com.factech.nexus.modules.system.users.interfaces;

import com.factech.nexus.modules.system.users.application.RegisterUserRequest;
import com.factech.nexus.modules.system.users.application.UserResponse;
import com.factech.nexus.modules.system.users.domain.service.RegisterUserService;
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

  public UserController(RegisterUserService alta) {
    this.alta = alta;
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
}
