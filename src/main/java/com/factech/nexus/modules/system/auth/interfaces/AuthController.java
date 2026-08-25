package com.factech.nexus.modules.system.auth.interfaces;

import com.factech.nexus.modules.system.auth.application.ChangePasswordRequest;
import com.factech.nexus.modules.system.auth.application.LoginRequest;
import com.factech.nexus.modules.system.auth.application.LogoutRequest;
import com.factech.nexus.modules.system.auth.application.RefreshRequest;
import com.factech.nexus.modules.system.auth.application.SessionResponse;
import com.factech.nexus.modules.system.auth.domain.service.ChangeOwnPasswordService;
import com.factech.nexus.modules.system.auth.domain.service.LoginService;
import com.factech.nexus.modules.system.auth.domain.service.SessionService;
import com.factech.nexus.shared.security.OpenApiSecurityConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sesión (`RF-SP-034`, `RF-SP-035`, `RF-SP-036`).
 *
 * <p><b>Los tres endpoints son públicos, y el cierre también.</b> Exigir un token de acceso vigente
 * para cerrar sesión lo impediría justo cuando más falta hace —cuando se sospecha que lo robaron— y
 * obligaría a renovar antes de poder cerrar.
 *
 * <p><b>Ninguno declara permiso</b>, y eso no los deja desprotegidos: lo que autoriza es la
 * posesión de la credencial que cada uno recibe.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Sesión", description = "Inicio, renovación y cierre de sesión.")
// Las tres rutas son públicas, y el contrato tiene que decirlo: sin esta
// declaración vacía heredarían el esquema global y la interfaz pediría un token
// para obtener el token.
@SecurityRequirements
public class AuthController {

  private final LoginService inicio;
  private final SessionService sesion;
  private final ChangeOwnPasswordService cambioDeContrasena;

  public AuthController(
      LoginService inicio, SessionService sesion, ChangeOwnPasswordService cambioDeContrasena) {
    this.inicio = inicio;
    this.sesion = sesion;
    this.cambioDeContrasena = cambioDeContrasena;
  }

  @PostMapping("/login")
  @Operation(
      summary = "Iniciar sesión",
      description =
          """
          Autentica con **nombre de usuario o correo** —el mismo campo para los
          dos— y entrega las credenciales de sesión.

          La cuenta bloqueada recibe `423` y un mensaje que la identifica como
          tal: es una excepción consciente al mensaje genérico, porque quien
          provocó el bloqueo ya sabe que la cuenta existe.

          Si la contraseña la fijó otra persona, la respuesta autentica **y
          advierte** con `mustChangePassword`: hace falta una sesión para poder
          cambiarla.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Credenciales de sesión.",
        content = @Content(schema = @Schema(implementation = SessionResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Identificador o contraseña ausentes",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description =
            "Credenciales inválidas, cuenta inexistente, inactiva o eliminada — los cuatro casos"
                + " comparten cuerpo y mensaje, sin una sola diferencia observable",
        content = @Content),
    @ApiResponse(responseCode = "423", description = "Cuenta bloqueada", content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public SessionResponse login(@Valid @RequestBody LoginRequest peticion) {
    return inicio.login(peticion.identifier(), peticion.password());
  }

  @PostMapping("/refresh")
  @Operation(
      summary = "Renovar la sesión",
      description =
          """
          Rota el refresh token y entrega credenciales nuevas.

          **La rotación es la defensa:** el token entregado deja de servir, de
          modo que presentarlo de nuevo significa que existe una copia. En ese
          caso se revoca la **familia entera** de la sesión y se emite la alarma.

          Las cinco condiciones de rechazo devuelven la **misma** respuesta: el
          cliente no debe poder deducir si el token fue robado, si expiró o si la
          cuenta fue desactivada.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Credenciales renovadas.",
        content = @Content(schema = @Schema(implementation = SessionResponse.class))),
    @ApiResponse(responseCode = "400", description = "Refresh token ausente", content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "La sesión no es válida — cinco causas posibles, una sola respuesta",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public SessionResponse refresh(@Valid @RequestBody RefreshRequest peticion) {
    return sesion.refresh(peticion.refreshToken());
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Cerrar sesión",
      description =
          """
          Revoca el refresh token de la sesión, o **todos** los de la persona si
          se pide.

          Responde `204` para **cualquier** token sintácticamente válido, exista o
          no, esté vigente o revocado. Distinguirlos permitiría comprobar con dos
          peticiones si una cadena de texto es un refresh token del sistema, y el
          resultado prometido —que ese token no sirva— se cumple igual.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Sesión cerrada.", content = @Content),
    @ApiResponse(
        responseCode = "400",
        description = "Refresh token ausente o con formato imposible",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public void logout(@Valid @RequestBody LogoutRequest peticion) {
    sesion.logout(peticion.refreshToken(), peticion.todasLasSesiones());
  }

  /**
   * <b>El único de esta sección que exige token</b>, y por eso reintroduce el esquema que la clase
   * desheredó: cambiar la propia contraseña no tiene sujeto sin alguien autenticado.
   */
  @PostMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @SecurityRequirement(name = OpenApiSecurityConfig.ESQUEMA)
  @Operation(
      summary = "Cambiar la propia contraseña",
      description =
          """
          Sustituye la contraseña de **quien porta el token**.

          **No lleva identificador de usuario**, y esa ausencia es la
          implementación de la regla: no hay campo por el que dirigir la
          operación a un tercero. Restablecer la contraseña de otra persona es
          otra operación, con su propio permiso.

          La contraseña actual se comprueba **la última**, después del formato, de
          que la nueva sea distinta y de la política. Es el único paso que consume
          el contador de intentos: ponerlo antes haría que cinco peticiones
          descuidadas de un cliente propio bloquearan la cuenta de su titular.

          La contraseña actual incorrecta devuelve `422` y **no `401`**: un `401`
          le diría al cliente que su sesión ya no vale y lo mandaría a iniciar
          sesión, cuando lo único que ocurrió es que escribió mal su contraseña.

          **Aquí sí se dice qué falló**, al revés que al iniciar sesión: quien
          hace esta petición ya está autenticado y no se le revela nada que no
          supiera.

          Al cambiarla, **todas las sesiones se revocan**, incluida la que ejecutó
          el cambio.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Contraseña sustituida.", content = @Content),
    @ApiResponse(
        responseCode = "400",
        description =
            "Falta alguna de las dos (`VAL-001`, `VAL-002`), la nueva no cumple la política"
                + " (`VAL-004`) o es igual a la actual (`VAL-005`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "La contraseña actual no es correcta (`VAL-003`). Consume un intento",
        content = @Content),
    @ApiResponse(
        responseCode = "423",
        description = "La cuenta quedó bloqueada al alcanzar el umbral de fallos",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public void cambiarContrasena(@RequestBody ChangePasswordRequest peticion) {
    cambioDeContrasena.change(peticion);
  }
}
