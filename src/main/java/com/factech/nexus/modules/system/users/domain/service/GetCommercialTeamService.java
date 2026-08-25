package com.factech.nexus.modules.system.users.domain.service;

import com.factech.nexus.modules.system.users.application.CommercialStructureResponse;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import com.factech.nexus.modules.system.users.domain.repository.RoleCatalog;
import com.factech.nexus.modules.system.users.domain.repository.TeamMember;
import com.factech.nexus.modules.system.users.domain.repository.UserRepository;
import com.factech.nexus.modules.system.users.domain.repository.UserSupervisor;
import com.factech.nexus.modules.system.users.domain.security.CommercialStructure;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.List;
import java.util.Optional;
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
 *   <li><b>Sin filtros.</b> `RF-SP-025` ya filtra el listado general; replicar esa semántica sobre
 *       un subconjunto que cabe en una o dos páginas obligaría a mantener dos filtrados
 *       sincronizados sin responder ninguna pregunta nueva.
 * </ul>
 *
 * <p><b>Quien no pertenece a la fuerza comercial recibe {@code 200}</b> con la estructura vacía, no
 * {@code 404} ni {@code 409}. «Esta persona no tiene estructura comercial» es una respuesta
 * legítima y distinta de «esta persona no existe»; devolver un error obligaría a la interfaz a
 * distinguir dos fallos para pintar lo mismo.
 *
 * <p>Todo en <b>una transacción de solo lectura</b>: el superior y el equipo se leen de la misma
 * foto. Leídos por separado, una reasignación simultánea podría dejar una respuesta en la que la
 * persona aparece sin superior y a la vez en el equipo de alguien.
 */
@Service
public class GetCommercialTeamService {

  private final UserRepository usuarios;
  private final RoleCatalog roles;
  private final CommercialStructure estructura;
  private final Pagination paginacion;

  public GetCommercialTeamService(
      UserRepository usuarios,
      RoleCatalog roles,
      CommercialStructure estructura,
      Pagination paginacion) {
    this.usuarios = usuarios;
    this.roles = roles;
    this.estructura = estructura;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public CommercialStructureResponse team(UUID userId, Integer pagina, Integer tamano) {
    Pagination.Slice trozo = paginacion.resolver(pagina, tamano);

    User persona =
        usuarios
            .findNotDeletedById(userId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "VAL-002", "No existe una persona con ese identificador."));

    Optional<AssignableRole> rango =
        estructura.rolDeMayorRango(roles.findAllById(roles.roleIdsOf(userId)));

    // `FA-002`: la cúspide omite el superior. AUSENTE y no en nulo — es lo que
    // distingue «no depende de nadie» de «no se pudo resolver».
    Optional<UserSupervisor> superior = usuarios.findActiveSupervisor(userId);

    int total = usuarios.countSupervisees(userId);
    List<TeamMember> equipo = usuarios.findTeam(userId, trozo.offset(), trozo.size());

    return new CommercialStructureResponse(
        new CommercialStructureResponse.Person(
            persona.getId(),
            persona.getUsername(),
            persona.getFirstName(),
            persona.getLastName(),
            rango.map(AssignableRole::code).orElse(null),
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
                        jefe.roleCode(),
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
                            miembro.roleCode(),
                            miembro.status()))
                .toList(),
            total,
            trozo.page(),
            trozo.size()));
  }
}
