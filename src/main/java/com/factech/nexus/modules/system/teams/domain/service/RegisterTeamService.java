package com.factech.nexus.modules.system.teams.domain.service;

import com.factech.nexus.modules.system.teams.application.RegisterTeamRequest;
import com.factech.nexus.modules.system.teams.application.TeamDetailResponse;
import com.factech.nexus.modules.system.teams.domain.models.Team;
import com.factech.nexus.modules.system.teams.domain.repository.TeamRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-SP-063`: registrar un equipo.
 *
 * <p><b>El alta más pequeña del módulo, y la que estrena el submódulo.</b> Nombre contra los no
 * eliminados, inserción, auditoría y relectura del detalle, que sobre un equipo recién creado sale
 * vacío: cero miembros y la forma completa. No consume nada de `users`: quién puede pertenecer a un
 * equipo lo decide `RF-SP-069`, no esta operación.
 *
 * <p><b>Ningún evento de seguridad</b>, y conviene decir por qué se mira: crear un rol tampoco lo
 * emite, y un equipo concede todavía menos —nada—. El día que pertenecer a un equipo decida qué
 * datos se ven (D-22), esta decisión se revisa.
 */
@Service
public class RegisterTeamService {

  private final TeamRepository equipos;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final TeamDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public RegisterTeamService(
      TeamRepository equipos,
      AuditWriter auditoria,
      UuidV7Generator ids,
      TeamDetailReader detalle) {
    this(equipos, auditoria, ids, detalle, Clock.systemUTC());
  }

  RegisterTeamService(
      TeamRepository equipos,
      AuditWriter auditoria,
      UuidV7Generator ids,
      TeamDetailReader detalle,
      Clock reloj) {
    this.equipos = equipos;
    this.auditoria = auditoria;
    this.ids = ids;
    this.detalle = detalle;
    this.reloj = reloj;
  }

  @Transactional
  public TeamDetailResponse register(RegisterTeamRequest peticion) {
    // Solo contra los NO ELIMINADOS (`EX-001`): un equipo eliminado libera su
    // nombre (`RN-SP-050`). La red es el índice parcial, que muerde en el
    // INSERT y sale con el mismo código —de ahí que la carrera dé 409 y no 500.
    if (peticion.name() != null && equipos.existsAliveName(peticion.name())) {
      String mensaje = "Ya existe un equipo con ese nombre.";
      throw new BusinessRuleException(
          "EX-001", mensaje, List.of(new FieldError("name", "EX-001", mensaje)));
    }

    Team nuevo =
        equipos.save(
            Team.create(
                ids.next(), peticion.name(), peticion.description(), OffsetDateTime.now(reloj)));

    // La instantánea la arma el agregado, y es la misma que usará la baja
    // (`RF-SP-068`): el registro de creación y el de eliminación describen el
    // mismo equipo con las mismas claves.
    auditoria.recordChange(
        new ChangeEvent(
            TeamDetailReader.MODULO,
            TeamDetailReader.ENTIDAD,
            nuevo.getId(),
            ChangeAction.CREATE,
            nuevo.instantanea()));

    return detalle.leer(nuevo.getId());
  }
}
