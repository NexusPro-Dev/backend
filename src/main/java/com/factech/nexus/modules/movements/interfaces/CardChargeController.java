package com.factech.nexus.modules.movements.interfaces;

import com.factech.nexus.modules.movements.application.CardChargeResponse;
import com.factech.nexus.modules.movements.domain.service.CardPayment;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pagar con tarjeta un pago pendiente propio (`RF-MV-042`). */
@Tag(name = "Movimientos")
@RestController
@RequestMapping("/api/v1/movements/mine")
public class CardChargeController {

  private final CardPayment tarjeta;
  private final AuthenticatedActor actor;

  public CardChargeController(CardPayment tarjeta, AuthenticatedActor actor) {
    this.tarjeta = tarjeta;
    this.actor = actor;
  }

  @PostMapping("/{id}/card-charge")
  @PreAuthorize("hasAuthority('movements:pay-pending-by-card')")
  @Operation(
      summary = "Pagar con tarjeta una compra pendiente propia",
      description =
          """
          Devuelve el **cobro con tarjeta** de una venta o una compra de puntos propia que tiene
          su pago pendiente con tarjeta (`RF-MV-042`): **el mismo cobro** si ya estaba abierto
          —porque la app se cerró a mitad, o la tarjeta se rechazó y se quiere probar otra—, o
          **uno nuevo** si nadie lo había abierto —la venta que registró un funcionario—. Con su
          `clientSecret` la app pide la tarjeta con Stripe Elements. **No confirma nada**: lo
          confirma la notificación de la pasarela. Sin cuerpo.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El cobro, con su secreto."),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:pay-pending-by-card` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe, no es suya, o no es venta ni compra de puntos (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "Sin pago pendiente, con un pago pendiente de otro método, o con el cobro cancelado"
                + " (`EX-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "Por debajo del mínimo de la pasarela (`RN-MV-057`)",
        content = @Content),
    @ApiResponse(
        responseCode = "503",
        description = "La pasarela no está configurada o no respondió (`RN-MV-057`)",
        content = @Content)
  })
  public CardChargeResponse pagarConTarjeta(@PathVariable UUID id) {
    return tarjeta.retomar(id, actor.id());
  }
}
