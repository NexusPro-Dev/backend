package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.service.AfftrackTierPicker.Tier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que la liquidación afftrack lee y escribe (`RF-CM-020`), siempre dentro de la transacción del
 * cierre.
 */
public interface AfftrackSettlementRepository {

  /**
   * Las líneas FTD que todavía no se contaron a quien las vendió (`RN-CM-036`, `RN-CM-040`): venta
   * {@code CONFIRMADA} de tipo {@code VENTA}, con vendedor, entregada antes de {@code corte} y de
   * un producto de {@code ftd}.
   *
   * <p><b>«No contada al vendedor» es «no contada a nadie de la cadena»</b>: los FTD de una línea
   * se escriben todos en la misma transacción.
   */
  List<FtdLine> newFtdLines(Collection<UUID> ftd, OffsetDateTime corte);

  /** Los remanentes vivos: la última liquidación de cada persona y producto, si le sobró algo. */
  Map<PersonProduct, Integer> carriedOut(Collection<UUID> ftd);

  /**
   * La escala de cada persona y producto el día del cierre (`RN-CM-039`): la suya si tiene algún
   * escalón vigente, y si no, la de su rol vendedor. <b>Dos sentencias para todas las personas</b>,
   * no dos por persona.
   */
  Map<PersonProduct, List<Tier>> scales(
      Collection<UUID> personas, Collection<UUID> productos, LocalDate dia);

  void insertSettlement(NewSettlement liquidacion);

  void insertFtds(UUID settlementId, List<CountedFtd> ftds, OffsetDateTime at);

  /** La comisión {@code POR_AFFTRACK} de una liquidación que alcanzó un escalón (`RN-CM-044`). */
  void insertCommission(NewAfftrackCommission comision);

  record PersonProduct(UUID userId, UUID productId) {}

  record FtdLine(UUID detailId, UUID productId, UUID sellerId, OffsetDateTime deliveredAt) {}

  record CountedFtd(UUID detailId, int chainLevel) {}

  record NewSettlement(
      UUID id,
      UUID closingId,
      UUID userId,
      UUID productId,
      int carriedIn,
      int newFtds,
      int paidFtds,
      int carriedOut,
      String source,
      UUID thresholdRateId,
      OffsetDateTime at) {}

  record NewAfftrackCommission(
      UUID id,
      UUID batchId,
      UUID settlementId,
      UUID userId,
      String source,
      UUID rateId,
      LocalDate resolvedOn,
      BigDecimal amountPerFtd,
      int quantity,
      BigDecimal amount,
      OffsetDateTime accruedAt) {}
}
