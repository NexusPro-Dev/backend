package com.factech.nexus.modules.system.brokers.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Las cuentas de broker agregadas, publicadas <b>por `SP` para `IN`</b> (`RF-IN-009`, `RN-IN-015`,
 * 10-10-2026).
 *
 * <p><b>Solo cuentas {@code CONSUMIDOR}</b> cuyo titular, si lo tienen, no está eliminado, y
 * <b>atribuidas por su origen</b>: al dueño de la cuenta {@code VENDEDOR} que las originó (el
 * {@code afftrack}), si es fuerza comercial; si no, a nadie —lo no atribuido—.
 *
 * <p><b>Los tipos son de `SP`</b>: el intervalo en {@code OffsetDateTime} y el tramo en texto. `SP`
 * es la raíz del grafo de módulos y no puede usar los de `MV`.
 */
public interface BrokerAccountFigures {

  /** El catálogo entero de brokers, por nombre: el desglose los trae todos. */
  List<Broker> brokers();

  /** La fuerza comercial —quien porta un rol {@code VENDEDOR}, no eliminado— con su superior. */
  List<Seller> commercialForce();

  /**
   * Las cifras por vendedor de origen y broker, <b>cada una con su fecha</b> (`RN-IN-015`).
   *
   * @param from inicio incluido, o nulo: sin límite
   * @param to fin excluido, o nulo: sin límite
   * @param allTime sin fechas pedidas: los FTD son todas las cuentas en {@code FIRST_DEPOSIT} y las
   *     que operaron, todas las que tienen alguna operación
   */
  List<SellerBrokerFigures> bySellerAndBroker(
      OffsetDateTime from, OffsetDateTime to, boolean allTime);

  /** Los consumidores distintos de las cuentas creadas en el intervalo, por vendedor de origen. */
  List<SellerConsumers> consumersBySeller(OffsetDateTime from, OffsetDateTime to);

  /**
   * Las cuentas creadas y los FTD por tramo, de las originadas por {@code sellers} —o por cualquier
   * vendedor de la fuerza comercial si es nulo—.
   *
   * @param unit {@code day}, {@code week} o {@code month}, como {@code date_trunc}
   */
  List<BucketFigures> byBucket(
      Set<UUID> sellers, OffsetDateTime from, OffsetDateTime to, String unit, ZoneId zone);

  /** Un vendedor de la fuerza comercial y su superior vigente, que puede no tenerlo. */
  record Seller(
      UUID id,
      String username,
      String firstName,
      String lastName,
      String roleCode,
      UUID supervisorId) {}

  /**
   * Las cifras de un vendedor de origen en un broker. {@code sellerId} nulo es lo no atribuido.
   *
   * <p>{@code converted} son las creadas en el intervalo que ya tienen primer depósito: el
   * numerador de la conversión, que <b>no</b> es {@code ftd} —esos son los depósitos llegados en el
   * intervalo—.
   */
  record SellerBrokerFigures(
      UUID sellerId,
      UUID brokerId,
      long accounts,
      long converted,
      long withoutHolder,
      long ftd,
      long activeAccounts,
      long operations) {}

  /** {@code sellerId} nulo es lo no atribuido. */
  record SellerConsumers(UUID sellerId, long consumers) {}

  record Broker(UUID id, String name) {}

  record BucketFigures(LocalDate start, long accounts, long ftd) {}
}
