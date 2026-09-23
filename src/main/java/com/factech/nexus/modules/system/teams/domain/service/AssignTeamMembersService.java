package com.factech.nexus.modules.system.teams.domain.service;

import com.factech.nexus.modules.system.teams.application.AssignTeamMembersRequest;
import com.factech.nexus.modules.system.teams.application.TeamDetailResponse;
import com.factech.nexus.modules.system.teams.domain.models.Team;
import com.factech.nexus.modules.system.teams.domain.models.TeamMember;
import com.factech.nexus.modules.system.teams.domain.repository.TeamMemberRepository;
import com.factech.nexus.modules.system.teams.domain.repository.TeamRepository;
import com.factech.nexus.modules.system.users.application.UserCatalog;
import com.factech.nexus.modules.system.users.application.UserCatalog.UserView;
import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import com.factech.nexus.modules.system.users.domain.repository.RoleCatalog;
import com.factech.nexus.modules.system.users.domain.security.CommercialStructure;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-SP-069`: asignar managers a un equipo.
 *
 * <p><b>Es la operación que da sentido a las cinco anteriores.</b> Hasta ahora los equipos
 * existían, se listaban, se abrían, se corregían y se eliminaban, y estaban todos vacíos: aquí es
 * donde {@code memberCount} deja de decir cero.
 *
 * <p><b>Resolver todo antes de escribir nada, y escribir todo de una vez.</b> El equipo se carga
 * con bloqueo, las personas se resuelven en una consulta y sus roles en otra, y solo cuando se sabe
 * que <b>todas</b> pueden entrar se cierran y se abren pertenencias. Es lo que hace posible el
 * `422` con la lista completa de quienes no pueden, en lugar de rechazar por la primera y obligar
 * al administrador a descubrirlas de una en una.
 *
 * <p><b>Toda la lista o ninguna</b> (`spec.md` §14.4): a medias, quien administra no sabría quién
 * entró sin volver a consultar, y el motivo que declaró valdría para un conjunto distinto del que
 * pidió.
 *
 * <p><b>Aquí sí se comprueba `RN-SP-053`</b> —un equipo `INACTIVO` no recibe miembros—, y es la
 * otra mitad de la asimetría que `RF-SP-067` declaró: la regla la aplica quien intenta entrar, no
 * quien cierra la puerta. Por eso el cambio de estado no comprueba nada y esta operación sí, y por
 * eso la restricción es del equipo que <b>recibe</b>: de uno suspendido se puede sacar gente y se
 * puede mover a alguien hacia uno activo.
 *
 * <p><b>Nada de `user_supervisors` ni de `user_roles` se toca</b> (`CA-SP-787`). Un equipo agrupa y
 * no manda: quién está a cargo de quién lo administra `RF-SP-041`, y esta operación solo dice en
 * qué cajón de la cúspide está cada manager.
 */
@Service
public class AssignTeamMembersService {

  static final String EQUIPO_INACTIVO =
      "El equipo está inactivo y no admite miembros nuevos. Actívelo antes de asignar.";
  static final String ENTIDAD = "team_members";
  static final String NO_ES_CUSPIDE =
      "Solo pueden pertenecer a un equipo quienes portan el rol comercial de mayor rango.";

  private final TeamRepository equipos;
  private final TeamMemberRepository pertenencias;
  private final UserCatalog personas;
  private final RoleCatalog roles;
  private final AuditWriter auditoria;
  private final TeamDetailReader detalle;
  private final UuidV7Generator ids;
  private final Clock reloj;

  @Autowired
  public AssignTeamMembersService(
      TeamRepository equipos,
      TeamMemberRepository pertenencias,
      UserCatalog personas,
      RoleCatalog roles,
      AuditWriter auditoria,
      TeamDetailReader detalle,
      UuidV7Generator ids) {
    this(equipos, pertenencias, personas, roles, auditoria, detalle, ids, Clock.systemUTC());
  }

  AssignTeamMembersService(
      TeamRepository equipos,
      TeamMemberRepository pertenencias,
      UserCatalog personas,
      RoleCatalog roles,
      AuditWriter auditoria,
      TeamDetailReader detalle,
      UuidV7Generator ids,
      Clock reloj) {
    this.equipos = equipos;
    this.pertenencias = pertenencias;
    this.personas = personas;
    this.roles = roles;
    this.auditoria = auditoria;
    this.detalle = detalle;
    this.ids = ids;
    this.reloj = reloj;
  }

  @Transactional
  public TeamDetailResponse assign(UUID id, AssignTeamMembersRequest peticion) {
    List<UUID> solicitados = peticion.memberIds();

    // El bloqueo es del equipo DESTINO, y es lo que ordena esta operación contra
    // la eliminación del mismo equipo (`RF-SP-068`): si la baja gana, aquí sale
    // `404`; si gana la asignación, la baja recibe su `409` por tener miembros.
    Team equipo =
        equipos
            .findAliveByIdForUpdate(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un equipo con ese identificador."));

    if (!equipo.estaActivo()) {
      throw new BusinessRuleException(
          "RN-SP-053",
          EQUIPO_INACTIVO,
          List.of(new FieldError("id", "RN-SP-053", EQUIPO_INACTIVO)));
    }

    verificarQueExisten(solicitados);
    verificarQueSonCuspide(solicitados);

    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    Map<UUID, TeamMember> vigentePorPersona = new HashMap<>();
    pertenencias
        .findActiveOf(solicitados)
        .forEach(vigente -> vigentePorPersona.put(vigente.getUserId(), vigente));

    // PRIMERA PASADA: quiénes entran de verdad y a quién hay que cerrarle la
    // pertenencia anterior. Quien ya está en ESTE equipo se queda fuera de las
    // dos listas (`FA-001`): cerrar y reabrir le quitaría el `joinedAt` que
    // ordena la lista del detalle, y llenaría el historial de tramos falsos.
    // Y no se audita, porque no hubo cambio.
    List<UUID> entran = new ArrayList<>();
    for (UUID persona : solicitados) {
      TeamMember vigente = vigentePorPersona.get(persona);
      if (vigente != null && vigente.getTeamId().equals(equipo.getId())) {
        continue;
      }
      entran.add(persona);
      if (vigente != null) {
        vigente.close(ahora);
        auditar(
            ChangeAction.UPDATE,
            persona,
            Map.of(
                "team_id", vigente.getTeamId().toString(),
                "ended", true,
                "reason", peticion.reason()));
      }
    }

    // EL VOLCADO DEL CIERRE VA ANTES DE ABRIR, y no es una optimización: JPA
    // vuelca los `INSERT` ANTES que los `UPDATE`, de modo que sin esta línea la
    // pertenencia nueva entraría mientras la anterior sigue abierta y
    // `uq_team_members_vigente` la mordería — un `409` para el caso normal de
    // mover a alguien de equipo, que es justo lo que `RN-SP-052` permite.
    pertenencias.flush();

    // SEGUNDA PASADA: las aperturas, ya con el hueco libre.
    List<TeamMember> nuevas = new ArrayList<>();
    for (UUID persona : entran) {
      TeamMember nueva = TeamMember.open(ids.next(), equipo.getId(), persona, ahora);
      nuevas.add(nueva);
      auditar(
          ChangeAction.CREATE,
          persona,
          Map.of(
              "team_id", equipo.getId().toString(),
              "membership_id", nueva.getId().toString(),
              "reason", peticion.reason()));
    }

    pertenencias.saveAll(nuevas);
    // Y el segundo volcado convierte la CARRERA sobre el mismo índice en el
    // `409` traducido, en lugar de un fallo al confirmar fuera de este método,
    // donde ya no hay nadie que sepa traducirlo.
    pertenencias.flush();

    return detalle.leer(equipo.getId());
  }

  /**
   * `EX-003`: alguna persona no existe o está eliminada, <b>sin distinguirlas</b> —el mismo
   * criterio de `RF-SP-030` `EX-002`: desde fuera son lo mismo, el identificador no designa a
   * alguien asignable.
   */
  private void verificarQueExisten(List<UUID> solicitados) {
    Map<UUID, UserView> encontradas = new HashMap<>();
    // Una sola consulta para toda la lista: cien personas no pueden costar cien
    // viajes, que es el `N+1` declarado como riesgo en el plan §10.
    personas.findAll(new LinkedHashSet<>(solicitados)).forEach(v -> encontradas.put(v.id(), v));

    List<FieldError> faltan =
        solicitados.stream()
            .filter(
                persona -> !encontradas.containsKey(persona) || encontradas.get(persona).deleted())
            .map(
                persona ->
                    new FieldError(
                        "memberIds", "EX-003", "La persona '" + persona + "' no existe."))
            .toList();

    if (!faltan.isEmpty()) {
      throw new UnprocessableEntityException("EX-003", "Una o más personas no existen.", faltan);
    }
  }

  /**
   * `EX-004` / `RN-SP-051`: solo la cúspide. La decisión la toma {@link TeamMembershipRules} sobre
   * {@link CommercialStructure}, que es la <b>única</b> definición de «cúspide» del sistema.
   */
  private void verificarQueSonCuspide(List<UUID> solicitados) {
    Map<UUID, Set<UUID>> rolesPorPersona = roles.roleIdsOfAll(new LinkedHashSet<>(solicitados));

    // El catálogo se memoriza DURANTE esta comprobación, y no es un adorno:
    // `CommercialStructure.esCuspide` pregunta por el rol padre, de modo que sin
    // memoria un lote de cien personas son cien lecturas del mismo puñado de
    // roles. La regla no cambia; cambia cuántas veces se lee lo mismo.
    MemoizingRoleCatalog memorizado = new MemoizingRoleCatalog(roles);
    TeamMembershipRules reglas = new TeamMembershipRules(new CommercialStructure(memorizado));

    Set<UUID> todosLosRoles = new LinkedHashSet<>();
    rolesPorPersona.values().forEach(todosLosRoles::addAll);
    Map<UUID, AssignableRole> catalogo = new HashMap<>();
    memorizado.findAllById(todosLosRoles).forEach(rol -> catalogo.put(rol.id(), rol));

    Map<UUID, List<AssignableRole>> resueltos = new LinkedHashMap<>();
    for (UUID persona : solicitados) {
      resueltos.put(
          persona,
          rolesPorPersona.getOrDefault(persona, Set.of()).stream()
              .map(catalogo::get)
              .filter(Objects::nonNull)
              .toList());
    }

    TeamMembershipRules.Veredicto veredicto = reglas.evaluar(solicitados, resueltos);
    if (veredicto.todosAdmitidos()) {
      return;
    }

    List<FieldError> rechazados =
        veredicto.rechazados().stream()
            .map(
                persona ->
                    new FieldError(
                        "memberIds",
                        "RN-SP-051",
                        "La persona '" + persona + "' no porta el rol comercial de mayor rango."))
            .toList();

    throw new UnprocessableEntityException("RN-SP-051", NO_ES_CUSPIDE, rechazados);
  }

  /**
   * Una fila por pertenencia abierta y otra por cada una cerrada, todas bajo el mismo identificador
   * de correlación —el de la petición, que {@code JpaAuditWriter} toma del contexto—, de modo que
   * «esta reorganización movió a estas cinco personas» se reconstruya de una pieza.
   *
   * <p><b>El {@code entity_id} es la PERSONA y no la fila de pertenencia</b>, por lo mismo que
   * `RF-SP-041` audita al subordinado y no la fila de asignación: la pregunta que alguien hará es
   * «¿a qué equipos ha pertenecido esta persona?», y con el identificador de la fila esa consulta
   * no se puede hacer sin conocer de antemano los tramos.
   */
  private void auditar(ChangeAction accion, UUID persona, Map<String, Object> cambios) {
    auditoria.recordChange(
        new ChangeEvent(TeamDetailReader.MODULO, ENTIDAD, persona, accion, cambios));
  }
}
