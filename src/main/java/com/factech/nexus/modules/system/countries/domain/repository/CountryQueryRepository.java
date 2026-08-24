package com.factech.nexus.modules.system.countries.domain.repository;

import com.factech.nexus.modules.system.countries.application.CountryItem;
import java.util.List;

/** Puerto de consulta del catálogo de países (`RF-SP-021`). */
public interface CountryQueryRepository {

  /**
   * Catálogo ordenado alfabéticamente por nombre, según la intercalación del español.
   *
   * @param search término sobre código y nombre; nulo o en blanco equivale a ausente
   * @param includeInactive si {@code true}, añade los desactivados a los activos
   */
  List<CountryItem> findAll(String search, boolean includeInactive);
}
