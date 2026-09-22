package com.factech.nexus.modules.system.teams.domain.service;

import com.factech.nexus.modules.system.teams.application.TeamDetailResponse;
import com.factech.nexus.modules.system.teams.application.UpdateTeamRequest;
import com.factech.nexus.modules.system.teams.domain.models.Team;
import com.factech.nexus.modules.system.teams.domain.repository.TeamRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ValidationException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-SP-066`: corregir el nombre y la descripción de un equipo.
 *
 * <p><b>Se corrigen dos campos y nada más.</b> El estado tiene su operación (`RF-SP-067`) y los
 * miembros las suyas (`RF-SP-069`, `RF-SP-070`), cada una con su permiso: meterlas aquí haría que
 * un `PATCH` pudiera mover la organización comercial entera sin que el permiso lo dijera.
 *
 * <p><b>El equipo eliminado responde `404`, igual que el inexistente</b> (`EX-001`). Es distinto de
 * `RF-SP-068`, donde «ya estaba eliminado» es un `409` que informa de que el retiro anterior
 * funcionó; aquí un eliminado no es editable en ningún caso y distinguirlo solo añadiría un código
 * sin decisión detrás.
 *
 * <p><b>Un equipo `INACTIVO` SÍ se edita.</b> Inactivo significa que no recibe miembros
 * (`RN-SP-053`), no que sea inmutable: corregir una errata en un equipo suspendido es exactamente
 * lo que se hace antes de reactivarlo.
 *
 * <p><b>La unicidad se comprueba y además se traduce.</b> La comprobación previa excluye al propio
 * equipo —renombrarse al nombre que ya se tiene es válido— y la red es {@code uq_teams_name}: dos
 * renombrados simultáneos al mismo nombre salen por el mismo `409`, no por un `500`.
 */
@Service
public class UpdateTeamService {

  private static final int NOMBRE_MAXIMO = 100;
  private static final int DESCRIPCION_MAXIMA = 500;

  private final TeamRepository equipos;
  private final AuditWriter auditoria;
  private final TeamDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public UpdateTeamService(
      TeamRepository equipos, AuditWriter auditoria, TeamDetailReader detalle) {
    this(equipos, auditoria, detalle, Clock.systemUTC());
  }

  UpdateTeamService(
      TeamRepository equipos, AuditWriter auditoria, TeamDetailReader detalle, Clock reloj) {
    this.equipos = equipos;
    this.auditoria = auditoria;
    this.detalle = detalle;
    this.reloj = reloj;
  }

  @Transactional
  public TeamDetailResponse update(UUID id, UpdateTeamRequest peticion) {
    verificarFormato(peticion);

    Team equipo =
        equipos
            .findAliveByIdForUpdate(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un equipo con ese identificador."));

    if (peticion.name().presente() && peticion.name().valor() != null) {
      String nombre = peticion.name().valor().trim();
      if (equipos.existsAliveNameForOther(nombre, equipo.getId())) {
        String mensaje = "Ya existe un equipo con ese nombre.";
        throw new BusinessRuleException(
            "EX-002", mensaje, List.of(new FieldError("name", "EX-002", mensaje)));
      }
    }

    Map<String, Object> cambios =
        equipo.update(peticion.name(), peticion.description(), OffsetDateTime.now(reloj));

    if (!cambios.isEmpty()) {
      // El volcado explícito convierte la carrera sobre el nombre en el `409`
      // traducido, y no en un fallo al confirmar fuera de este método — donde
      // ya no hay nadie que sepa traducirlo.
      equipos.flush();
      auditoria.recordChange(
          new ChangeEvent(
              TeamDetailReader.MODULO,
              TeamDetailReader.ENTIDAD,
              equipo.getId(),
              ChangeAction.UPDATE,
              cambios));
    }

    return detalle.leer(equipo.getId());
  }

  /**
   * Los `400` de forma, <b>juntos</b>: quien envía el nombre vacío y la descripción larga tiene que
   * poder corregir las dos cosas de una vez.
   *
   * <p><b>El nombre en nulo explícito se rechaza aquí</b> y no en el agregado: un equipo sin nombre
   * no existe (`RN-SP-050`), de modo que {@code name: null} no es «borrar el nombre», es un dato
   * inválido — y el mensaje tiene que decir eso y no fallar más adelante por otra vía.
   */
  private static void verificarFormato(UpdateTeamRequest peticion) {
    List<FieldError> problemas = new ArrayList<>();
    if (!peticion.informaAlgo()) {
      problemas.add(
          new FieldError("body", "VAL-003", "Debe indicar al menos un campo a modificar."));
    }
    if (peticion.name().presente()) {
      String nombre = peticion.name().valor();
      if (nombre == null || nombre.isBlank() || nombre.trim().length() > NOMBRE_MAXIMO) {
        problemas.add(
            new FieldError(
                "name",
                "VAL-001",
                "El nombre no puede estar vacío ni superar los 100 caracteres."));
      }
    }
    if (peticion.description().presente()) {
      String descripcion = peticion.description().valor();
      if (descripcion != null && descripcion.trim().length() > DESCRIPCION_MAXIMA) {
        problemas.add(
            new FieldError(
                "description", "VAL-002", "La descripción no puede exceder 500 caracteres."));
      }
    }
    if (!problemas.isEmpty()) {
      throw new ValidationException(problemas.get(0).code(), resumen(problemas), problemas);
    }
  }

  private static String resumen(List<FieldError> problemas) {
    return problemas.size() == 1
        ? problemas.get(0).message()
        : "La petición trae " + problemas.size() + " campos inválidos.";
  }
}
