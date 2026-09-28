package com.factech.nexus.modules.commissions.interfaces;

import com.factech.nexus.modules.commissions.application.CommissionClosingResponse;
import com.factech.nexus.modules.commissions.domain.service.CloseCommissionPeriodService;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los lotes de comisión (`RF-CM-009` a `RF-CM-012`): cerrarlos, consultarlos y pagarlos.
 *
 * <p><b>No hay ruta para crear un lote</b>: nace solo, con la primera comisión de una persona en
 * una moneda (`RF-CM-013`, `RN-CM-033`).
 */
@Tag(
    name = "Lotes de comisión",
    description =
        "Lo que se le debe a cada persona, por periodo y moneda: abierto mientras crece, pendiente"
            + " tras el cierre, pagado cuando Finanzas lo abona en la billetera.")
@RestController
@RequestMapping("/api/v1/commission-batches")
public class CommissionBatchController {

  private final CloseCommissionPeriodService cierre;
  private final AuthenticatedActor actor;

  public CommissionBatchController(CloseCommissionPeriodService cierre, AuthenticatedActor actor) {
    this.cierre = cierre;
    this.actor = actor;
  }

  @Operation(
      summary = "Cerrar el periodo a mano",
      description =
          """
          **Hace lo mismo que el cierre programado**, y existe para relanzar el que no corrió
          (`RF-CM-009`, `RN-CM-035`). Primero **barre**: devenga las líneas cobradas y con
          vendedor que se quedaron sin desenlace, y reintenta las rechazadas por pasar del 100 %
          (`RN-CM-034`). Después pasa **todos** los lotes abiertos a `PENDIENTE`, con el instante
          del cierre como fin de periodo. Lo que devengue un segundo después abre un lote nuevo.

          **Sin cuerpo.** Responde la constancia del cierre, que queda además consultable en
          `GET /commission-closings`. **`200` y no `201`**: se ejecuta una acción, no se crea un
          recurso que se vaya a leer por su dirección.

          Si hay otro cierre en curso —el programado, u otro manual—, **`409`** y no se cierra
          nada.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Cerrado, aunque fuesen cero lotes"),
    @ApiResponse(responseCode = "401", description = "Sin token"),
    @ApiResponse(responseCode = "403", description = "Sin `commission-batches:settle`"),
    @ApiResponse(responseCode = "409", description = "Hay un cierre en curso")
  })
  @PostMapping("/closing")
  @PreAuthorize("hasAuthority('commission-batches:settle')")
  public CommissionClosingResponse cerrar() {
    return cierre.closeManually(actor.id());
  }
}
