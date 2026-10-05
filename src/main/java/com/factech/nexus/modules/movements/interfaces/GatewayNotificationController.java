package com.factech.nexus.modules.movements.interfaces;

import com.factech.nexus.modules.movements.domain.service.GatewayEventIntake;
import com.factech.nexus.modules.movements.domain.service.LocalChargeReconciler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las notificaciones de la pasarela de pago (`RF-MV-041`). <b>No la llama el frontend</b>: la llama
 * Stripe, firmada.
 */
@Tag(
    name = "Pasarela de pago",
    description =
        "Lo que notifican las pasarelas: la de la tarjeta (Stripe) y la local (PayRetailers). Solo"
            + " las llaman ellas; el frontend no.")
@RestController
@RequestMapping("/api/v1/movements/gateway-notifications")
public class GatewayNotificationController {

  private final GatewayEventIntake recepcion;
  private final LocalChargeReconciler local;

  public GatewayNotificationController(GatewayEventIntake recepcion, LocalChargeReconciler local) {
    this.recepcion = recepcion;
    this.local = local;
  }

  @PostMapping(value = "/stripe", consumes = "*/*")
  @Operation(
      summary = "Recibir una notificación de Stripe",
      description =
          """
          **La llama Stripe, no el frontend** (`RF-MV-041`). Ruta **pública**: la autentica la
          cabecera `Stripe-Signature`, un HMAC del cuerpo con el secreto del endpoint. Se guarda
          la notificación entera, **una sola vez** aunque llegue repetida, y se responde `200` en
          cuanto queda guardada; procesarla va después: un cobro que entró confirma el pago y la
          venta o la compra de puntos; una tarjeta rechazada deja el pago pendiente; un cobro
          cancelado lo rechaza; un reembolso o una disputa se marcan en el pago (`RN-MV-058` a
          `RN-MV-060`).
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Recibida; cuerpo vacío.", content = @Content),
    @ApiResponse(
        responseCode = "400",
        description = "Firma ausente, inválida o con más de cinco minutos (`RN-MV-059`)",
        content = @Content),
    @ApiResponse(
        responseCode = "503",
        description = "La pasarela no está configurada en este entorno (`RN-MV-059`)",
        content = @Content)
  })
  public ResponseEntity<Void> stripe(
      @RequestBody(required = false) byte[] cuerpo,
      @RequestHeader(value = "Stripe-Signature", required = false) String firma) {
    recepcion.receive(cuerpo, firma);
    return ResponseEntity.ok().build();
  }

  @PostMapping(value = "/payretailers", consumes = "*/*")
  @Operation(
      summary = "Recibir un aviso de PayRetailers",
      description =
          """
          El aviso de la pasarela local (`RF-MV-049`). **No va firmado, y por eso no se cree**
          (`RN-MV-064`): se guarda tal como llegó, se responde `200` y, después, se
          **pregunta a la pasarela** por el cobro de su `trackingId` con las credenciales de esta
          API. Lo que conteste es lo que se aplica: aprobado confirma el pago y la venta o la
          compra de puntos; fallido, rechazado, cancelado o caducado lo rechaza; pendiente no hace
          nada. Un aviso de un cobro que no es de ningún pago se ignora sin preguntar.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Recibido; cuerpo vacío.", content = @Content),
    @ApiResponse(
        responseCode = "400",
        description = "Ilegible: sin `uid` o sin `trackingId` (`RN-MV-064`)",
        content = @Content),
    @ApiResponse(
        responseCode = "503",
        description = "La pasarela local no está configurada en este entorno (`RN-MV-064`)",
        content = @Content)
  })
  public ResponseEntity<Void> payretailers(@RequestBody(required = false) byte[] cuerpo) {
    local.receive(cuerpo);
    return ResponseEntity.ok().build();
  }
}
