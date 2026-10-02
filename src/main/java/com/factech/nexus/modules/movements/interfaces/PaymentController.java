package com.factech.nexus.modules.movements.interfaces;

import com.factech.nexus.modules.movements.application.ConfirmPaymentRequest;
import com.factech.nexus.modules.movements.application.RejectPaymentRequest;
import com.factech.nexus.modules.movements.application.RetryPaymentRequest;
import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.models.IdempotencyKey;
import com.factech.nexus.modules.movements.domain.service.PaymentResolutionService;
import com.factech.nexus.modules.movements.domain.service.RetryPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los intentos de pago (`RN-MV-039`): volver a pagar una venta (`RF-MV-018`) y <b>conciliar el
 * pago</b> —confirmarlo (`RF-MV-044`) o rechazarlo (`RF-MV-045`)—, sea de una venta o de una compra
 * de puntos (`RN-MV-061`, 01-10-2026).
 *
 * <p>Un controlador aparte de {@link MovementController} y bajo el mismo recurso, porque son las
 * operaciones de <b>otro objeto</b> —el pago— sobre la misma venta.
 */
@Tag(
    name = "Movimientos",
    description = "El libro de hechos económicos: qué se vendió, a quién y a quién se le atribuye.")
@RestController
@RequestMapping("/api/v1/movements")
public class PaymentController {

  private final RetryPaymentService reintento;
  private final PaymentResolutionService conciliacion;

  public PaymentController(RetryPaymentService reintento, PaymentResolutionService conciliacion) {
    this.reintento = reintento;
    this.conciliacion = conciliacion;
  }

