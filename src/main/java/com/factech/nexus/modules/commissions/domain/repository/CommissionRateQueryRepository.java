package com.factech.nexus.modules.commissions.domain.repository;

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
  record RateFilters(UUID roleId, boolean includeDeleted) {}

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
      BigDecimal percentage,
      long associatedProducts,
      OffsetDateTime deletedAt) {}
}
