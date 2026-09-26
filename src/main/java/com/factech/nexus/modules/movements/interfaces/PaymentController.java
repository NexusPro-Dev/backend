package com.factech.nexus.modules.movements.interfaces;

import com.factech.nexus.modules.movements.application.RejectPaymentRequest;
import com.factech.nexus.modules.movements.application.RetryPaymentRequest;
import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.models.IdempotencyKey;
import com.factech.nexus.modules.movements.domain.service.RejectPaymentService;
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
 * Los intentos de pago de una venta (`RN-MV-039`): volver a pagar (`RF-MV-018`) y rechazar el pago
 * pendiente (`RF-MV-004`).
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
  private final RejectPaymentService rechazo;

  public PaymentController(RetryPaymentService reintento, RejectPaymentService rechazo) {
    this.reintento = reintento;
    this.rechazo = rechazo;
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

  @PostMapping("/{id}/rejection")
  @PreAuthorize("hasAuthority('movements:reject-payment')")
  @Operation(
      summary = "Rechazar el pago pendiente de una venta",
      description =
          """
          Dice **«este cobro no entró»**: el pago pendiente de la venta pasa a `RECHAZADO`, con
          el instante y el motivo, y **la venta sigue pendiente** para que el comprador pueda
          volver a pagarla (`RF-MV-004`). `reason` es obligatorio, hasta 500 caracteres.

          **Rechazar no es anular**: anular cierra la venta; rechazar cierra un intento de
          cobrarla.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La venta, con el pago rechazado."),
    @ApiResponse(
        responseCode = "400",
        description = "Identificador malformado o motivo vacío o demasiado largo",
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
        description = "No existe, o no es una venta (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "No está pendiente (`EX-002`), o no tiene un pago pendiente (`EX-003`)",
        content = @Content)
  })
  public SaleResponse rechazar(
      @PathVariable UUID id, @RequestBody(required = false) RejectPaymentRequest peticion) {
    return rechazo.reject(id, peticion == null ? null : peticion.reason());
  }
}
