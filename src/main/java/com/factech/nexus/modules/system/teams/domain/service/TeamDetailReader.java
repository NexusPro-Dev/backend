package com.factech.nexus.modules.system.teams.domain.service;

import com.factech.nexus.modules.system.teams.application.TeamDetailResponse;
import com.factech.nexus.modules.system.teams.domain.repository.TeamQueryRepository;
import com.factech.nexus.modules.system.teams.domain.repository.TeamQueryRepository.TeamMemberRow;
import com.factech.nexus.modules.system.teams.domain.repository.TeamQueryRepository.TeamRow;
import com.factech.nexus.shared.audit.DeletionReasonReader;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Arma la respuesta del equipo: ficha → miembros vigentes → motivo de eliminación (`RF-SP-065` §8).
 *
 * <p><b>Un solo sitio</b>, porque la devuelven seis operaciones —el alta, el detalle, la
 * corrección, el cambio de estado y las dos de miembros— y cada una que la armara por su cuenta
 * sería una copia que podría quedarse atrás.
 *
 * <p><b>Dos sentencias</b> para un equipo vivo —ficha y miembros, y la segunda se cortocircuita
 * cuando el recuento de la primera es cero— y <b>tres</b> cuando está eliminado y hay que leer el
 * motivo (`CA-SP-754`). Ninguna crece con el número de miembros.
 */
@Component
public class TeamDetailReader {

  public static final String MODULO = "SP";
  public static final String ENTIDAD = "teams";

  private final TeamQueryRepository consultas;
  private final DeletionReasonReader motivos;

  public TeamDetailReader(TeamQueryRepository consultas, DeletionReasonReader motivos) {
    this.consultas = consultas;
    this.motivos = motivos;
  }

  /** El detalle, o el `404` de `RF-SP-065` `EX-001`. Un equipo eliminado <b>no</b> es un `404`. */
  public TeamDetailResponse leer(UUID id) {
    TeamRow fila =
        consultas
            .findDetail(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un equipo con ese identificador."));
    List<TeamMemberRow> miembros =
        fila.memberCount() == 0 ? List.of() : consultas.findActiveMembers(fila.id());
    // El nulo aquí significa que el equipo está vivo, y la respuesta lo omite
    // del JSON en lugar de enviarlo en nulo. Si está eliminado y el registro de
    // baja no aparece, sale nulo y presente: la lectura no se cae por un hueco
    // en la auditoría (`RF-SP-065` `FA-004`).
    String motivo =
        fila.eliminado() ? motivos.reasonFor(MODULO, ENTIDAD, fila.id()).orElse(null) : null;
    return TeamDetailResponse.from(fila, miembros, motivo);
  }
}
