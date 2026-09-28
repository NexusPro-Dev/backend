package com.factech.nexus.modules.commissions.interfaces;

import com.factech.nexus.modules.commissions.application.CommissionClosingPageResponse;
import com.factech.nexus.modules.commissions.application.ListCommissionClosingsRequest;
import com.factech.nexus.modules.commissions.domain.service.ListCommissionClosingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Los cierres del periodo de comisiones (`RF-CM-009`). */
@Tag(
    name = "Cierres de comisiones",
    description = "Cada cierre del periodo, programado o a mano, con lo que hizo.")
@RestController
@RequestMapping("/api/v1/commission-closings")
public class CommissionClosingController {

  private final ListCommissionClosingsService cierres;

  public CommissionClosingController(ListCommissionClosingsService cierres) {
    this.cierres = cierres;
  }

  @Operation(
      summary = "Consultar los cierres",
      description =
          """
          Los cierres del periodo, **del más reciente al más antiguo** (`RF-CM-009`,
          `CA-CM-180`): cuándo empezó y cuándo cerró cada uno, si fue programado o a mano —y
          quién—, cuántos lotes cerró, cuántas líneas recogió el barrido y cuántas rechazadas
          reintentó y recuperó.

          **Existe porque un proceso programado no tiene a quién contestar**: es la forma de saber
          si el cierre de anoche corrió. **`closedAt` nulo** es un cierre en curso o uno que falló
          a medias; sus lotes siguen abiertos. **`linesSwept` distinto de cero** quiere decir que
          el aviso de las ventas se está perdiendo y el barrido lo está tapando.

          Filtros: `origin` (`PROGRAMADO`, `MANUAL`) y `from`/`to` sobre el inicio. Los errores
          de los filtros salen **todos juntos**.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de cierres"),
    @ApiResponse(responseCode = "400", description = "Filtros inválidos"),
    @ApiResponse(responseCode = "401", description = "Sin token"),
    @ApiResponse(responseCode = "403", description = "Sin `commission-closings:read`")
  })
  @GetMapping
  @PreAuthorize("hasAuthority('commission-closings:read')")
  public CommissionClosingPageResponse listar(
      @ModelAttribute ListCommissionClosingsRequest filtros) {
    return cierres.list(filtros);
  }
}
