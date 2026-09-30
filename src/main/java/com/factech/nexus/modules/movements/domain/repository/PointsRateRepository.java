package com.factech.nexus.modules.movements.domain.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de las tasas de puntos (`RN-MV-050`; `RF-MV-025` · `plan.md` §3).
 *
 * <p><b>La vigente se resuelve siempre aquí</b>: la fila de esa moneda con el {@code valid_from}
 * más reciente que no pase del instante. Fijar, consultar, comprar y pagar con puntos la leen por
 * el mismo método para que no puedan discrepar.
 */
public interface PointsRateRepository {

  /** La tasa que rige en esa moneda en ese instante, o vacío si no hay ninguna. */
  Optional<RateRow> current(UUID currencyId, OffsetDateTime at);

  /**
   * La vigente de cada moneda <b>activa</b> que la tenga, por código de moneda (`RF-MV-026`). Una
   * sola sentencia.
   */
  List<RateRow> currentOfActiveCurrencies(OffsetDateTime at);

  /** Escribe una tasa nueva. La anterior no se toca. */
  void insert(
      UUID id, UUID currencyId, BigDecimal pointsPerUnit, OffsetDateTime validFrom, UUID by);

  record RateRow(
      UUID id,
      UUID currencyId,
      String currencyCode,
      BigDecimal pointsPerUnit,
      OffsetDateTime validFrom) {}
}
