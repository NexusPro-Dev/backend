package com.factech.nexus.modules.indicators.interfaces;

import com.factech.nexus.modules.indicators.application.SalesIndicatorRequest;
import com.factech.nexus.modules.indicators.application.SalesSummaryResponse;
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

  public SalesIndicatorsController(GetSalesSummaryService resumen) {
    this.resumen = resumen;
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
          `to` incluidos, el último entero. Sin fechas, el mes en curso hasta hoy; con solo
          `to`, desde el primero de su mes. Como mucho **366 días**. La respuesta devuelve el
          periodo efectivo.

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
                + " o un periodo de más de 366 días (`VAL-003`).",
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
      @RequestParam(required = false) UUID sellerId) {
    return resumen.get(new SalesIndicatorRequest(from, to, currencyId, sellerId));
  }
}
