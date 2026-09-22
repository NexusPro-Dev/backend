package com.factech.nexus.modules.system.teams.domain.service;

import com.factech.nexus.modules.system.teams.application.TeamDetailResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-SP-065`: la ficha del equipo con sus miembros vigentes.
 *
 * <p>Es {@link TeamDetailReader} bajo una transacción de solo lectura, y nada más. La transacción
 * no está de adorno: las dos o tres lecturas —ficha, miembros y, si está eliminado, el motivo—
 * tienen que ver <b>la misma instantánea</b>, o el recuento de la primera podría no cuadrar con la
 * lista de la segunda (`CA-SP-751`).
 *
 * <p><b>Un equipo eliminado NO es un {@code 404}</b>: se devuelve con su fecha, su motivo y la
 * lista vacía, como la categoría retirada de `RF-AC-003` y el paquete de `RF-PM-019`. El listado ya
 * lo enseña con {@code includeDeleted}, y abrirlo desde ahí para recibir un {@code 404} sería
 * incoherente.
 *
 * <p><b>No pregunta quién es el actor.</b> Quien porta {@code teams:read} abre cualquier equipo; no
 * hay «mi equipo» por esta vía, y el permiso es distinto del de listar (`RN-SEG-014`).
 */
@Service
public class GetTeamService {

  private final TeamDetailReader detalle;

  public GetTeamService(TeamDetailReader detalle) {
    this.detalle = detalle;
  }

  @Transactional(readOnly = true)
  public TeamDetailResponse detail(UUID id) {
    return detalle.leer(id);
  }
}
