package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de consulta del catálogo de tasas por rol (`RF-CM-002`). */
public interface CommissionRateQueryRepository {

  /** Una página del listado, con el rol ya resuelto. */
  List<RateRow> search(RateFilters filtros, int offset, int limit);

  /** Una fila concreta, retirada o no. */
  Optional<RateRow> findRow(UUID id);

  /** Cuántas cumplen el filtro. */
  long count(RateFilters filtros);

  /**
   * Los filtros del catálogo. Un valor nulo significa «sin filtro».
   *
   * <p><b>{@code productId} entra el 15-09-2026</b> con `RN-CM-021`: la tasa nace con su producto,
   * y «qué paga este producto» es una pregunta que el catálogo ahora puede responder solo.
   *
   * <p><b>{@code rateType} entra por decisión del responsable del proyecto</b> (02-09-2026) y no
   * por necesidad técnica: ninguna operación lo requiere. Responde a la pregunta que nace el día
   * que conviven las dos formas — «enséñame las que pagan importe fijo»—.
   *
   * <p><b>No existe el equivalente en las tasas personalizadas</b>, y no es un olvido: allí se
   * filtra por persona, y una persona tiene <b>una</b> tasa vigente (`RN-CM-006`).
   */
  record RateFilters(
      UUID productId, UUID roleId, CommissionRateType rateType, boolean includeDeleted) {}

  /**
   * Una fila leída, con el producto —y su moneda— y el rol resueltos en la misma sentencia.
   *
   * <p>Hasta el 15-09-2026 llevaba <b>cuántos</b> productos asociados tenía la tasa, porque una con
   * cero no pagaba nada a nadie (`RN-CM-012`). Desde `RN-CM-021` lleva <b>cuál</b>: toda tasa viva
   * rige sobre su producto, y el número dejó de decir nada.
   *
   * <p><b>El precio y la moneda viajan con el producto</b> por decisión del responsable del
   * proyecto (15-09-2026): un importe fijo es dinero en la moneda del producto, y un porcentaje es
   * una parte de su precio. Sin los dos, la cifra de la fila no dice cuánto es.
   */
  record RateRow(
      UUID id,
      UUID productId,
      String productCode,
      String productName,
      BigDecimal productPrice,
      UUID currencyId,
      String currencyCode,
      int currencyDecimalPlaces,
      UUID roleId,
      String roleCode,
      String roleName,
      CommissionRateType rateType,
      BigDecimal percentage,
      BigDecimal fixedAmount,
      OffsetDateTime deletedAt) {}
}
