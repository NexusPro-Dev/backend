package com.factech.nexus.modules.system.teams.domain.service;

import com.factech.nexus.modules.system.teams.application.ChangeTeamStatusRequest;
import com.factech.nexus.modules.system.teams.application.TeamDetailResponse;
import com.factech.nexus.modules.system.teams.domain.models.Team;
import com.factech.nexus.modules.system.teams.domain.models.TeamStatus;
import com.factech.nexus.modules.system.teams.domain.repository.TeamRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ValidationException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-SP-067`: suspender o reactivar un equipo.
 *
 * <p><b>Es `RF-SP-007` para equipos en la forma y no en el fondo.</b> Desactivar un rol retira
 * permisos a sus portadores de inmediato (`RN-SEG-002`) y por eso emite además un evento de
 * seguridad; desactivar un equipo <b>no le quita nada a nadie</b>, porque un equipo no concede. Lo
 * único que cambia es hacia adelante: deja de admitir miembros nuevos.
 *
 * <p><b>El atajo de la idempotencia es la línea que más cuidado pide.</b> Si el estado declarado es
 * el que ya tiene, se sale sin escribir y sin auditar (`FA-001`, `CA-SP-765`). Escribir «por si
 * acaso» produciría una fila de auditoría por cada reintento y haría avanzar {@code updated_at} sin
 * que nada cambiara, que es cómo una auditoría deja de poder leerse.
 *
 * <p><b>Desactivar NO vacía el equipo</b> (`RN-SP-053`): ninguna fila de {@code team_members} se
 * toca en ninguno de los dos sentidos. Cerrarlas en silencio movería la atribución de toda una red
 * —cada manager arrastra a sus directores y a los agentes de estos— sin que nadie lo hubiera
 * decidido, y las comisiones leerán ese historial para repartir. Quien quiera vaciarlo lo hace
 * miembro a miembro y con motivo (`RF-SP-070`).
 *
 * <p><b>Aquí NO se comprueba que un equipo suspendido no reciba miembros</b>, aunque sea la regla
 * que este estado significa: la aplica quien intenta entrar (`RF-SP-069`). La asimetría es
 * deliberada —comprobarla en los dos sitios duplicaría la condición y abriría la puerta a que
 * divergieran—, y `CA-SP-766` la prueba desde la asignación, que es donde vive.
 */
@Service
public class ChangeTeamStatusService {

  static final String ESTADO_INVALIDO = "El estado indicado no es válido";

  private final TeamRepository equipos;
  private final AuditWriter auditoria;
  private final TeamDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public ChangeTeamStatusService(
      TeamRepository equipos, AuditWriter auditoria, TeamDetailReader detalle) {
    this(equipos, auditoria, detalle, Clock.systemUTC());
  }

  ChangeTeamStatusService(
      TeamRepository equipos, AuditWriter auditoria, TeamDetailReader detalle, Clock reloj) {
    this.equipos = equipos;
    this.auditoria = auditoria;
    this.detalle = detalle;
    this.reloj = reloj;
  }

  @Transactional
  public TeamDetailResponse change(UUID id, ChangeTeamStatusRequest peticion) {
    TeamStatus destino = resolver(peticion);

    // Con bloqueo: dos cambios simultáneos se ordenan y el segundo ve el estado
    // que dejó el primero, de modo que uno escribe y el otro resulta idempotente
    // en lugar de escribir los dos (plan §7).
    Team equipo =
        equipos
            .findAliveByIdForUpdate(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un equipo con ese identificador."));

    TeamStatus antes = equipo.getStatus();
    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    boolean cambio =
        destino == TeamStatus.ACTIVO ? equipo.activate(ahora) : equipo.deactivate(ahora);

    if (cambio) {
      auditoria.recordChange(
          new ChangeEvent(
              TeamDetailReader.MODULO,
              TeamDetailReader.ENTIDAD,
              equipo.getId(),
              ChangeAction.UPDATE,
              Map.of(
                  "status", Map.of("before", antes.name(), "after", equipo.getStatus().name()))));
    }

    return detalle.leer(equipo.getId());
  }

  /**
   * `VAL-001`: el estado es obligatorio y se admite en cualquier caja, pero solo puede ser uno de
   * los dos declarados.
   *
   * <p>El mensaje dice <b>qué se envió y qué se admite</b>, como en el filtro del listado: un «no
   * es válido» a secas obliga a quien integra a ir a buscar el dominio al contrato.
   */
  private static TeamStatus resolver(ChangeTeamStatusRequest peticion) {
    String estado = peticion.estadoNormalizado();
    String admitidos =
        String.join(", ", Arrays.stream(TeamStatus.values()).map(Enum::name).toList());
    if (estado == null) {
      String mensaje = ESTADO_INVALIDO + ": es obligatorio. Valores admitidos: " + admitidos + ".";
      throw new ValidationException(
          "VAL-001", mensaje, List.of(new FieldError("status", "VAL-001", mensaje)));
    }
    try {
      return TeamStatus.valueOf(estado);
    } catch (IllegalArgumentException desconocido) {
      String mensaje =
          ESTADO_INVALIDO + ": '" + peticion.status() + "'. Valores admitidos: " + admitidos + ".";
      throw new ValidationException(
          "VAL-001", mensaje, List.of(new FieldError("status", "VAL-001", mensaje)));
    }
  }
}
