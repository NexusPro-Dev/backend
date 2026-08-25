package com.factech.nexus.modules.system.currencies.domain.service;

import com.factech.nexus.modules.system.currencies.application.CurrencyCatalogResponse;
import com.factech.nexus.modules.system.currencies.application.ListCurrenciesRequest;
import com.factech.nexus.modules.system.currencies.domain.repository.CurrencyQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de monedas (`RF-SP-019`).
 *
 * <p>Sin participación del dominio: `RN-SP-010` —el catálogo es inmutable por API— <b>se cumple por
 * ausencia de endpoint de escritura</b>, no por código que rechace. Un {@code POST} sobre este
 * recurso obtiene {@code 405} de Spring sin que nadie lo haya escrito.
 */
@Service
public class ListCurrenciesService {

  private final CurrencyQueryRepository monedas;

  public ListCurrenciesService(CurrencyQueryRepository monedas) {
    this.monedas = monedas;
  }

  @Transactional(readOnly = true)
  public CurrencyCatalogResponse list(ListCurrenciesRequest peticion) {
    return CurrencyCatalogResponse.from(monedas.findAll(peticion.incluirInactivas()));
  }
}
