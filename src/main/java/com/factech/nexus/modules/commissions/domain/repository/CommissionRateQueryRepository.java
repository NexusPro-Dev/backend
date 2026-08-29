package com.factech.nexus.modules.commissions.domain.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de consulta de las tarifas de comisión (`RF-CM-002` y `RF-CM-005`). */
public interface CommissionRateQueryRepository {

  /** Una página del listado, con el rol, el producto y la persona ya resueltos. */
  List<RateRow> search(RateFilters filtros, int offset, int limit);

  /** Una fila concreta, retirada o no, con lo de otros módulos ya resuelto. */
  Optional<RateRow> findRow(UUID id);

  /** Cuántas cumplen el filtro. */
  long count(RateFilters filtros);

  /**
   * La tarifa que se aplica a esa persona, ese producto y esa fecha (`RN-CM-004`).
   *
   * <p><b>La precedencia se resuelve en la sentencia</b>, no leyendo cuatro veces y eligiendo en
   * Java: con cuatro lecturas el orden viviría en el flujo de control y una refactorización podría
   * alterarlo sin que nada falle — devolvería un porcentaje <b>plausible</b>.
   */
  Optional<RateRow> resolve(UUID roleId, UUID productId, UUID userId, LocalDate fecha);

  /** Los filtros del listado. Un valor nulo significa «sin filtro». */
  record RateFilters(
      UUID roleId, UUID productId, UUID userId, LocalDate onDate, boolean includeDeleted) {}

  /** Una fila leída, con lo de otros módulos resuelto en la misma sentencia. */
  record RateRow(
      UUID id,
      UUID roleId,
      String roleCode,
      String roleName,
      UUID productId,
      String productCode,
      String productName,
      UUID userId,
      String username,
      String userFullName,
      BigDecimal percentage,
      LocalDate validFrom,
      LocalDate validTo,
      OffsetDateTime deletedAt) {}
}
