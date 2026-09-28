package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.AccrualOutcome;
import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import com.factech.nexus.modules.commissions.domain.models.RateSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Puerto del devengo: el desenlace de cada línea y sus comisiones (`RF-CM-013`). */
public interface CommissionAccrualRepository {

  /**
   * Bloqueo consultivo de transacción sobre la línea. Dos devengos de la misma línea —el aviso y el
   * barrido— se esperan aquí, y el segundo encuentra el desenlace del primero.
   */
  void lockLine(UUID detailId);

  Optional<AccrualRow> find(UUID detailId);

  /** De esas líneas, las que ya tienen desenlace, sea cual sea. */
  Set<UUID> withOutcome(Collection<UUID> detailIds);

  /** Las líneas rechazadas, para reintentarlas (`RN-CM-034`). */
  List<UUID> rejected();

  void insertOutcome(UUID detailId, AccrualOutcome outcome, String reason, OffsetDateTime at);

  /** El reintento de una rechazada: nuevo desenlace, un intento más. */
  void updateOutcome(UUID detailId, AccrualOutcome outcome, String reason, OffsetDateTime at);

  void insertCommission(NewCommission comision);

  record AccrualRow(UUID detailId, AccrualOutcome outcome, int attempts) {}

  /** Una fila de {@code commissions}, con lo que se aplicó copiado (`RN-CM-008`). */
  record NewCommission(
      UUID batchId,
      UUID detailId,
      UUID userId,
      int chainLevel,
      RateSource source,
      UUID rateId,
      LocalDate resolvedOn,
      CommissionRateType rateType,
      BigDecimal value,
      BigDecimal unitPrice,
      int quantity,
      BigDecimal amount,
      OffsetDateTime accruedAt) {}
}
