package com.factech.nexus.modules.system.memberships.domain.service;

import com.factech.nexus.modules.system.memberships.application.ListMembershipsRequest;
import com.factech.nexus.modules.system.memberships.application.MembershipChainResponse;
import com.factech.nexus.modules.system.memberships.domain.repository.MembershipQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadena completa de membresías (`RF-SP-017`).
 *
 * <p><b>No se pagina y no se ordena por otra cosa que el nivel.</b> El orden es la información: la
 * cadena se lee de la cima al extremo inferior y una lista alfabética de niveles no significaría
 * nada.
 *
 * <p><b>Este listado no bloquea nada.</b> El {@code FOR UPDATE} que toma el alta no estorba a los
 * lectores, de modo que consultar la cadena mientras alguien inserta un nivel devuelve el estado
 * anterior completo y coherente, nunca uno a medio reordenar.
 */
@Service
public class ListMembershipsService {

  private final MembershipQueryRepository cadena;

  public ListMembershipsService(MembershipQueryRepository cadena) {
    this.cadena = cadena;
  }

  @Transactional(readOnly = true)
  public MembershipChainResponse list(ListMembershipsRequest peticion) {
    return MembershipChainResponse.from(cadena.findChain(peticion.search()));
  }
}
