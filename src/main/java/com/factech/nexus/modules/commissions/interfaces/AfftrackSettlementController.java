package com.factech.nexus.modules.commissions.interfaces;

import com.factech.nexus.modules.commissions.application.AfftrackSettlementPageResponse;
import com.factech.nexus.modules.commissions.application.ListAfftrackSettlementsRequest;
import com.factech.nexus.modules.commissions.domain.service.ListAfftrackSettlementsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Las liquidaciones afftrack de cada cierre (`RF-CM-021`). */
@Tag(
    name = "Comisiones afftrack",
    description =
        "Escalones por FTD —membresía BECA → BECA activada— que se liquidan en cada cierre.")
@RestController
@RequestMapping("/api/v1/afftrack-settlements")
public class AfftrackSettlementController {

  private final ListAfftrackSettlementsService listado;

  public AfftrackSettlementController(ListAfftrackSettlementsService listado) {
    this.listado = listado;
  }

  @Operation(
      summary = "Consultar las liquidaciones afftrack",
      description =
          """
          Lo que **cada cierre** hizo con los FTD de cada persona y producto
          (`RF-CM-020`): `carriedIn` —el remanente con el que llegó—, `newFtds`
          —los contados en ese cierre, repartidos en `ownFtds` y `networkFtds`—,
          `paidFtds` y `carriedOut` —**el remanente para el siguiente cierre**—.
          Si alcanzó un escalón, `tier` dice cuál **tal como se pagó** —aunque
          después se corrigiera o retirara— y `amount` lo pagado; si no, los dos
          son nulos.

          Del cierre más reciente al más antiguo. Filtros `userId`, `productId`,
          `closingId`, `from`/`to` sobre el instante del cierre y `paid`
          (`true`: solo las que pagaron). **Filtrando por persona y producto, la
          primera fila es su remanente vigente.**
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de liquidaciones"),
    @ApiResponse(responseCode = "400", description = "Filtros inválidos"),
    @ApiResponse(responseCode = "401", description = "Sin token"),
    @ApiResponse(responseCode = "403", description = "Sin `afftrack-settlements:read`")
  })
  @GetMapping
  @PreAuthorize("hasAuthority('afftrack-settlements:read')")
  public AfftrackSettlementPageResponse listar(
      @ModelAttribute ListAfftrackSettlementsRequest filtros) {
    return listado.list(filtros);
  }
}
