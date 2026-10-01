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

  /** Si la línea es un FTD ya contado en una liquidación afftrack (`RN-CM-040`, `RN-CM-047`). */
  boolean hasCountedFtd(UUID detailId);

  /**
   * Las comisiones <b>vivas</b> de la línea, <b>bloqueadas</b>: las comisiones antes que los lotes
   * (`RF-CM-022` `plan.md` §1).
   */
  List<LiveCommission> lockLiveCommissionsOf(UUID detailId);

  /** Las marca revertidas, por quién y cuándo (`RN-CM-047`). No se borran. */
  void revert(Collection<UUID> commissionIds, UUID actorId, OffsetDateTime at);

  /**
   * Borra el desenlace de la línea, para que la cadena nueva se devengue como una línea recién
   * atribuida (`RN-CM-047`): la fila dice qué le falta a la línea, y a esta le falta todo.
   */
  void deleteOutcome(UUID detailId);

  /** Una comisión viva de una línea, con lo que hace falta para revertirla. */
  record LiveCommission(UUID id, UUID batchId, UUID userId, BigDecimal amount) {}

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
