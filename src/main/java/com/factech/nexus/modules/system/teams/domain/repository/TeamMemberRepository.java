package com.factech.nexus.modules.system.teams.domain.repository;

import com.factech.nexus.modules.system.teams.domain.models.TeamMember;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * La escritura de las pertenencias (`RF-SP-069`, `RF-SP-070`).
 *
 * <p><b>Es un puerto aparte de {@link TeamRepository}</b> aunque el submódulo sea el mismo: aquel
 * gobierna el agregado {@code Team} —su unicidad de nombre y su bloqueo— y este las filas de {@code
 * team_members}, que tienen su propia restricción y su propia traducción. Juntarlos ataría cada
 * cambio de una a la otra.
 *
 * <p><b>Y es de escritura, no de consulta.</b> Las lecturas de {@code team_members} que sirven a
 * una respuesta —los miembros del detalle, el recuento, quiénes pasaron— viven en {@link
 * TeamQueryRepository} sobre proyecciones. Aquí se cargan <b>entidades</b>, porque hay que
 * cerrarlas.
 */
public interface TeamMemberRepository {

  /**
   * Las pertenencias <b>vigentes</b> de un conjunto de personas, en <b>una</b> consulta.
   *
   * <p>En bloque y no una por persona: un lote de cien personas no puede costar cien consultas, y
   * es el `N+1` que el plan de `RF-SP-069` declara como riesgo.
   */
  List<TeamMember> findActiveOf(Collection<UUID> userIds);

  /**
   * Las pertenencias vigentes <b>de ESTE equipo</b> para un conjunto de personas, en una consulta
   * (`RF-SP-070`).
   *
   * <p>No es {@link #findActiveOf} con un filtro encima: la diferencia es la que hace que quien
   * pertenece a OTRO equipo salga como «aquí no está», que es exactamente lo que el retiro necesita
   * responder. Preguntar primero por todas y filtrar después dejaría la decisión repetida en cada
   * llamador.
   */
  List<TeamMember> findActiveIn(UUID teamId, Collection<UUID> userIds);

  List<TeamMember> saveAll(Collection<TeamMember> pertenencias);

  /**
   * Vuelca lo pendiente para que la carrera sobre {@code uq_team_members_vigente} salga traducida
   * al `409` de este caso de uso y no como un fallo al confirmar, fuera de él — donde ya no hay
   * nadie que sepa traducirlo. Es lo mismo que {@link TeamRepository#flush} hace con el nombre.
   */
  void flush();
}
