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

          El enlace lleva dos datos y **ninguno es un secreto**: el producto y el
          vendedor. **Los dos viajan dentro de `movement`** (`productId` y
          `sellerUsername`) y no en el primer nivel: hasta el 09-09-2026 estaban
          además duplicados como `product` y `referrer`, y dos campos para un
          dato son dos valores que pueden discrepar.

          `countryCode` va en alfa-3 y `documentType` por abreviación, porque es
          un formulario público al que no se le puede pedir que conozca los
          identificadores del sistema. Los que sí son UUID son los de lo que el
          formulario **acaba de leer de un catálogo**: el broker de cada cuenta y
          el producto.

          **El registro anota una venta** (`RN-SP-043`), siempre — también en el
          enlace gratuito. Nace `PENDIENTE`, de modo que registrarse **no paga
          nada**, y su código se devuelve en `sale`.

          **`brokerAccounts` es una LISTA: se declaran UNA O MÁS cuentas** en el
          mismo registro, porque una persona puede operar con varios brokers y
          esta es hoy la única vía para declararlos.

          **Es obligatoria —al menos una— cuando el producto del enlace es una
          membresía `BECA → BECA`** (`RN-SP-042`), y solo entonces. El motivo es
          el estado en el que nace la cuenta: sin cuenta de broker declarada,
          nadie podrá atribuirle el depósito que la saca de `FTD_PENDIENTE`. Con
          otro producto **se admiten igualmente** las que se envíen.

          **El nombre de usuario en el broker no se pide**: lo rellena después el
          webhook del propio broker.

          **Dos cuentas iguales en la misma petición se rechazan** con `VAL-014`,
          y no con el `409` del índice: ese mensaje diría «ya está declarada por
          otra persona», y la otra persona sería ella misma.

          **El estado en que nace la cuenta depende del producto**
          (`RN-SP-044`). Con un enlace `BECA → BECA` nace en `FTD_PENDIENTE`, que
          **autentica y no opera**: quien se registra puede iniciar sesión y ver
          lo suyo, y lo que le falta para operar es el primer depósito. Con un
          producto **de pago** nace **`ACTIVO`** —no hay ningún depósito que
          esperar— y recibe la membresía **del suelo**, no la comprada: esa la
          concede **confirmar la venta**, y no registrarse.

          **La contraseña la elige la persona**, y por eso la cuenta **no** queda
          marcada para cambio obligatorio — se marca lo que fijó otro, no lo que
          uno fijó.

          **No devuelve credenciales de sesión**: registrarse no es iniciar
          sesión. Quien acaba de crear su cuenta pasa por el inicio de sesión
          como todo el mundo.

          **Los rechazos no dicen qué pasó, salvo dos.** Producto inexistente,
          inactivo o retirado comparten respuesta, y lo mismo el vendedor que no
          existe y el que no es fuerza comercial: distinguirlos convertiría este
          endpoint en una forma de enumerar el catálogo y la plantilla. **Sí
          dicen** cuál de las dos identidades —nombre de usuario o correo— está
          en uso, y que la cuenta de broker ya está declarada por otra persona:
          los dos los necesita quien se registra para poder corregir.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description =
            "Cuenta creada —`FTD_PENDIENTE` con el enlace gratuito, `ACTIVO` con uno de pago— y"
                + " venta anotada en estado `PENDIENTE`",
        content = @Content(schema = @Schema(implementation = SelfRegistrationResponse.class))),
    @ApiResponse(responseCode = "400", description = "Datos inválidos (serie `VAL-nnn`)"),
    @ApiResponse(
        responseCode = "409",
        description =
            "Nombre de usuario o correo en uso (`EX-005`), cuenta de broker ya declarada"
                + " (`EX-009`), o la venta no procede — método de pago en una venta gratuita, o"
                + " ausente en una con importe (`RN-MV-022`)"),
    @ApiResponse(
        responseCode = "422",
        description =
            "El enlace no procede (`EX-001`, `EX-002`, `EX-003`, `EX-006`, `EX-007`, `EX-008`) o"
                + " el movimiento no procede (`EX-010`)"),
    @ApiResponse(responseCode = "429", description = "Demasiadas peticiones desde este origen")
  })
  public SelfRegistrationResponse registrar(@Valid @RequestBody SelfRegistrationRequest peticion) {
    return registro.register(peticion);
  }
}
