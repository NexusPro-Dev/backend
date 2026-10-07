package com.factech.nexus.modules.indicators.interfaces;

import com.factech.nexus.modules.indicators.application.CommissionBatchesSummaryResponse;
import com.factech.nexus.modules.indicators.domain.service.GetCommissionBatchesSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los indicadores de comisiones (`IN`, `RF-IN-007`). Un controlador por tanda, como los de ventas y
 * puntos, bajo la misma raíz {@code /indicators}.
 */
@Tag(
    name = "Indicadores",
    description =
        "Cifras agregadas de la plataforma. Cada indicador tiene su permiso, y sus cifras se"
            + " acotan al alcance de quien pregunta.")
@RestController
@RequestMapping("/api/v1/indicators")
public class CommissionIndicatorsController {

  private final GetCommissionBatchesSummaryService lotes;

  public CommissionIndicatorsController(GetCommissionBatchesSummaryService lotes) {
    this.lotes = lotes;
  }

  @GetMapping("/commissions/batches/summary")
  @PreAuthorize("hasAuthority('indicators:read-commission-batches-summary')")
  @Operation(
      summary = "Consultar el resumen de lotes de comisiones",
      description =
          """
          Los lotes de comisiones **como están ahora**, en cuatro bloques —**`open`**, los
          abiertos, que siguen creciendo con cada comisión que se devenga; **`pending`**, los
          cerrados que esperan a que se paguen; **`paid`**, los ya abonados; y **`total`**, todos—,
          cada uno con **`batches`**, cuántos lotes, y **`amounts`**, su valor por moneda: la suma
          del total que cada lote tiene hoy, que ya descuenta lo retirado o revertido. **Los
          cuatro bloques vienen siempre**, en cero si no hay lotes. `amounts` es una lista, un
          valor por moneda, y nunca hay que sumarla.

          **Es una foto de hoy: no tiene periodo.** No acepta `from`, `to` ni `granularity`; si
          llegan, se ignoran, y la respuesta no lleva `period`. El único filtro es `currencyId`:
          solo los lotes de esa moneda, y ceros si no existe.

          **No se acota por alcance**: quien porte el permiso ve los lotes de todas las personas.
          El permiso se siembra solo a administración; dárselo a un rol vendedor es darle esta
          vista entera. Ni `commission-batches:read` ni otro permiso de indicadores abren este.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El resumen, aunque sea de ceros."),
    @ApiResponse(
        responseCode = "400",
        description = "`currencyId` malformado (`VAL-001`).",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin el permiso `indicators:read-commission-batches-summary` (`AUTH-002`).",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public CommissionBatchesSummaryResponse resumenDeLotes(
      @RequestParam(required = false) UUID currencyId) {
    return lotes.get(currencyId);
  }
}
