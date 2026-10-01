package com.factech.nexus.modules.system.countries.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Lo que `SP` publica de un <b>país</b> para otros módulos (**D-25**; `RF-MV-032` · `plan.md` §3).
 *
 * <p>Es la primera lectura de países fuera de `SP`: nace con el catálogo de entidades de cobro de
 * `MV`, que tiene que saber si el país de una entidad existe y si está activo. Ver
 * `architecture.md` §15.2.
 */
public interface CountryCatalog {

  /**
   * El país, si existe, activo o no: el estado viaja en la respuesta y lo interpreta quien
   * pregunta.
   *
   * @param id identificador del país; un valor nulo devuelve vacío en lugar de fallar
   */
  Optional<CountryView> find(UUID id);

  record CountryView(UUID id, String code, String name, boolean active) {}
}
