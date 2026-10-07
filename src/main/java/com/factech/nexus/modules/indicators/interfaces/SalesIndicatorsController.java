package com.factech.nexus.modules.indicators.interfaces;

import com.factech.nexus.modules.indicators.application.SaleLinesSummaryResponse;
import com.factech.nexus.modules.indicators.application.SalesIndicatorRequest;
import com.factech.nexus.modules.indicators.application.SalesSeriesResponse;
import com.factech.nexus.modules.indicators.application.SalesSummaryResponse;
import com.factech.nexus.modules.indicators.domain.service.GetSaleLinesSummaryService;
import com.factech.nexus.modules.indicators.domain.service.GetSalesSeriesService;
import com.factech.nexus.modules.indicators.domain.service.GetSalesSummaryService;
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
 * Los indicadores de ventas (`IN`, `RF-IN-001` a `RF-IN-004`).
 *
 * <p><b>La raíz es {@code /indicators}, y bajo ella un segmento por tanda</b> —{@code /sales} hoy—
 * y uno por indicador. <b>Cada indicador tiene su permiso, y ese permiso es el reparto por rol</b>
 * (`RN-IN-001`): el frontend sabe qué tarjetas pintar por los permisos efectivos del perfil propio.
 */
@Tag(
    name = "Indicadores",
    description =
        "Cifras agregadas de la plataforma. Cada indicador tiene su permiso, y sus cifras se"
            + " acotan al alcance de quien pregunta.")
@RestController
@RequestMapping("/api/v1/indicators")
public class SalesIndicatorsController {

  private final GetSalesSummaryService resumen;
  private final GetSalesSeriesService evolucion;
  private final GetSaleLinesSummaryService lineas;

  public SalesIndicatorsController(
      GetSalesSummaryService resumen,
      GetSalesSeriesService evolucion,
      GetSaleLinesSummaryService lineas) {
    this.resumen = resumen;
    this.evolucion = evolucion;
    this.lineas = lineas;
  }

  @GetMapping("/sales/summary")
  @PreAuthorize("hasAuthority('indicators:read-sales-summary')")
  @Operation(
      summary = "Consultar el resumen de ventas",
      description =
          """
          Devuelve **cuánto se vendió en un periodo**: las ventas **confirmadas** —cuántas,
          cuántas líneas, cuántas unidades y por cuánto—, y aparte las **pendientes** y las
          **anuladas**, cada una con su cantidad y su importe. Solo cuentan las ventas: una
          compra de puntos no es una venta.

          **`total` es el número de ventas sea cual sea su estado**, y **cada bloque dice en
          `free` cuántas fueron gratuitas** (desde el 06-10-2026): una venta es gratuita si su
          importe a pagar —el de la venta entera— es cero, como la del alta por enlace. **Las
          gratuitas siguen contando en `sales`**, de modo que las pagadas son
          `confirmed.sales - confirmed.free`.

          **Lo que se ve lo decide el tipo de rol de quien pregunta**, como en
          `GET /movements/sales`: un **funcionario** ve toda la plataforma, también las líneas
          que aún no tienen vendedor; un **vendedor** ve lo que vendió **él y su red**, en toda
          la profundidad; cualquier otro, lo que vendió él.

          **Se cuenta por LÍNEA, no por venta.** Una venta con una línea de mi red y otra de una
          rama ajena cuenta **una** venta con **solo** el importe de mi línea. Por eso el importe
          de un vendedor **puede ser menor** que la suma de los totales de las ventas que ve en
          su listado: las dos cifras son correctas y responden preguntas distintas.

          **Un importe por moneda**, nunca sumados entre sí; una moneda sin ventas no aparece.
          Las cantidades sí se suman.

          **El periodo se pide en días** de la zona del negocio (`America/Bogota`): `from` y
          `to` incluidos, el último entero. **Sin fechas, toda la historia** hasta hoy; con una
          sola, la otra queda abierta —solo `from`, hasta hoy; solo `to`, desde el principio—.
          **Sin tope de días.** La respuesta devuelve el periodo efectivo, con `from` **nulo**
          si no se pidió.

          **`granularity`** (`DAY`, `WEEK` o `MONTH`, opcional) parte las cifras en tramos del
          calendario de Bogotá —la semana de lunes—: la respuesta trae además `buckets`, uno por
          tramo y todos presentes, del primero con datos —o del de `from`— al de hoy —o al de
          `to`—, y **la suma de los tramos es el total**. Sin `granularity`, `buckets` y
          `granularity` vienen nulos y la respuesta es la de siempre.

          `sellerId` acota a una persona **de mi alcance**; fuera de él —o inexistente— la
          respuesta son **ceros**, y no un error, para que el filtro no sirva para descubrir
          quién cuelga de quién. Una `currencyId` inexistente también da ceros. Ni
          `movements:list-sales` ni otro permiso de indicadores abren este.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El resumen, aunque sea de ceros."),
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
        description = "Sin el permiso `indicators:read-sales-summary` (`AUTH-002`).",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public SalesSummaryResponse resumen(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID currencyId,
      @RequestParam(required = false) UUID sellerId,
      @RequestParam(required = false) String granularity) {
    return resumen.get(new SalesIndicatorRequest(from, to, currencyId, sellerId), granularity);
  }

  @GetMapping("/sales/series")
  @PreAuthorize("hasAuthority('indicators:read-sales-series')")
  @Operation(
      summary = "Consultar la evolución de las ventas",
      description =
          """
          Lo **confirmado** del periodo partido en tramos de **día**, **semana** o **mes**
          (`granularity`, por defecto `DAY`), para dibujarlo. Cuenta exactamente lo mismo que el
          resumen —por línea, un importe por moneda, el alcance de quien pregunta—, y **la suma
          de los tramos es lo confirmado del resumen** para el mismo periodo y filtros.

          **Cada tramo del periodo aparece, también los vacíos, con ceros**: una serie con huecos
          se dibuja uniendo puntos lejanos. **Cada tramo trae un importe por cada moneda de
          `currencies`, en ese orden**, con cero donde no vendió en ella, de modo que cada moneda
          es una serie completa.

          Los tramos son **del calendario de Bogotá**: la semana va de **lunes a domingo** y el
          mes del uno al último día. `start` dice dónde empieza el tramo en el calendario; **el
          primero y el último pueden estar recortados** por el periodo, que es el que se devuelve
          en `period`. `from`, `to`, `currencyId` y `sellerId` son los del resumen, con los mismos
          ceros fuera del alcance; **sin `from`, la serie empieza en el tramo de la primera venta**
          (sin ventas, es el tramo de hoy), y no hay tope. Ni el permiso del resumen ni
          otro de indicadores abren este.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La serie, aunque sea de ceros."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Fecha o identificador malformado (`VAL-001`), `from` posterior a `to` (`VAL-002`),"
                + " o un tramo que no es `DAY`, `WEEK` ni `MONTH`"
                + " (`VAL-005`). Los problemas del periodo y del tramo se devuelven juntos.",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin el permiso `indicators:read-sales-series` (`AUTH-002`).",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public SalesSeriesResponse evolucion(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID currencyId,
      @RequestParam(required = false) UUID sellerId,
      @RequestParam(required = false) String granularity) {
    return evolucion.get(new SalesIndicatorRequest(from, to, currencyId, sellerId), granularity);
  }

