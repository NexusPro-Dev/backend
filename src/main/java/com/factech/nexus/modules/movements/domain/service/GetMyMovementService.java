package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository.MovementDetailView;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El detalle de un movimiento propio (`RF-MV-008`).
 *
 * <p><b>Un movimiento ajeno responde lo mismo que uno inexistente</b> (`EX-002`), y no es un
 * descuido: decir «existe pero no es tuyo» <b>confirma que existe</b>, y con un identificador que
 * alguien esté probando eso ya es información. Por eso no es un {@code 403} — un {@code 403} lo
 * confirmaría. Es el mismo criterio del rechazo genérico de `RF-SP-034`.
 *
 * <p>La consulta lleva el alcance dentro, de modo que aquí no hay ninguna comprobación de
 * pertenencia que alguien pueda mover de sitio: lo ajeno <b>no se lee</b>.
 *
 * <p><b>Devuelve la misma forma que registrar una venta.</b> Quien la registró y quien la consulta
 * después tienen que ver lo mismo (`spec.md` §6.3), y por eso reutiliza {@code SaleResponse} en
 * lugar de estrenar una respuesta propia — armada en {@link SaleDetailMapper}, que desde el
 * 17-09-2026 comparte con la confirmación (`RF-MV-003`).
 */
@Service
public class GetMyMovementService {

  private final MovementRepository movimientos;
  private final AuthenticatedActor actor;

  public GetMyMovementService(MovementRepository movimientos, AuthenticatedActor actor) {
    this.movimientos = movimientos;
    this.actor = actor;
  }

  @Transactional(readOnly = true)
  public SaleResponse get(UUID movementId) {
    MovementDetailView detalle =
        movimientos
            .findMineById(movementId, actor.id())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "VAL-002", "No existe un movimiento suyo con ese identificador."));
    return SaleDetailMapper.de(detalle);
  }
}
