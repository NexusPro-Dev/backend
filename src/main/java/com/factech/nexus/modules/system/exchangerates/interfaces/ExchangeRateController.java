package com.factech.nexus.modules.system.exchangerates.interfaces;

import com.factech.nexus.modules.system.exchangerates.application.ExchangeRateResponse;
import com.factech.nexus.modules.system.exchangerates.application.RegisterExchangeRateRequest;
import com.factech.nexus.modules.system.exchangerates.domain.service.RegisterExchangeRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tasas de cambio (`RF-SP-047`). */
@RestController
@RequestMapping("/api/v1/exchange-rates")
@Tag(name = "Tasas de cambio", description = "A cuánto se cambia una moneda por otra")
public class ExchangeRateController {

  private final RegisterExchangeRateService alta;

  public ExchangeRateController(RegisterExchangeRateService alta) {
    this.alta = alta;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('exchange-rates:create')")
  @Operation(
      summary = "Registrar una tasa de cambio",
      description =
          """
          Declara **a cuánto se cambia una moneda por otra y desde cuándo**.

          `price` es **cuántas unidades de la moneda de destino da UNA de la de
          origen**: `USD → COP` a `4150` significa que un dólar da cuatro mil
          ciento cincuenta pesos. **La dirección contraria no se deduce** — la
          inversa aritmética casi nunca es la tasa real, y calcularla produciría
          un número plausible y falso. Quien necesite las dos declara **dos
          tasas**.

          **Ocho decimales**, y no los de ninguna moneda: una tasa **no es un
          importe**, es un cociente entre dos monedas. Con cuatro, `COP → USD`
          se guardaría redondeada y una moneda más devaluada, como cero.

          **`validFrom` es obligatoria y `validTo` no**: sin ella la tasa es
          **vitalicia**. Ausente y `null` significan lo mismo. Una vigencia que
          empieza y termina el mismo día es legítima, y una que **empieza en el
          futuro** también — es lo que hace innecesario un estado «programada».

          **`isActive` es opcional y por omisión verdadero.**

          **Dos tasas activas del mismo par no pueden solaparse** (`RN-SP-032`),
          y los **dos extremos de la vigencia cuentan**: una que termina el 30 de
          junio choca con otra que empieza el 30 de junio. El choque devuelve
          `409` y **no registra nada**: el sistema **no** cierra ni recorta la
          tasa anterior, porque resolver el conflicto es una decisión de quien
          administra, no del servidor.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Tasa registrada.",
        content = @Content(schema = @Schema(implementation = ExchangeRateResponse.class))),
    @ApiResponse(responseCode = "400", description = "Datos inválidos (serie `VAL-nnn`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `exchange-rates:create` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "409",
        description = "Se solapa con una tasa vigente del mismo par (`EX-002`)"),
    @ApiResponse(responseCode = "422", description = "Moneda inexistente o desactivada (`EX-001`)")
  })
  public ResponseEntity<ExchangeRateResponse> registrar(
      @Valid @RequestBody RegisterExchangeRateRequest peticion) {

    ExchangeRateResponse creada = alta.register(peticion);
    return ResponseEntity.created(URI.create("/api/v1/exchange-rates/" + creada.id())).body(creada);
  }
}
