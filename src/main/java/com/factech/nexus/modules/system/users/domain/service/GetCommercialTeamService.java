package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.users.application.CommercialStructureResponse;
import com.factech.nexus.modules.system.users.application.UserResponse;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.repository.TeamMember;
import com.factech.nexus.modules.system.users.domain.repository.UserQueryRepository;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.modules.system.users.domain.repository.UserSupervisor;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Superior inmediato y equipo directo de una persona (`RF-SP-042`).
 *
 * <p><b>Casi todo lo que define este requerimiento es lo que NO devuelve</b>, y no es una
 * limitación provisional: es lo que impide adelantar la decisión <b>D-22</b> —el alcance de los
 * datos según quién pregunta— sin que nadie la haya tomado.
 *
 * <ul>
 *   <li><b>Un solo nivel.</b> El equipo directo, nunca el árbol descendente: devolverlo publicaría
 *       de una vez la estructura completa de la empresa por un permiso de lectura de usuarios.
 *   <li><b>Sin conteo indirecto.</b> {@code totalElements} cuenta a quienes reportan directamente,
 *       y nada más.
 *   <li><b>Sin historial.</b> Quién fue su superior antes está en la tabla y no se publica aquí.
 *   <li><b>Un solo filtro, y es el rol</b> (10-09-2026). Ni búsqueda por nombre, ni estado, ni
 *       país: `RF-SP-025` ya los tiene sobre el listado general y duplicarlos aquí obligaría a
 *       mantener dos semánticas sincronizadas. El rol es la excepción porque el listado general
 *       <b>no sabe responder</b> «de la gente que cuelga de este agente, enséñame solo los
 *       clientes» — una pregunta que ni siquiera existía cuando se decidió que no habría filtros,
 *       porque hasta `RF-SP-045` esta estructura solo relacionaba vendedores entre sí.
 * </ul>
 *
 * <p><b>El filtro acota el equipo y NADA más.</b> Ni el superior ni la persona consultada se ven
 * afectados, y no es una omisión: el superior es uno solo y <b>su ausencia ya significa otra
 * cosa</b> —«no depende de nadie», la cúspide (`CA-SP-445`)—. Si el filtro pudiera quitarlo, la
 * interfaz no podría distinguir las dos cosas.
 *
 * <p><b>Quien no pertenece a la fuerza comercial recibe {@code 200}</b> con la estructura vacía, no
 * {@code 404} ni {@code 409}. «Esta persona no tiene estructura comercial» es una respuesta
 * legítima y distinta de «esta persona no existe»; devolver un error obligaría a la interfaz a
 * distinguir dos fallos para pintar lo mismo. <b>Sus roles sí salen</b>: no tener estructura no es
 * no tener roles.
 *
 * <p>Todo en <b>una transacción de solo lectura</b>: el superior y el equipo se leen de la misma
 * foto. Leídos por separado, una reasignación simultánea podría dejar una respuesta en la que la
 * persona aparece sin superior y a la vez en el equipo de alguien.
 */
@Service
public class GetCommercialTeamService {

  private final UserRepository usuarios;
  private final UserQueryRepository consultas;
  private final Pagination paginacion;

  public GetCommercialTeamService(
      UserRepository usuarios, UserQueryRepository consultas, Pagination paginacion) {
    this.usuarios = usuarios;
    this.consultas = consultas;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public CommercialStructureResponse team(
      UUID userId, List<String> roleCodes, Integer pagina, Integer tamano) {

    Pagination.Slice trozo = paginacion.resolver(pagina, tamano);
    List<String> codigos = normalizar(roleCodes);

    User persona =
        usuarios
            .findNotDeletedById(userId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "VAL-002", "No existe una persona con ese identificador."));

    // `FA-002`: la cúspide omite el superior. AUSENTE y no en nulo — es lo que
    // distingue «no depende de nadie» de «no se pudo resolver». Y NO se filtra:
    // ver el javadoc de la clase.
    Optional<UserSupervisor> superior = usuarios.findActiveSupervisor(userId);

    int total = usuarios.countTeam(userId, codigos);
    List<TeamMember> equipo = usuarios.findTeam(userId, codigos, trozo.offset(), trozo.size());

    // UNA consulta para los roles de TODA la respuesta, y no una por persona.
    // Un N+1 aquí no rompería ninguna prueba —la respuesta sería correcta y
    // solo lenta—, que es exactamente por lo que hay que evitarlo a propósito.
    Map<UUID, List<UserResponse.RoleRef>> roles =
        rolesDe(userId, superior.map(UserSupervisor::supervisorId).orElse(null), equipo);

    return new CommercialStructureResponse(
        new CommercialStructureResponse.Person(
            persona.getId(),
            persona.getUsername(),
            persona.getFirstName(),
            persona.getLastName(),
            roles.getOrDefault(persona.getId(), List.of()),
            persona.getStatus().name(),
            null),
        superior
            .map(
                jefe ->
                    new CommercialStructureResponse.Person(
                        jefe.supervisorId(),
                        jefe.username(),
                        jefe.firstName(),
                        jefe.lastName(),
                        roles.getOrDefault(jefe.supervisorId(), List.of()),
                        jefe.status(),
                        jefe.since()))
            .orElse(null),
        null,
        null,
        PageResponse.de(
            equipo.stream()
                .map(
                    miembro ->
                        CommercialStructureResponse.Person.de(
                            miembro.id(),
                            miembro.username(),
                            miembro.firstName(),
                            miembro.lastName(),
                            roles.getOrDefault(miembro.id(), List.of()),
                            miembro.status()))
                .toList(),
            total,
            trozo.page(),
            trozo.size()));
  }

  /**
   * Los roles de todas las personas de la respuesta, en una sola consulta.
   *
   * <p>Reutiliza el puerto que `RF-SP-025` ya usa para lo mismo. Escribir otra consulta aquí daría
   * dos formas de responder «¿qué roles porta esta persona?», y el día que una excluyera los roles
   * eliminados y la otra no, nadie se enteraría.
   */
  private Map<UUID, List<UserResponse.RoleRef>> rolesDe(
      UUID persona, UUID superior, List<TeamMember> equipo) {

    Set<UUID> ids = new LinkedHashSet<>();
    ids.add(persona);
    if (superior != null) {
      ids.add(superior);
    }
    equipo.forEach(miembro -> ids.add(miembro.id()));

    return PersonRoles.de(consultas.rolesOf(new ArrayList<>(ids)));
  }

  /**
   * Recorta, descarta lo vacío, normaliza a mayúsculas y quita repetidos.
   *
   * <p>A mayúsculas porque los códigos de rol se persisten así, y un {@code ?roles=agente} que
   * devolviera vacío se leería como «no hay agentes» en lugar de como un error de forma. Repetir un
   * código no significa nada distinto de escribirlo una vez. Y lo que llega en blanco se descarta:
   * sin eso, un {@code ?roles=} sin valor se convertiría en un predicado que no devuelve a nadie,
   * cuando lo que significa es «sin filtro».
   */
  private static List<String> normalizar(List<String> roleCodes) {
    if (roleCodes == null) {
      return List.of();
    }
    return roleCodes.stream()
        .filter(codigo -> codigo != null && !codigo.isBlank())
        .map(codigo -> codigo.trim().toUpperCase(Locale.ROOT))
        .distinct()
        .toList();
  }
}
