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
          Por cada moneda: los puntos **comprados** —compras de puntos ya cobradas—, los
          **redimidos** —gastados al pagar ventas—, los **sumados** y **restados** por ajustes a
          mano de administración, cada uno con cuántos movimientos los produjeron, y **el saldo
          del periodo**. Todo en positivo: el sentido lo da el nombre de la cifra.

          **Lo comprado, lo redimido y los ajustes son los del periodo**, y el periodo mira
          **cuándo se movieron los puntos**: una compra cuenta el día en que se cobró, no el día
          en que se pidió. **El saldo se calcula con esas cuatro cifras** (desde el 07-10-2026):
          `balance = purchased − redeemed + added − removed` del periodo, y puede ser negativo. Sin
          fechas es toda la historia, es decir, los puntos que hay hoy.

          **De quién son los puntos lo decide el tipo de rol de quien pregunta**, como en los
          indicadores de ventas: un **funcionario** ve los de todas las personas; un
          **vendedor**, los suyos y los de las personas de su red; cualquier otro, los suyos.
          `userId` acota a **una persona titular de mi alcance**; fuera de él —o inexistente— la
          lista sale vacía, y no un error. El periodo es el de los demás indicadores: días de
          Bogotá, **sin fechas toda la historia** —y entonces el saldo son los puntos que hay hoy—, una sola fecha deja la otra abierta, sin tope.

          **`granularity`** (`DAY`, `WEEK` o `MONTH`, opcional) añade `buckets`: por tramo,
          las cuatro clases de cada moneda de `currencies`, en su orden y con ceros; todos los
          tramos presentes, y su suma es la del periodo. Cada tramo trae también **su saldo**, con sus
          cuatro cifras. Ni los permisos de
          ventas ni el de los saldos de una persona abren este.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El resumen, una entrada por moneda."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Fecha o identificador malformado (`VAL-001`), `from` posterior a `to` (`VAL-002`)"
                + " o un tramo que no es `DAY`, `WEEK` ni `MONTH` (`VAL-005`); los dos últimos, juntos.",
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
      @RequestParam(required = false) String granularity) {
    return resumen.get(new SalesIndicatorRequest(from, to, currencyId, userId), granularity);
  }
}
