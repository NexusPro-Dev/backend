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

  /** Los filtros del listado. Un valor nulo significa «sin filtro». */
  /**
   * Los filtros del catálogo.
   *
   * <p><b>{@code rateType} entra por decisión del responsable del proyecto</b> (02-09-2026) y no
   * por necesidad técnica: ninguna operación lo requiere. Responde a la pregunta que nace el día
   * que conviven las dos formas — «enséñame las que pagan importe fijo»—. Nulo: no filtra.
   *
   * <p><b>No existe el equivalente en las tasas personalizadas</b>, y no es un olvido: allí se
   * filtra por persona, y una persona tiene <b>una</b> tasa vigente (`RN-CM-006`).
   */
  record RateFilters(UUID roleId, CommissionRateType rateType, boolean includeDeleted) {}

  /**
   * Una fila leída, con el rol resuelto en la misma sentencia.
   *
   * <p><b>{@code associatedProducts} no es un adorno.</b> Es lo que hace visible `RN-CM-012`: una
   * tasa con cero asociaciones <b>no paga nada a nadie</b>, y sin este dato quien mire el catálogo
   * vería una tasa con su porcentaje y concluiría que está configurada. El cambio de significado
   * respecto al modelo anterior se descubriría liquidando.
   */
  record RateRow(
      UUID id,
      UUID roleId,
      String roleCode,
      String roleName,
      CommissionRateType rateType,
      BigDecimal percentage,
      BigDecimal fixedAmount,
      long associatedProducts,
      OffsetDateTime deletedAt) {}
}
