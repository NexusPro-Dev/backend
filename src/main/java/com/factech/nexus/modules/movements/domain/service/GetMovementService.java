package com.factech.nexus.modules.movements.domain.service;

import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.repository.MovementRepository;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El detalle de cualquier movimiento, con su comprobante (`RF-MV-007`).
 *
 * <p><b>Sin alcance, y por eso aparte de {@link GetMyMovementService}</b>: aquel lleva el actor
 * dentro de la consulta para que no haya ninguna comprobación de pertenencia que alguien pueda
 * mover de sitio, y un método sin actor a su lado sería justo eso (`plan.md` §3). Aquí el permiso
 * de la ruta es toda la autorización: quien lee el libro entero abre cualquier fila.
 *
 * <p><b>Lee con {@link MovementRepository#findById} y arma con {@link SaleDetailMapper}</b>, las
 * mismas dos piezas que el detalle propio, de modo que las dos rutas devuelven lo mismo sobre el
 * mismo movimiento (`CA-MV-290`) por construcción y no por disciplina.
 */
@Service
public class GetMovementService {

  private final MovementRepository movimientos;
  private final WithdrawalDestinations destinos;

  public GetMovementService(MovementRepository movimientos, WithdrawalDestinations destinos) {
    this.movimientos = movimientos;
    this.destinos = destinos;
  }

  @Transactional(readOnly = true)
  public SaleResponse get(UUID movementId) {
    return movimientos
        .findById(movementId)
        .map(detalle -> SaleDetailMapper.de(detalle, destinos.deRetiro(detalle)))
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "EX-001", "No existe un movimiento con ese identificador."));
  }
}
