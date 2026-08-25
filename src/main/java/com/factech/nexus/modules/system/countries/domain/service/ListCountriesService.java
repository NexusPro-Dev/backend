package com.factech.nexus.modules.system.countries.domain.service;

import com.factech.nexus.modules.system.countries.application.CountryCatalogResponse;
import com.factech.nexus.modules.system.countries.application.ListCountriesRequest;
import com.factech.nexus.modules.system.countries.domain.repository.CountryQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catálogo de países (`RF-SP-021`). */
@Service
public class ListCountriesService {

  private final CountryQueryRepository paises;

  public ListCountriesService(CountryQueryRepository paises) {
    this.paises = paises;
  }

  @Transactional(readOnly = true)
  public CountryCatalogResponse list(ListCountriesRequest peticion) {
    return CountryCatalogResponse.from(
        paises.findAll(peticion.search(), peticion.incluirInactivos()));
  }
}
