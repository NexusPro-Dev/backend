package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fijar la conversión de un país (`RF-MV-046`). <b>Sin anotaciones de validación</b>, como la tasa
 * de puntos: el servicio reúne los errores de los cuatro campos y los devuelve juntos.
 */
@Schema(name = "SetCountryConversionRateRequest")
public record SetCountryConversionRateRequest(
    @Schema(description = "El país cuya conversión se fija.") UUID countryId,
    @Schema(description = "La moneda local del país, a la que se convierte.") UUID currencyId,
    @Schema(description = "Precio al cobrar: 1 unidad de la moneda base = X moneda local.")
        BigDecimal payInPrice,
    @Schema(description = "Precio al pagar un retiro, en la misma unidad.") BigDecimal payoutPrice,
    @Schema(
            description =
                "La tienda de PayRetailers del país (`RN-MV-063`): una por país. Opcional: sin"
                    + " ella se hereda la de la conversión vigente.")
        String shopId,
    @Schema(
            description =
                "La clave secreta de esa tienda. Se guarda cifrada y NUNCA se devuelve."
                    + " Obligatoria con una tienda nueva; sin ella, se hereda la vigente de la"
                    + " misma tienda.",
            accessMode = Schema.AccessMode.WRITE_ONLY)
        String secretKey) {

  /** Sin la clave: que no la escriba ningún registro. */
  @Override
  public String toString() {
    return "SetCountryConversionRateRequest[countryId="
        + countryId
        + ", currencyId="
        + currencyId
        + ", payInPrice="
        + payInPrice
        + ", payoutPrice="
        + payoutPrice
        + ", shopId="
        + shopId
        + ", secretKey="
        + (secretKey == null ? "null" : "***")
        + "]";
  }
}