  /**
   * <b>Bajo {@code /sales/lines}</b>, como {@code GET /movements/sales/lines} en `MV`: la tanda de
   * ventas mirada desde las líneas. <b>Sin alcance</b> (`RN-IN-011`).
   */
  @GetMapping("/sales/lines/summary")
  @PreAuthorize("hasAuthority('indicators:read-sale-lines-summary')")
  @Operation(
      summary = "Consultar el resumen de líneas de venta",
      description =
          """
          Sobre **todas** las líneas de venta de la plataforma, dos bloques, cada uno en
          **`total`** y **`byType`, por tipo de producto** (`BOT`, `UPGRADE_MEMBRESIA`):

          - **`sold`, lo vendido**: solo las ventas **confirmadas** —las ventas, las líneas,
            **las unidades** (los productos vendidos: la suma de las cantidades) y el importe por
            moneda—.
          - **`unassigned`, lo que no tiene vendedor**: las líneas sin vendedor de las ventas
            **no anuladas** —lo que falta por atribuir—, con las mismas cifras.

          El tipo es el del producto de la línea. **Una venta con líneas de dos tipos cuenta en
          cada uno**: las ventas por tipo pueden sumar más que el total; las líneas, las unidades
          y los importes, no. No hay pendientes ni anuladas en la respuesta.

          **No se acota por alcance**: quien porte el permiso ve las cifras de toda la plataforma,
          sea cual sea su tipo de rol. El permiso se siembra solo a administración; dárselo a un
          rol vendedor es darle esta vista entera. El total de lo vendido es lo confirmado que ve
          administración en `GET /indicators/sales/summary`.

          El periodo, la moneda y `granularity` son los de los demás indicadores: días de
          Bogotá, sin fechas toda la historia, sin tope; con `granularity`, `buckets` trae los
          mismos bloques por tramo, todos presentes, y su suma es el total. **No hay filtro por
          vendedor.** Ni `movements:list-sale-lines` ni otro permiso de indicadores abren este.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El resumen, aunque sea de ceros."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Fecha o identificador malformado (`VAL-001`), `from` posterior a `to` (`VAL-002`)"
                + " o un tramo desconocido (`VAL-005`); los dos últimos, juntos.",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin el permiso `indicators:read-sale-lines-summary` (`AUTH-002`).",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public SaleLinesSummaryResponse resumenDeLineas(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID currencyId,
      @RequestParam(required = false) String granularity) {
    return lineas.get(from, to, currencyId, granularity);
  }
}
