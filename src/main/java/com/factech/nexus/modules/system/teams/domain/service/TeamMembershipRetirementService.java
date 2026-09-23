package com.factech.nexus.modules.system.teams.domain.service;

import com.factech.nexus.modules.system.teams.application.TeamMembershipRetirement;
import com.factech.nexus.modules.system.teams.domain.models.TeamMember;
import com.factech.nexus.modules.system.teams.domain.repository.TeamMemberRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * El adaptador de {@link TeamMembershipRetirement} (`RN-SP-055`, `RF-SP-070` `T-09`).
 *
 * <p><b>{@code MANDATORY} y no {@code REQUIRED}</b>, y es la línea que sostiene la regla: esta
 * escritura tiene que morir con la transacción que la invoca. Si el retiro del rol se revierte, la
 * pertenencia <b>no</b> puede quedar cerrada —la persona sigue siendo manager— y si abriera
 * transacción propia quedaría cerrada de todos modos. Declararlo obligatorio hace que un llamador
 * descuidado falle de inmediato en lugar de dejar el sistema a medias.
 *
 * <p><b>No falla si no hay pertenencia</b>: devuelve {@code false} y no escribe nada. Quien retira
 * un rol no sabe si la persona estaba en un equipo, y no tiene por qué saberlo.
 */
@Service
public class TeamMembershipRetirementService implements TeamMembershipRetirement {

  static final String ENTIDAD = "team_members";

  private final TeamMemberRepository pertenencias;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public TeamMembershipRetirementService(TeamMemberRepository pertenencias, AuditWriter auditoria) {
    this(pertenencias, auditoria, Clock.systemUTC());
  }

  TeamMembershipRetirementService(
      TeamMemberRepository pertenencias, AuditWriter auditoria, Clock reloj) {
    this.pertenencias = pertenencias;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean retire(UUID userId, String reason) {
    if (userId == null) {
      return false;
    }
    List<TeamMember> vigentes = pertenencias.findActiveOf(List.of(userId));
    if (vigentes.isEmpty()) {
      return false;
    }

    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    for (TeamMember vigente : vigentes) {
      if (!vigente.close(ahora)) {
        continue;
      }
      // La misma forma que la de `RF-SP-069`: el `entity_id` es la persona, y el
      // motivo viaja dentro. Lo que distingue esta fila de un retiro manual es
      // `RN-SP-055`, que dice quién la provocó.
      auditoria.recordChange(
          new ChangeEvent(
              TeamDetailReader.MODULO,
              ENTIDAD,
              userId,
              ChangeAction.UPDATE,
              Map.of(
                  "team_id",
                  vigente.getTeamId().toString(),
                  "ended",
                  true,
                  "reason",
                  reason == null ? "RN-SP-055" : reason,
                  "rule",
                  "RN-SP-055")));
    }
    return true;
  }
}
