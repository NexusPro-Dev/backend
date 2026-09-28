package com.factech.nexus.modules.commissions.interfaces;

import com.factech.nexus.modules.commissions.application.CommissionAccrualPageResponse;
import com.factech.nexus.modules.commissions.application.ListCommissionAccrualsRequest;
import com.factech.nexus.modules.commissions.domain.service.ListCommissionAccrualsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** El desenlace de las líneas de venta a efectos de comisión (`RF-CM-014`). */
@Tag(
    name = "Desenlace de las comisiones",
    description =
        "Qué pasó con cada línea de venta: devengó, no tenía tasa, o se rechazó por pasar del"
            + " 100 %.")
@RestController
@RequestMapping("/api/v1/commission-accruals")
public class CommissionAccrualController {

  private final ListCommissionAccrualsService desenlaces;

  public CommissionAccrualController(ListCommissionAccrualsService desenlaces) {
    this.desenlaces = desenlaces;
  }

  @Operation(
      summary = "Consultar el desenlace de las líneas",
      description =
          """
          **Qué pasó con cada línea de venta cobrada y con vendedor** (`RF-CM-014`,
          `RN-CM-032`), del último intento más reciente al más antiguo:

          - `DEVENGADA`: la cadena cobró; sus comisiones están en los lotes.
          - `SIN_COMISION`: nadie de la cadena tenía tasa sobre el producto. **Definitivo**: no
            se reintenta aunque después se registre una tasa.
          - `RECHAZADA`: la cadena pasaba del 100 % de la línea (`RN-CM-026`). `reason` dice
            cuánto sumaba y cuánto valía la línea, para saber qué tasa corregir. **Cada cierre la
            reintenta**, y `attempts` lo cuenta; en cuanto la tasa se corrige, devenga sola.

          Una línea **sin desenlace todavía** no aparece: la recogerá el barrido del siguiente
          cierre. Filtros `outcome`, `movementId`, `productId`, y `from`/`to` sobre el último
          intento, con los errores todos juntos.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de desenlaces"),
    @ApiResponse(responseCode = "400", description = "Filtros inválidos"),
    @ApiResponse(responseCode = "401", description = "Sin token"),
    @ApiResponse(responseCode = "403", description = "Sin `commission-accruals:read`")
  })
  @GetMapping
  @PreAuthorize("hasAuthority('commission-accruals:read')")
  public CommissionAccrualPageResponse listar(
      @ModelAttribute ListCommissionAccrualsRequest filtros) {
    return desenlaces.list(filtros);
  }
}
