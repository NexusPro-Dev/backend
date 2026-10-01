package com.factech.nexus.modules.movements.interfaces;

import com.factech.nexus.modules.movements.domain.service.GatewayEventIntake;
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
        "Lo que notifica la pasarela de la tarjeta. Solo la llama Stripe; el frontend no.")
@RestController
@RequestMapping("/api/v1/movements/gateway-notifications")
public class GatewayNotificationController {

  private final GatewayEventIntake recepcion;

  public GatewayNotificationController(GatewayEventIntake recepcion) {
    this.recepcion = recepcion;
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
}
