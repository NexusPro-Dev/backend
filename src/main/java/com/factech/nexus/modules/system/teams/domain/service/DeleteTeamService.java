package com.factech.nexus.modules.system.teams.domain.service;

import com.factech.nexus.modules.system.teams.application.DeleteTeamRequest;
import com.factech.nexus.modules.system.teams.domain.models.Team;
import com.factech.nexus.modules.system.teams.domain.repository.TeamQueryRepository;
import com.factech.nexus.modules.system.teams.domain.repository.TeamRepository;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.audit.DeletionReason;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-SP-068`: eliminar un equipo.
 *
 * <p>Es `RF-AC-005` con otra tabla y <b>una decisión al revés en lo esencial</b>: aquí la baja
 * <b>se rechaza si el equipo tiene miembros vigentes</b> (`RN-SP-054`). La diferencia no es de
 * gusto: una categoría es un filtro y retirarla no rompe nada —sus cursos se ofrecen igual sin
 * cajón—, mientras que un equipo es la única forma de decir en qué parte de la red está un manager,
 * y eliminarlo con gente dentro dejaría a esas personas sin pertenencia sin que nadie lo hubiera
 * decidido, moviendo de paso la atribución de todo lo que cuelga de ellas. Es la postura de
 * `RN-SEG-008` con un rol que tiene usuarios: <b>lo que otros sostienen no se retira solo</b>.
 *
 * <p><b>El orden importa y es el de `RF-AC-005`.</b> El motivo se valida <b>antes de consultar
 * nada</b> —un motivo ausente no debe costar ni una sentencia—, el equipo se resuelve <b>en
 * cualquier estado</b> y con bloqueo para distinguir «no existe» de «ya estaba eliminado», y la
 * comprobación de `RN-SP-054` va <b>dentro del bloqueo</b>: una asignación simultánea entre el
 * {@code SELECT} y el {@code UPDATE} dejaría un equipo eliminado con alguien dentro, que es
 * exactamente el estado que la regla existe para impedir.
 *
 * <p><b>El historial sobrevive.</b> Las pertenencias cerradas no se borran: son un hecho —esta
 * persona estuvo aquí entre estas dos fechas— y son lo que las comisiones leerán para repartir lo
 * que esa red produjo. Por eso la instantánea lleva <b>los identificadores de todas las personas
 * que pasaron por el equipo</b> y no solo de las vigentes, que además son cero por definición: un
 * registro de baja que dice «tuve gente» sin decir quién no sirve para reconstruir nada.
 *
 * <p><b>Y el nombre queda libre</b> (`RN-SP-050`): {@code uq_teams_name} es parcial y un eliminado
 * no compite. Volver a crear «Equipo Norte» al día siguiente es legítimo y <b>no revive nada</b>.
 */
@Service
public class DeleteTeamService {

  static final String CON_MIEMBROS =
      "El equipo tiene miembros y no puede eliminarse. Retírelos o reasígnelos antes.";

  private final TeamRepository equipos;
  private final TeamQueryRepository consultas;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public DeleteTeamService(
      TeamRepository equipos, TeamQueryRepository consultas, AuditWriter auditoria) {
    this(equipos, consultas, auditoria, Clock.systemUTC());
  }

  DeleteTeamService(
      TeamRepository equipos, TeamQueryRepository consultas, AuditWriter auditoria, Clock reloj) {
    this.equipos = equipos;
    this.consultas = consultas;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public void delete(UUID id, DeleteTeamRequest peticion) {
    // Antes de cualquier consulta (`CA-SP-773`): quien no manda motivo no paga
    // una sentencia por enterarse.
    DeletionReason motivo = new DeletionReason(peticion == null ? null : peticion.reason());

    Team equipo =
        equipos
            .findByIdForUpdate(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un equipo con ese identificador."));

    // `EX-002`, y aquí SÍ se distingue del inexistente —al contrario que en
    // `RF-SP-066` y `RF-SP-067`—: la distinción decide algo, porque quien
    // elimina dos veces merece saber que la primera funcionó, y no hay nada que
    // ocultar (el detalle devuelve los eliminados a quien tiene `teams:read`).
    if (equipo.estaEliminado()) {
      String mensaje = "El equipo ya está eliminado.";
      throw new BusinessRuleException(
          "EX-002", mensaje, List.of(new FieldError("id", "EX-002", mensaje)));
    }

    // `EX-003`, dentro del bloqueo. El mensaje dice QUÉ HACER porque la salida no
    // es obvia: hay dos, retirar (`RF-SP-070`) o reubicar en otro equipo
    // (`RF-SP-069`).
    if (consultas.countActiveMembers(equipo.getId()) > 0) {
      throw new BusinessRuleException(
          "EX-003", CON_MIEMBROS, List.of(new FieldError("id", "EX-003", CON_MIEMBROS)));
    }

    // La foto es de ANTES: se toma con `deleted_at` todavía nulo y la
    // instantánea no lo lleva, que es el detalle que `CA-AC-031` dejó fijado y
    // que `CA-SP-775` repite.
    Map<String, Object> instantanea = new LinkedHashMap<>(equipo.instantanea());
    instantanea.put(
        "member_ids",
        consultas.findAllMemberIdsEver(equipo.getId()).stream().map(UUID::toString).toList());

    equipo.delete(OffsetDateTime.now(reloj));

    auditoria.recordDeletion(
        new DeletionEvent(
            TeamDetailReader.MODULO,
            TeamDetailReader.ENTIDAD,
            equipo.getId(),
            DeletionType.LOGICAL,
            motivo.value(),
            instantanea));
  }
}
