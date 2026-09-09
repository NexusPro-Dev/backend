package com.factech.nexus.modules.system.users.interfaces;

import com.factech.nexus.modules.system.users.application.SelfRegistrationRequest;
import com.factech.nexus.modules.system.users.application.SelfRegistrationResponse;
import com.factech.nexus.modules.system.users.domain.service.RegisterClientByLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro de clientes por enlace (`RF-SP-045`).
 *
 * <p><b>Cuelga de {@code /auth} y no de {@code /users}</b>: las rutas públicas del sistema viven
 * ahí, y colgarla de {@code /users} la pondría al lado de {@code POST /api/v1/users}, que exige
 * {@code users:create}. Dos altas de personas con reglas opuestas a un carácter de distancia es una
 * confusión que se paga tarde.
 */
@RestController
@RequestMapping("/api/v1/auth/registration")
@Tag(
    name = "Registro por enlace",
    description = "Alta de un cliente desde el enlace de un vendedor. Público (RF-SP-045).")
public class RegistrationController {

  private final RegisterClientByLinkService registro;

  public RegistrationController(RegisterClientByLinkService registro) {
    this.registro = registro;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Registrarse desde el enlace de un vendedor",
      description =
          """
          Crea una cuenta de cliente **sin autenticación**, a partir del enlace
          que un vendedor reparte. Es el **primer endpoint público del sistema
          que escribe**.

          El enlace lleva dos datos y **ninguno es un secreto**: el producto —por
          su código o su identificador, en el mismo campo— y el vendedor, por su
          **nombre de usuario**. Los cuatro campos de referencia de este cuerpo
          evitan los UUID —producto, vendedor, `countryCode` alfa-3 y
          `documentType` por abreviación— porque es un formulario público al que
          no se le puede pedir que conozca los identificadores del sistema. La
          excepción es `brokerId`, que se elige de un desplegable leído del
          catálogo público de brokers.

          **`brokerId` y `brokerAccountId` son obligatorios cuando el producto
          del enlace es una membresía `FREE → FREE`** (`RN-SP-042`), y solo
          entonces. El motivo es el estado en el que nace la cuenta: sin la
          cuenta de broker declarada, nadie podrá atribuirle el depósito que la
          saca de `FTD_PENDIENTE`. El **nombre de usuario en el broker no se
          pide**: lo rellena después el webhook del propio broker.

          **La cuenta nace en `FTD_PENDIENTE`**, que **autentica y no opera**:
          quien se registra puede iniciar sesión y ver lo suyo, y lo que le falta
          para operar es el primer depósito.

          **La contraseña la elige la persona**, y por eso la cuenta **no** queda
          marcada para cambio obligatorio — se marca lo que fijó otro, no lo que
          uno fijó.

          **No devuelve credenciales de sesión**: registrarse no es iniciar
          sesión. Quien acaba de crear su cuenta pasa por el inicio de sesión
          como todo el mundo.

          **Los rechazos no dicen qué pasó, salvo tres.** Producto inexistente,
          inactivo o retirado comparten respuesta, y lo mismo el vendedor que no
          existe y el que no es fuerza comercial: distinguirlos convertiría este
          endpoint en una forma de enumerar el catálogo y la plantilla. **Sí
          dicen** cuál de las dos identidades —nombre de usuario o correo— está
          en uso, que el producto exige pago, y que la cuenta de broker ya está
          declarada por otra persona: los tres los necesita quien se registra
          para poder corregir.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Cuenta creada, en estado `FTD_PENDIENTE`",
        content = @Content(schema = @Schema(implementation = SelfRegistrationResponse.class))),
    @ApiResponse(responseCode = "400", description = "Datos inválidos (serie `VAL-nnn`)"),
    @ApiResponse(
        responseCode = "409",
        description =
            "Nombre de usuario o correo en uso (`EX-005`), o cuenta de broker ya declarada"
                + " (`EX-009`)"),
    @ApiResponse(
        responseCode = "422",
        description =
            "El enlace no procede (`EX-001`, `EX-002`, `EX-003`, `EX-006`, `EX-007`, `EX-008`) o"
                + " el producto exige pago (`EX-004`)"),
    @ApiResponse(responseCode = "429", description = "Demasiadas peticiones desde este origen")
  })
  public SelfRegistrationResponse registrar(@Valid @RequestBody SelfRegistrationRequest peticion) {
    return registro.register(peticion);
  }
}
