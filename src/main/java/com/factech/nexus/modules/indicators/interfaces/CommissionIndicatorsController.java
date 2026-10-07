package com.factech.nexus.modules.indicators.interfaces;

import com.factech.nexus.modules.indicators.application.CommissionBatchesSummaryResponse;
import com.factech.nexus.modules.indicators.application.OwnCommissionsSummaryResponse;
import com.factech.nexus.modules.indicators.domain.service.GetCommissionBatchesSummaryService;
import com.factech.nexus.modules.indicators.domain.service.GetOwnCommissionsSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
  private final GetOwnCommissionsSummaryService mias;

  public CommissionIndicatorsController(
      GetCommissionBatchesSummaryService lotes, GetOwnCommissionsSummaryService mias) {
    this.lotes = lotes;
    this.mias = mias;
  }

  @GetMapping("/commissions/mine/summary")
  @PreAuthorize("hasAuthority('indicators:read-own-commissions-summary')")
  @Operation(
      summary = "Consultar el resumen de mis comisiones",
      description =
          """
          **Mis** comisiones —las de quien pregunta, nunca las de otro ni las de su red—, en
          cuatro bloques según el estado **de hoy** del lote donde está cada una: **`open`**, en
          mi lote abierto, que sigue creciendo; **`pending`**, en lotes cerrados que esperan el
          pago; **`paid`**, en lotes ya pagados; y **`total`**, todas. Cada bloque trae
          **`commissions`**, cuántas, y **`amounts`**, lo que suman por moneda —la del lote—.
          **Los cuatro bloques vienen siempre**, en cero si no hay comisiones.

          **La persona la pone el token**: no hay `sellerId`, y si llega se ignora. Lo que un
          director cobra por la venta de su agente ya es una comisión suya y está aquí; las de su
          agente, no.

          Filtros opcionales y combinables:

          - **`from` y `to`**, días ISO en la zona del negocio: solo las comisiones **nacidas**
            esos días, los dos incluidos —la misma fecha que filtra
            `GET /commission-batches/mine/commissions`—. El estado sigue siendo el de hoy: una
            comisión de septiembre cuyo lote se pagó ayer sale en `paid`. Sin `from`, desde el
            principio; sin `to`, hoy. `period` dice qué días se usaron. `from` posterior a `to`
            es `400` (`VAL-002`).
          - **`currencyId`**: solo las de esa moneda; ceros si no existe.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El resumen, aunque sea de ceros."),
    @ApiResponse(
        responseCode = "400",
        description =
            "`currencyId` o una fecha malformados (`VAL-001`); `from` posterior a `to`"
                + " (`VAL-002`).",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin el permiso `indicators:read-own-commissions-summary` (`AUTH-002`).",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public OwnCommissionsSummaryResponse resumenDeMisComisiones(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID currencyId) {
    return mias.get(from, to, currencyId);
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

          **El estado es el de hoy.** Los filtros eligen **qué lotes** se cuentan, opcionales y
          combinables:

          - **`from` y `to`**, días ISO en la zona del negocio: solo los lotes **cuyo periodo de
            comisiones toca esos días** —empezó antes de que termine `to` y no había terminado
            al empezar `from`—; un lote abierto no tiene fin, y entra si empezó antes de que
            termine `to`. Un lote de septiembre pagado ayer sale en `paid`: las fechas no
            reconstruyen el estado de entonces. Sin `from`, desde el principio; sin `to`, hoy;
            sin ninguno, todos los lotes. `period` dice qué días se usaron. `from` posterior a
            `to` es `400` (`VAL-002`). `granularity` se ignora.
          - **`sellerId`**: solo los lotes de esa persona; ceros si no existe.
          - **`currencyId`**: solo los lotes de esa moneda; ceros si no existe.

          **No se acota por alcance**: quien porte el permiso ve los lotes de todas las personas, y
          `sellerId` solo estrecha lo que ya ve.
          El permiso se siembra solo a administración; dárselo a un rol vendedor es darle esta
          vista entera. Ni `commission-batches:read` ni otro permiso de indicadores abren este.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El resumen, aunque sea de ceros."),
    @ApiResponse(
        responseCode = "400",
        description =
            "`currencyId`, `sellerId` o una fecha malformados (`VAL-001`); `from` posterior a"
                + " `to` (`VAL-002`).",
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
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID currencyId,
      @RequestParam(required = false) UUID sellerId) {
    return lotes.get(from, to, currencyId, sellerId);
  }
}
