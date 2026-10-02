package com.factech.nexus.modules.movements.domain.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto del catálogo de entidades de cobro (`RN-MV-054`; `RF-MV-032` a `RF-MV-034`). */
public interface PayoutInstitutionRepository {

  /**
   * Escribe la entidad, activa. <b>Devuelve {@code false} si el código ya existía</b>: la unicidad
   * la decide {@code uq_payout_institutions_code} con {@code ON CONFLICT DO NOTHING}, sin abortar
   * la transacción y sin consulta previa que dos peticiones simultáneas pudieran burlar.
   */
  boolean insert(UUID id, String code, String name, String kind, UUID countryId, OffsetDateTime at);

  Optional<InstitutionRow> find(UUID id);

  /** La entidad con su fila bloqueada {@code FOR UPDATE}, para editarla (`RF-MV-034`). */
  Optional<InstitutionRow> lock(UUID id);

  /**
   * El catálogo, ordenado por nombre de país y de entidad (`RF-MV-033`). Cada filtro nulo no
   * filtra.
   */
  List<InstitutionRow> list(UUID countryId, String kind, Boolean active);

  void update(UUID id, String name, boolean active, OffsetDateTime at);

  record InstitutionRow(
      UUID id,
      String code,
      String name,
      String kind,
      UUID countryId,
      String countryCode,
      String countryName,
      boolean active,
      OffsetDateTime createdAt) {}
}