  @PostMapping("/mine/{id}/payments")
  @PreAuthorize("hasAuthority('movements:retry-payment')")
  @Operation(
      summary = "Volver a pagar una venta propia pendiente",
      description =
          """
          Abre un **nuevo intento de pago** sobre una venta propia pendiente cuyo último pago se
          **rechazó** —la aplicación se cayó, o quien cobra lo rechazó—, con el mismo método o
          con otro, **sin registrar otra venta** (`RF-MV-018`, `RN-MV-039`).

          **La cabecera `Idempotency-Key` es obligatoria**: el cliente la genera una vez por
          intento y la repite tal cual si tiene que reenviar. La misma petición repetida
          —misma clave, misma venta, mismo método— responde `200` con lo que ya se creó, sin
          abrir otro pago. La misma clave con otra petición es `409`.

          Mientras la venta tenga un pago **pendiente** no admite otro (`409`): ese pago puede
          estar entrando ahora mismo. `paymentMethodId` es obligatorio si la venta cobra algo y
          está prohibido si su importe es cero (`RN-MV-022`). Una venta ajena responde `404`,
          igual que una que no existe.

          **Con tarjeta (`CREDIT_CARD`), desde el 01-10-2026, abre el cobro en la pasarela** en el mismo
          acto (`RF-MV-040`): la respuesta trae `cardCharge.clientSecret`, con el que la app pide la
          tarjeta con Stripe Elements. **Nada queda confirmado**: lo confirma la notificación de la
          pasarela. Si la pasarela no responde, `503` y no se registra nada; por debajo del mínimo
          (0,50 USD), `422`. Con la pasarela apagada, el pago queda pendiente sin cobro, como antes.

          **Un pago pendiente con cobro abierto ya no bloquea** (`RN-MV-058`): volver a pagar
          con otro método lo cancela en la pasarela y lo cierra rechazado; si ya se cobró, `409`
          (`EX-009`). Con tarjeta otra vez, `409` (`EX-010`): el cobro abierto se retoma con
          `POST /movements/mine/{id}/card-charge`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Pago abierto: la venta con sus pagos."),
    @ApiResponse(
        responseCode = "200",
        description = "La misma petición repetida: la venta, sin abrir otro pago (`FA-001`)."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador malformado, `Idempotency-Key` ausente o malformada, o método presente"
                + " en una venta de importe cero o ausente en una cobrada",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `movements:retry-payment` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "La venta no existe, **no es suya** o no es una venta (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "No está pendiente (`EX-003`), tiene un pago pendiente (`EX-004`), el método está"
                + " desactivado (`EX-010`) o la clave es de otra petición (`EX-006`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "El método de pago no existe (`EX-010`)",
        content = @Content),
    @ApiResponse(
        responseCode = "503",
        description =
            "La pasarela de pago no respondió; nada se escribió y se puede reintentar"
                + " (`RN-MV-057`)",
        content = @Content)
  })
  public ResponseEntity<SaleResponse> volverAPagar(
      @PathVariable UUID id,
      @RequestBody(required = false) RetryPaymentRequest peticion,
      @Parameter(
              in = ParameterIn.HEADER,
              required = true,
              description = "Una por intento; se repite tal cual al reenviar. 8 a 80 caracteres.")
          @RequestHeader(value = IdempotencyKey.CABECERA, required = false)
          String clave) {
    RetryPaymentService.Result hecho = reintento.retry(id, peticion, clave);
    if (!hecho.created()) {
      return ResponseEntity.ok(hecho.sale());
    }
    return ResponseEntity.created(URI.create("/api/v1/movements/mine/" + id)).body(hecho.sale());
  }

  /**
   * <b>Se concilia el pago, no el movimiento</b> (`RN-MV-061`): la ruta nombra el pago, y el
   * movimiento lo sigue según su tipo. Sustituye a {@code POST /movements/{id}/confirmation} y a
   * {@code POST /movements/{id}/points-purchase-confirmation}, retiradas sin alias el 01-10-2026.
   */
  @PostMapping("/payments/{paymentId}/confirmation")
  @PreAuthorize("hasAuthority('movements:confirm-payment')")
  @Operation(
      summary = "Confirmar un pago pendiente",
      description =
          """
          Dice **«este pago entró»**, con cualquier método, y **su movimiento lo sigue** en el
          mismo acto (`RF-MV-044`, `RN-MV-061`):

          - **El pago de una venta** → la venta pasa a `CONFIRMADA` y **entrega lo que se pueda
            entregar** (`RF-MV-003`): lo automático queda `ENTREGADA` —un upgrade concede la
            membresía, salvo que baje de nivel: entonces `RETENIDA` con `deliveryNote`—, y lo
            manual queda `PENDIENTE` hasta que quien lo compró lo active. Y avisa a comisiones.
          - **El pago de una compra de puntos** → la compra pasa a `CONFIRMADA` y **se abonan los
            puntos congelados al comprar**, no los de la tasa de hoy (`RF-MV-028`).

          Cuerpo **opcional**: `providerReference`, la referencia del extracto, que se guarda en
          el pago. Responde **el detalle del movimiento**, con la forma de
          `GET /api/v1/movements/{id}`.

          **Confirmar dos veces confirma una vez**: el segundo intento —o una confirmación y un
          rechazo a la vez— recibe `409` diciendo el estado del pago, y nada cambia.

          **No alcanza a un pago con cobro abierto en la pasarela** (`RN-MV-058`): lo confirma
          su notificación. Un pago con tarjeta sin cobro —el que registró un funcionario— se
          confirma aquí. **Ni al pago de un retiro**, que se resuelve al aprobarlo.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El movimiento, con el pago confirmado."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador malformado (`VAL-001`) o referencia de más de 120 caracteres (`VAL-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `movements:confirm-payment` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe un pago con ese identificador (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "Es el pago de un retiro (`EX-002`), no está pendiente —el mensaje dice su estado—"
                + " (`EX-003`), o tiene un cobro abierto en la pasarela (`EX-004`). Nada cambió.",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Entregar o abonar falló; nada quedó escrito (`ERR-500`)",
        content = @Content)
  })
  public SaleResponse confirmar(
      @PathVariable UUID paymentId, @RequestBody(required = false) ConfirmPaymentRequest peticion) {
    return conciliacion.confirm(paymentId, peticion == null ? null : peticion.providerReference());
  }

  /**
   * El espejo de la confirmación. Sustituye a {@code POST /movements/{id}/rejection} y a {@code
   * POST /movements/{id}/points-purchase-rejection}, retiradas sin alias el 01-10-2026.
   */
  @PostMapping("/payments/{paymentId}/rejection")
  @PreAuthorize("hasAuthority('movements:reject-payment')")
  @Operation(
      summary = "Rechazar un pago pendiente",
      description =
          """
          Dice **«este pago no entró»**, con cualquier método: el pago pasa a `RECHAZADO` con el
          instante y el motivo, y **su movimiento lo sigue** según su tipo (`RF-MV-045`,
          `RN-MV-061`):

          - **El pago de una venta** → la venta **sigue `PENDIENTE`**, y quien compró la puede
            volver a pagar (`RF-MV-004`, `RF-MV-018`).
          - **El pago de una compra de puntos** → la compra queda **`RECHAZADA`**, con el mismo
            motivo. Es final: quien quiera los puntos compra otra vez (`RF-MV-029`).

          `reason` es obligatorio, hasta 500 caracteres. Responde **el detalle del movimiento**.
          **Rechazar no es anular**: anular cierra la venta; rechazar cierra un intento de
          cobrarla.

          **No alcanza a un pago con cobro abierto en la pasarela** (`RN-MV-058`) **ni al pago de
          un retiro**, que se niega por su propia ruta.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El movimiento, con el pago rechazado."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador malformado (`VAL-001`), motivo vacío (`VAL-002`) o de más de 500"
                + " caracteres (`VAL-003`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `movements:reject-payment` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe un pago con ese identificador (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "Es el pago de un retiro (`EX-002`), no está pendiente —el mensaje dice su estado—"
                + " (`EX-003`), o tiene un cobro abierto en la pasarela (`EX-004`). Nada cambió.",
        content = @Content)
  })
  public SaleResponse rechazar(
      @PathVariable UUID paymentId, @RequestBody(required = false) RejectPaymentRequest peticion) {
    return conciliacion.reject(paymentId, peticion == null ? null : peticion.reason());
  }
}
