package com.factech.nexus.modules.system.teams.domain.service;

import com.factech.nexus.modules.system.teams.application.RemoveTeamMembersRequest;
import com.factech.nexus.modules.system.teams.application.TeamDetailResponse;
import com.factech.nexus.modules.system.teams.domain.models.Team;
import com.factech.nexus.modules.system.teams.domain.models.TeamMember;
import com.factech.nexus.modules.system.teams.domain.repository.TeamMemberRepository;
import com.factech.nexus.modules.system.teams.domain.repository.TeamRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-SP-070`: retirar miembros de un equipo.
 *
 * <p><b>Existe porque mover no es lo mismo que sacar.</b> Asignar a otro equipo cierra la
 * pertenencia anterior (`RN-SP-052`), pero eso solo sirve cuando hay destino; aquí no lo hay, y
 * hacen falta dos cosas que la asignación no puede dar: <b>vaciar un equipo</b> —lo que `RN-SP-054`
 * exige antes de eliminarlo— y dejar a alguien <b>sin equipo</b>, que es un estado legítimo.
 *
 * <p><b>Se puede retirar de un equipo `INACTIVO`, y es deliberado.</b> `RN-SP-053` prohíbe que un
 * equipo suspendido <b>reciba</b>, no que suelte: si también prohibiera soltar, un equipo
 * suspendido con gente dentro no podría vaciarse nunca y por tanto no podría eliminarse — la regla
 * se habría cerrado sobre sí misma.
 *
 * <p><b>Aquí «no pertenece» SÍ es un error</b>, al contrario que en la asignación (`RF-SP-069`
 * `FA-001`). Asignar a quien ya está declara un estado que ya era cierto; retirar a quien no está
 * es creer que se saca a alguien de donde no está, y responder `200` dejaría al administrador con
 * una idea falsa de cómo quedó la cúspide. No se distingue «no tiene equipo» de «está en otro»: las
 * dos significan lo mismo aquí —no está— y distinguirlas convertiría el error en una consulta sobre
 * dónde está cada cual, que es `RF-SP-064` y `RF-SP-065`.
 *
 * <p><b>Cerrar no es borrar</b> (`RN-SP-052`): la fila se queda con su fecha de fin, porque decide
 * a qué equipo se atribuía lo que esa red producía.
 */
@Service
public class RemoveTeamMembersService {

  static final String NO_PERTENECEN = "Estas personas no pertenecen hoy a este equipo.";

  private final TeamRepository equipos;
  private final TeamMemberRepository pertenencias;
  private final AuditWriter auditoria;
  private final TeamDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public RemoveTeamMembersService(
      TeamRepository equipos,
      TeamMemberRepository pertenencias,
      AuditWriter auditoria,
      TeamDetailReader detalle) {
    this(equipos, pertenencias, auditoria, detalle, Clock.systemUTC());
  }

  RemoveTeamMembersService(
      TeamRepository equipos,
      TeamMemberRepository pertenencias,
      AuditWriter auditoria,
      TeamDetailReader detalle,
      Clock reloj) {
    this.equipos = equipos;
    this.pertenencias = pertenencias;
    this.auditoria = auditoria;
    this.detalle = detalle;
    this.reloj = reloj;
  }

  @Transactional
  public TeamDetailResponse remove(UUID id, RemoveTeamMembersRequest peticion) {
    List<UUID> solicitados = peticion.memberIds();

    // Con bloqueo, y el equipo puede estar `INACTIVO`: lo único que se exige es
    // que exista y no esté eliminado.
    Team equipo =
        equipos
            .findAliveByIdForUpdate(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un equipo con ese identificador."));

    // En UNA consulta, y solo las de ESTE equipo: quien pertenece a otro es, para
    // esta operación, alguien que no está aquí.
    Map<UUID, TeamMember> vigentes = new HashMap<>();
    pertenencias
        .findActiveIn(equipo.getId(), solicitados)
        .forEach(fila -> vigentes.put(fila.getUserId(), fila));

    List<FieldError> ajenos =
        solicitados.stream()
            .filter(persona -> !vigentes.containsKey(persona))
            .map(
                persona ->
                    new FieldError(
                        "memberIds",
                        "EX-002",
                        "La persona '" + persona + "' no pertenece hoy a este equipo."))
            .toList();

    if (!ajenos.isEmpty()) {
      // Toda la lista o ninguna, como en la asignación y por lo mismo: a medias,
      // el motivo declarado valdría para un conjunto distinto del que se pidió.
      throw new UnprocessableEntityException("EX-002", NO_PERTENECEN, ajenos);
    }

    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    for (UUID persona : solicitados) {
      TeamMember vigente = vigentes.get(persona);
      if (!vigente.close(ahora)) {
        continue;
      }
      auditoria.recordChange(
          new ChangeEvent(
              TeamDetailReader.MODULO,
              TeamMembershipRetirementService.ENTIDAD,
              persona,
              ChangeAction.UPDATE,
              Map.of(
                  "team_id", equipo.getId().toString(),
                  "ended", true,
                  "reason", peticion.reason())));
    }

    // El volcado deja el cierre en la base antes de releer el detalle: sin él, la
    // respuesta podría contar todavía a quien acaba de salir.
    pertenencias.flush();

    return detalle.leer(equipo.getId());
  }
}
