package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.CommissionRateType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de consulta de las tasas personalizadas (`RF-CM-002`).
 *
 * <p><b>Es un listado aparte y no un filtro del otro</b>, porque las dos piezas no se parecen: esta
 * tiene vigencia y persona, aquella tiene rol y asociaciones. Fundirlas obligaría a una respuesta
 * con la mitad de los campos nulos en cada fila, y a que el cliente dedujera de qué tipo es cada
 * una.
 */
public interface UserCommissionRateQueryRepository {

  /** Una página del listado, con la persona ya resuelta. */
  List<UserRateRow> search(UserRateFilters filtros, int offset, int limit);

  /** Una fila concreta, retirada o no. */
  Optional<UserRateRow> findRow(UUID id);

  /** Cuántas cumplen el filtro. */
  long count(UserRateFilters filtros);

  /**
   * Los filtros. Un valor nulo significa «sin filtro».
   *
   * <p><b>No hay interruptor «solo vigentes»</b>: eso es {@code onDate} con la fecha de hoy. Un
   * interruptor y una fecha podrían contradecirse, y esa contradicción no la detecta nada.
   */
  record UserRateFilters(UUID userId, LocalDate onDate, boolean includeDeleted) {}

  /** Una fila leída, con la persona resuelta en la misma sentencia. */
  record UserRateRow(
      UUID id,
      UUID userId,
      String username,
      String userFullName,
      CommissionRateType rateType,
      BigDecimal percentage,
      BigDecimal fixedAmount,
      LocalDate validFrom,
      LocalDate validTo,
      OffsetDateTime deletedAt) {}
}
