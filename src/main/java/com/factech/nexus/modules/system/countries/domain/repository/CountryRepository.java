package com.factech.nexus.modules.system.countries.domain.repository;

import com.factech.nexus.modules.system.countries.domain.models.Country;
import com.factech.nexus.modules.system.countries.domain.models.CountryCode;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de escritura del catálogo de países (`RF-SP-020`, `RF-SP-022`).
 *
 * <p>Sin {@code update} ni {@code delete} generales: `RN-SP-009` los prohíbe, y no tenerlos es la
 * forma más barata de que nadie los llame por descuido. Lo único que se modifica es el estado, y
 * eso ocurre sobre la entidad gestionada que devuelve {@link #findByIdForUpdate(UUID)}.
 */
public interface CountryRepository {

  /**
   * Persiste un país nuevo.
   *
   * @throws com.factech.nexus.shared.error.BusinessRuleException si viola {@code uq_countries_code}
   *     o {@code uq_countries_name}, distinguiendo cuál de los dos
   */
  Country save(Country pais);

  /** ¿Hay ya un país con ese código? (`EX-001`) */
  boolean existsCode(CountryCode code);

  /**
   * ¿Hay ya un país con ese nombre, <b>sin distinguir mayúsculas ni acentos</b>?
   *
   * <p>Compara sobre la misma forma normalizada que {@code uq_countries_name}. Si comparara el
   * texto literal mientras el índice compara la forma normalizada, el `409` legible se convertiría
   * en un fallo de integridad justo para el caso que más importa.
   */
  boolean existsName(String name);

  /**
   * Carga el país <b>bloqueando su fila</b> hasta el final de la transacción.
   *
   * <p>Sin el bloqueo, dos peticiones simultáneas leerían el mismo estado inicial y ambas creerían
   * haberlo cambiado, emitiendo dos eventos para un solo cambio real.
   */
  Optional<Country> findByIdForUpdate(UUID id);
}
