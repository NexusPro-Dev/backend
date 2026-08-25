package com.factech.nexus.modules.system.countries.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * El catálogo completo de países (`RF-SP-021`).
 *
 * <p>Envuelto en {@code content} y sin metadatos de paginación, por lo dicho en `RF-SP-010` §4.
 * `CA-SP-140` exige que no haya paginación, y rellenar {@code totalPages: 1} diría lo contrario.
 *
 * <p>Una búsqueda sin coincidencias devuelve la colección <b>vacía</b>, no un error (`CA-SP-142`).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CountryCatalogResponse(List<CountryResponse> content) {

  public static CountryCatalogResponse from(List<CountryItem> paises) {
    return new CountryCatalogResponse(paises.stream().map(CountryResponse::from).toList());
  }
}
