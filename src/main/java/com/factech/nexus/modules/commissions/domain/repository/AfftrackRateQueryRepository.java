package com.factech.nexus.modules.commissions.domain.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lectura de los escalones afftrack de rol, con el producto, su moneda y el rol resueltos en la
 * misma sentencia (`RF-CM-016`), como {@link CommissionRateQueryRepository}.
 */
public interface AfftrackRateQueryRepository {

  List<AfftrackRateRow> search(AfftrackRateFilters filtros, int offset, int limit);

  long count(AfftrackRateFilters filtros);

  Optional<AfftrackRateRow> findRow(UUID id);

  record AfftrackRateFilters(UUID productId, UUID roleId, boolean includeDeleted) {}

  /** {@code amountAtThreshold} sale de la sentencia: la misma cuenta que se pagará. */
  record AfftrackRateRow(
      UUID id,
      UUID productId,
      String productCode,
      String productName,
      UUID currencyId,
      String currencyCode,
      int currencyDecimalPlaces,
      UUID roleId,
      String roleCode,
      String roleName,
      int threshold,
      BigDecimal amountPerFtd,
      BigDecimal amountAtThreshold,
      OffsetDateTime createdAt,
      OffsetDateTime deletedAt) {}
}
