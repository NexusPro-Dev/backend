package com.factech.nexus.modules.commissions.domain.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lectura de los escalones afftrack de persona, con la persona y el producto resueltos
 * (`RF-CM-019`).
 */
public interface UserAfftrackRateQueryRepository {

  List<UserAfftrackRateRow> search(UserAfftrackRateFilters filtros, int offset, int limit);

  long count(UserAfftrackRateFilters filtros);

  Optional<UserAfftrackRateRow> findRow(UUID id);

  /**
   * {@code onDate} aplica el mismo predicado de vigencia que el cierre (`AfftrackSql.VIGENTE_EN`).
   */
  record UserAfftrackRateFilters(
      UUID userId, UUID productId, LocalDate onDate, boolean includeDeleted) {}

  record UserAfftrackRateRow(
      UUID id,
      UUID userId,
      String username,
      String fullName,
      UUID productId,
      String productCode,
      String productName,
      UUID currencyId,
      String currencyCode,
      int currencyDecimalPlaces,
      int threshold,
      BigDecimal amountPerFtd,
      BigDecimal amountAtThreshold,
      LocalDate validFrom,
      LocalDate validTo,
      OffsetDateTime createdAt,
      OffsetDateTime deletedAt) {}
}
