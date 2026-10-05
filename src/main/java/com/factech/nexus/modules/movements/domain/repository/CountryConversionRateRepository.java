package com.factech.nexus.modules.movements.domain.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * La conversión por país (`RN-MV-062`, `requirements/mv.md` §7.15): un histórico, como las tasas de
 * puntos. <b>La vigente se resuelve solo aquí</b>, para que el cobro y el retiro por la pasarela
 * local la lean igual que su consulta (`RF-MV-046` · `plan.md` §1).
 */
public interface CountryConversionRateRepository {

  /** La vigente del país en ese instante: la de {@code valid_from} más reciente no futura. */
  Optional<ConversionRow> current(UUID countryId, OffsetDateTime at);

  /**
   * La vigente de cada país <b>activo</b> que tenga una, ordenadas por código de país, en una sola
   * sentencia (`RF-MV-047`). Con {@code countryId}, solo la de ese país.
   */
  List<ConversionRow> currentOfActiveCountries(OffsetDateTime at, UUID countryId);

  void insert(
      UUID id,
      UUID countryId,
      UUID currencyId,
      UUID baseCurrencyId,
      BigDecimal payInPrice,
      BigDecimal payoutPrice,
      OffsetDateTime validFrom,
      UUID by);

  record ConversionRow(
      UUID id,
      UUID countryId,
      String countryCode,
      String countryName,
      UUID currencyId,
      String currencyCode,
      int currencyDecimalPlaces,
      UUID baseCurrencyId,
      String baseCurrencyCode,
      int baseCurrencyDecimalPlaces,
      BigDecimal payInPrice,
      BigDecimal payoutPrice,
      OffsetDateTime validFrom) {}
}
