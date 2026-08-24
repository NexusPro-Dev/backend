package com.factech.nexus.modules.system.memberships.domain.service;

import com.factech.nexus.modules.system.memberships.application.MembershipDetailResponse;
import com.factech.nexus.modules.system.memberships.domain.repository.MembershipQueryRepository;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detalle de una membresía con sus dos vecinos (`RF-SP-018`).
 *
 * <p>{@code 404} y no {@code 422}, a diferencia de `EX-002` en el alta: aquí lo que no existe es el
 * recurso <b>de la ruta</b>, que es exactamente el caso que `development-guide.md` §7.1 reserva
 * para el {@code 404}.
 */
@Service
public class GetMembershipService {

  private final MembershipQueryRepository cadena;

  public GetMembershipService(MembershipQueryRepository cadena) {
    this.cadena = cadena;
  }

  @Transactional(readOnly = true)
  public MembershipDetailResponse byId(UUID id) {
    return cadena
        .findDetail(id)
        .map(MembershipDetailResponse::from)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "EX-001", "No existe una membresía con ese identificador."));
  }
}
