package com.factech.nexus.modules.movements.interfaces;

import com.factech.nexus.modules.movements.application.LocalChargeResponse;
import com.factech.nexus.modules.movements.domain.service.LocalPayment;
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

/** Pagar por la pasarela local un pago pendiente propio (`RF-MV-051`). */
@Tag(name = "Movimientos")
@RestController
@RequestMapping("/api/v1/movements/mine")
public class LocalChargeController {

  private final LocalPayment local;
  private final AuthenticatedActor actor;

  public LocalChargeController(LocalPayment local, AuthenticatedActor actor) {
    this.local = local;
    this.actor = actor;
  }

  @PostMapping("/{id}/local-charge")
  @PreAuthorize("hasAuthority('movements:pay-pending-locally')")
  @Operation(
      summary = "Pagar por la pasarela local una compra pendiente propia",
      description =
          """
          Devuelve el **cobro de la pasarela local** de una venta o una compra de puntos propia
          con su pago pendiente con `PSE` (`RF-MV-051`): **el mismo** si ya estaba abierto
          —el cliente cerró la página de pago—, o **uno nuevo** si nadie lo había abierto —la
          venta que registró un funcionario—, en la moneda del país de quien paga con su
          conversión vigente (`RN-MV-063`). La app lleva al cliente a `checkoutUrl`. **No
          confirma nada**: lo confirma la consulta a la pasarela que disparan su aviso o el
          barrido (`RN-MV-064`). Sin cuerpo.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El cobro, con su página de pago."),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:pay-pending-locally` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe, o no es suya (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "Sin pago pendiente, con un pago pendiente de otro método (`EX-002`), o el país de"
                + " quien paga sin conversión vigente (`RN-MV-063`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "La pasarela rechazó el cobro (`RN-MV-063`)",
        content = @Content),
    @ApiResponse(
        responseCode = "503",
        description = "La pasarela local no está configurada o no respondió",
        content = @Content)
  })
  public LocalChargeResponse pagarPorLaPasarelaLocal(@PathVariable UUID id) {
    return local.retomar(id, actor.id());
  }
}
