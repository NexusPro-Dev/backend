package com.factech.nexus.modules.system.exchangerates.application;

import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog.CurrencyView;
import com.factech.nexus.modules.system.exchangerates.domain.models.ExchangeRate;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * La tasa tal como sale de la API.
 *
 * <p><b>{@code JsonInclude.ALWAYS} no es decorativo</b>: sin él, la fecha de fin de una tasa
 * vitalicia llegaría <b>ausente</b> en lugar de nula, y un campo que falta es indistinguible de uno
 * que el cliente no conoce.
 *
 * <p><b>Las dos monedas llegan resueltas</b> y no como identificadores sueltos: resolverlas cuesta
 * cero consultas extra, porque la validación del alta ya las trajo del catálogo.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ExchangeRateResponse(
    UUID id,
    CurrencyRef sourceCurrency,
    CurrencyRef targetCurrency,
    BigDecimal price,
    LocalDate validFrom,
    LocalDate validTo,
    boolean isActive) {

  /**
   * La moneda, con su nombre y los decimales que declara (`CA-SP-530`).
   *
   * <p><b>No es el {@code CurrencyRef} del hotlink</b>, que lleva dos campos y ningún
   * identificador: aquí responde una vista de administración y allí una ruta pública. Compartir la
   * forma habría publicado sin token lo que esta necesita.
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record CurrencyRef(UUID id, String code, String name, int decimalPlaces) {}

  public static ExchangeRateResponse from(
      ExchangeRate tasa, CurrencyView origen, CurrencyView destino) {
    return new ExchangeRateResponse(
        tasa.getId(),
        new CurrencyRef(origen.id(), origen.code(), origen.name(), origen.decimalPlaces()),
        new CurrencyRef(destino.id(), destino.code(), destino.name(), destino.decimalPlaces()),
        // El precio sale con su escala declarada y NO con la de ninguna moneda:
        // una tasa no está expresada en ninguna de las dos.
        tasa.getPrice(),
        tasa.getValidFrom(),
        tasa.getValidTo(),
        tasa.isActive());
  }
}
