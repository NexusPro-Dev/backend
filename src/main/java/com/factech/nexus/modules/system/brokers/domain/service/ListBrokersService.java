package com.factech.nexus.modules.system.brokers.domain.service;

import com.factech.nexus.modules.system.brokers.application.BrokerCatalogResponse;
import com.factech.nexus.modules.system.brokers.application.ListBrokersRequest;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El catálogo de brokers (`RF-SP-052`).
 *
 * <p><b>Una consulta y ninguna regla.</b> No hay nada que decidir aquí: la única regla del
 * requerimiento —que el catálogo no se administra por API (`RN-SP-039`)— se cumple por lo que <b>no
 * existe</b>, y no por lo que este servicio comprueba.
 */
@Service
public class ListBrokersService {

  private final BrokerQueryRepository catalogo;

  public ListBrokersService(BrokerQueryRepository catalogo) {
    this.catalogo = catalogo;
  }

  @Transactional(readOnly = true)
  public BrokerCatalogResponse list(ListBrokersRequest filtros) {
    ListBrokersRequest efectivos = filtros == null ? new ListBrokersRequest(null) : filtros;
    return BrokerCatalogResponse.de(catalogo.findAll(efectivos.incluirInactivos()));
  }
}
