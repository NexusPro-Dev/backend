package com.factech.nexus.modules.commissions.domain.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Lectura de las liquidaciones afftrack (`RF-CM-021`). */
public interface AfftrackSettlementQueryRepository {

  List<SettlementRow> search(SettlementFilter filtro, int offset, int limit);

  long count(SettlementFilter filtro);

  /** {@code paid} nulo no filtra; {@code from}/{@code to} van sobre el instante del cierre. */
  record SettlementFilter(
      UUID userId,
      UUID productId,
      UUID closingId,
      OffsetDateTime from,
      OffsetDateTime to,
      Boolean paid) {}

  /**
   * El escalón se lee de la comisión que lo pagó y no de la tabla de escalones: dice lo que se
   * pagó, aunque después se corrigiera o retirara (`spec.md` §13).
   */
  record SettlementRow(
      UUID id,
      UUID closingId,
      OffsetDateTime closedAt,
      UUID userId,
      String username,
      String fullName,
      UUID productId,
      String productCode,
      String productName,
      UUID currencyId,
      String currencyCode,
      int currencyDecimalPlaces,
      int carriedIn,
      int newFtds,
      int ownFtds,
      int networkFtds,
      int paidFtds,
      int carriedOut,
      String source,
      BigDecimal amountPerFtd,
      BigDecimal amount) {}
}
