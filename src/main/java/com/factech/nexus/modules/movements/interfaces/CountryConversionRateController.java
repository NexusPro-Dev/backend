package com.factech.nexus.modules.movements.interfaces;

import com.factech.nexus.modules.movements.application.CountryConversionRateResponse;
import com.factech.nexus.modules.movements.application.SetCountryConversionRateRequest;
import com.factech.nexus.modules.movements.domain.service.CountryConversionRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La conversión por país (`requirements/mv.md` §4.9): a cuánto se convierte la moneda base a la
 * moneda de cada país, al cobrar y al pagar retiros. Bajo {@code /movements} porque solo existe
 * para cobrar y pagar, como la tasa de puntos.
 */
@Tag(
    name = "Conversión por país",
    description =
        "El precio de cobro y el de retiro de cada país, 1 USD = X moneda local. Los usará la"
            + " pasarela local (PayRetailers), que cobra y paga en la moneda del país.")
@RestController
@RequestMapping("/api/v1/movements")
public class CountryConversionRateController {

  private final CountryConversionRateService conversiones;

  public CountryConversionRateController(CountryConversionRateService conversiones) {
    this.conversiones = conversiones;
  }

  @PostMapping("/conversion-rates")
  @PreAuthorize("hasAuthority('movements:set-conversion-rate')")
  @Operation(
      summary = "Fijar la conversión de un país",
      description =
          """
          Fija a cuánto se convierte **una unidad de la moneda base** —la moneda por omisión del
          sistema, hoy USD, que la conversión copia— a la **moneda local** del país, con **dos
          precios**: `payInPrice` al cobrar y `payoutPrice` al pagar un retiro. `4150` es
          «1 USD = 4.150 COP». Los dos son mayores que cero, con hasta cuatro decimales, y se
          fijan juntos (`RF-MV-046`, `RN-MV-062`).

          **Rige desde ese instante**, y la anterior **no se toca**: queda en el histórico, porque
          explicará los cobros y retiros que se hagan con ella. Fijar la que ya rige —misma moneda
          local y mismos dos precios— responde `200` con ella y no escribe nada. País y moneda
          tienen que estar activos, y la moneda local no puede ser la base. No se exige que el
          precio de retiro sea menor que el de cobro.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Conversión nueva, vigente desde ahora."),
    @ApiResponse(responseCode = "200", description = "Ya era la vigente: nada cambió."),
    @ApiResponse(
        responseCode = "400",
        description =
            "País o moneda ausentes, o un precio ausente, no positivo, con más de cuatro decimales"
                + " o más de diez cifras enteras. Los errores salen juntos",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:set-conversion-rate` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "El país o la moneda están inactivos (`EX-003`), o la moneda local es la base"
                + " (`EX-004`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "El país o la moneda no existen (`EX-002`)",
        content = @Content)
  })
  public ResponseEntity<CountryConversionRateResponse> fijar(
      @RequestBody(required = false) SetCountryConversionRateRequest peticion) {
    CountryConversionRateService.SetResult hecho = conversiones.set(peticion);
    return hecho.created()
        ? ResponseEntity.created(URI.create("/api/v1/movements/conversion-rates"))
            .body(hecho.rate())
        : ResponseEntity.ok(hecho.rate());
  }

  @GetMapping("/conversion-rates")
  @PreAuthorize("hasAuthority('movements:read-conversion-rates')")
  @Operation(
      summary = "Consultar la conversión vigente de cada país",
      description =
          """
          La conversión que rige hoy en cada país **activo** que tenga una, ordenadas por código
          de país y sin paginar (`RF-MV-047`). Con `countryId`, solo la de ese país; vacía si no
          tiene. El histórico no se publica. Con ella se calcula cuánto se pagará o se recibirá en
          moneda local; el importe definitivo lo fijará la operación, con la conversión de ese
          momento.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Las vigentes; vacía si ninguna."),
    @ApiResponse(
        responseCode = "400",
        description = "`countryId` sin forma de identificador",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:read-conversion-rates` (`AUTH-002`)",
        content = @Content)
  })
  public List<CountryConversionRateResponse> vigentes(
      @Parameter(description = "Solo la conversión de este país.") @RequestParam(required = false)
          UUID countryId) {
    return conversiones.current(countryId);
  }
}
