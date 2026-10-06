package com.factech.nexus.modules.indicators.interfaces;

import com.factech.nexus.modules.indicators.application.PointsSummaryResponse;
import com.factech.nexus.modules.indicators.application.SalesIndicatorRequest;
import com.factech.nexus.modules.indicators.domain.service.GetPointsSummaryService;
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
 * Los indicadores de puntos (`IN`, `RF-IN-005`). Un controlador por tanda, como el de ventas, bajo
 * la misma raíz {@code /indicators}.
 */
@Tag(
    name = "Indicadores",
    description =
        "Cifras agregadas de la plataforma. Cada indicador tiene su permiso, y sus cifras se"
            + " acotan al alcance de quien pregunta.")
@RestController
@RequestMapping("/api/v1/indicators")
public class PointsIndicatorsController {

  private final GetPointsSummaryService resumen;

  public PointsIndicatorsController(GetPointsSummaryService resumen) {
    this.resumen = resumen;
  }

  @GetMapping("/points/summary")
  @PreAuthorize("hasAuthority('indicators:read-points-summary')")
  @Operation(
      summary = "Consultar el resumen de puntos",
      description =
          """
          **La suma de las filas de la lista de los movimientos de puntos** de administración
          (`GET /movements/points-movements`, `RF-MV-056`): la misma definición y la misma fecha,
          de modo que con los mismos filtros cuadran. Por cada moneda:

          - **`purchases`**, las compras de puntos (`COMPRA_PUNTOS`) **por estado** —`confirmed`,
            `pending`, `rejected`—, cada una con `count`, `points` y **`amount`, lo pagado**.
            Los puntos de una pendiente o rechazada son los que daría o habría dado: **no mueven
            el saldo**.
          - **`spent`**, las ventas pagadas con puntos (`GASTO_PUNTOS`): `count` y los `points`
            que de verdad se descontaron.
          - **`adjustments`**, los ajustes a mano (`AJUSTE_PUNTOS`): `added` y `removed`.
          - **`balance`, el saldo de HOY**: no lo acotan ni el periodo, ni `type`, ni `status`.

          Todo en positivo: el sentido lo da el nombre. **La fecha es la de la fila de la lista**:
          cuándo ocurrió la compra o el ajuste —una compra pedida el 30 y cobrada el 1 cuenta el
          30— y cuándo se descontaron los puntos de un gasto. Sin filtros, `balance =
          purchases.confirmed.points − spent.points + added.points − removed.points`.

          **`type`** (`COMPRA_PUNTOS`, `AJUSTE_PUNTOS`, `GASTO_PUNTOS`) y **`status`**
          (`PENDIENTE`, `CONFIRMADA`, `RECHAZADA`) acotan como en la lista. **De quién son los
          puntos lo decide el tipo de rol de quien pregunta**: un **funcionario**, los de todas las
          personas; un **vendedor**, los suyos y los de su red; cualquier otro, los suyos.
          `userId` acota a una persona titular de mi alcance; fuera de él la lista sale vacía. El
          periodo: días de Bogotá, sin fechas toda la historia, sin tope.

          **`granularity`** (`DAY`, `WEEK`, `MONTH`) añade `buckets`: lo mismo por tramo, con las
          monedas de `currencies` en su orden y con ceros; **el saldo no se parte**. Ni los
          permisos de ventas ni los de la lista de puntos abren este.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El resumen, una entrada por moneda."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Fecha o identificador malformado (`VAL-001`), `from` posterior a `to` (`VAL-002`)"
                + ", un tramo que no es `DAY`, `WEEK` ni `MONTH` (`VAL-005`), un `type` (`VAL-006`) o"
                + " un `status` (`VAL-007`) desconocidos; los de negocio, juntos.",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin el permiso `indicators:read-points-summary` (`AUTH-002`).",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public PointsSummaryResponse resumen(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID currencyId,
      @RequestParam(required = false) UUID userId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String granularity) {
    return resumen.get(
        new SalesIndicatorRequest(from, to, currencyId, userId), type, status, granularity);
  }
}
