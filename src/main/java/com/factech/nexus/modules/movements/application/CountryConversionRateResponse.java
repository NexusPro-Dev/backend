package com.factech.nexus.modules.movements.application;

import com.factech.nexus.modules.movements.domain.repository.CountryConversionRateRepository.ConversionRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** La conversión de un país (`RF-MV-046`, `RF-MV-047`, `RN-MV-062`). */
@Schema(name = "CountryConversionRateResponse")
public record CountryConversionRateResponse(
    UUID id,
    CountryRef country,
    @Schema(description = "La moneda local, a la que se convierte.") CurrencyRef currency,
    @Schema(description = "La moneda de la que se convierte, copiada al fijar: hoy USD.")
        CurrencyRef baseCurrency,
    @Schema(
            description =
                "Al cobrar: cuántas unidades de la moneda local vale una de la base."
                    + " `4150.0000` es «1 USD = 4.150 COP».")
        BigDecimal payInPrice,
    @Schema(description = "Al pagar un retiro, en la misma unidad que `payInPrice`.")
        BigDecimal payoutPrice,
    @Schema(
            description =
                "La tienda de PayRetailers del país; nula si no tiene, y entonces no se cobra"
                    + " por la pasarela local en él.")
        String shopId,
    @Schema(description = "Si la tienda tiene su clave secreta. La clave nunca se devuelve.")
        boolean shopSecretKeySet,
    @Schema(description = "Desde cuándo rige.") OffsetDateTime validFrom) {

  @Schema(name = "ConversionRateCountry")
  public record CountryRef(UUID id, String code, String name) {}

  @Schema(name = "ConversionRateCurrency")
  public record CurrencyRef(UUID id, String code, int decimalPlaces) {}

  public static CountryConversionRateResponse from(ConversionRow fila) {
    return new CountryConversionRateResponse(
        fila.id(),
        new CountryRef(fila.countryId(), fila.countryCode(), fila.countryName()),
        new CurrencyRef(fila.currencyId(), fila.currencyCode(), fila.currencyDecimalPlaces()),
        new CurrencyRef(
            fila.baseCurrencyId(), fila.baseCurrencyCode(), fila.baseCurrencyDecimalPlaces()),
        fila.payInPrice(),
        fila.payoutPrice(),
        fila.shopId(),
        fila.shopSecretKey() != null,
        fila.validFrom());
  }
}
