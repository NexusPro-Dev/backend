package com.factech.nexus.modules.commissions.interfaces;

import com.factech.nexus.modules.commissions.application.AfftrackProgressResponse;
import com.factech.nexus.modules.commissions.application.AfftrackSettlementPageResponse;
import com.factech.nexus.modules.commissions.application.ListAfftrackSettlementsRequest;
import com.factech.nexus.modules.commissions.domain.service.GetOwnAfftrackProgressService;
import com.factech.nexus.modules.commissions.domain.service.ListAfftrackSettlementsService;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las liquidaciones afftrack de cada cierre (`RF-CM-021`) y mi avance hacia la próxima
 * (`RF-CM-030`).
 */
@Tag(
    name = "Comisiones afftrack",
    description =
        "Escalones por FTD —membresía BECA → BECA activada— que se liquidan en cada cierre.")
@RestController
@RequestMapping("/api/v1/afftrack-settlements")
public class AfftrackSettlementController {

  private final ListAfftrackSettlementsService listado;
  private final GetOwnAfftrackProgressService avance;
  private final AuthenticatedActor actor;

  public AfftrackSettlementController(
      ListAfftrackSettlementsService listado,
      GetOwnAfftrackProgressService avance,
      AuthenticatedActor actor) {
    this.listado = listado;
    this.avance = avance;
    this.actor = actor;
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

  @Operation(
      summary = "Consultar mi avance de FTD en el periodo abierto",
      description =
          """
          **Lo que el próximo cierre me liquidaría si corriera ahora** (`RF-CM-030`,
          `RN-CM-056`), por producto FTD: `carriedIn` —el remanente de mi última
          liquidación—, `newFtds` —los activados y aún no contados, repartidos en `ownFtds`
          (los que vendí) y `networkFtds` (los de mi red, con la cadena del día de cada
          activación)—, `available` = los dos, `reachedTier` —el mayor escalón alcanzado,
          con `estimatedAmount` = límite × valor— y `remainder`, lo que sobraría; y
          `nextTier`, el escalón siguiente con `missing`, cuántos faltan. Sin escalón
          alcanzado o sin siguiente, viajan nulos.

          **Es una estimación**: se calcula con la escala vigente hoy y no guarda nada; el
          cierre usa la escala del último día del periodo y los FTD que haya en su instante.
          Sale un producto si tengo FTD nuevos, remanente o escala vigente; por código.
          `periodStart` es el corte del último cierre (nulo sin cierres) y `nextClosingAt`
          el próximo cierre programado (nulo si está apagado).

          La persona la pone el token: **no hay filtro de persona**.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Mi avance, por producto FTD"),
    @ApiResponse(responseCode = "401", description = "Sin token"),
    @ApiResponse(responseCode = "403", description = "Sin `afftrack-settlements:read-own-progress`")
  })
  @GetMapping("/mine/progress")
  @PreAuthorize("hasAuthority('afftrack-settlements:read-own-progress')")
  public AfftrackProgressResponse miAvance() {
    return avance.get(actor.id());
  }
}
