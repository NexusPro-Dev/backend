package com.factech.nexus.modules.system.teams.domain.repository;

import com.factech.nexus.modules.system.teams.domain.models.Team;
import java.util.Optional;
import java.util.UUID;

/**
 * Escritura y lectura bloqueante del equipo (`RF-SP-063` y el resto del submódulo).
 *
 * <p>La forma de `AC` y de `PM`: la unicidad del nombre se comprueba antes y se <b>traduce</b>
 * después —el índice parcial muerde en el {@code INSERT} y sale como el mismo {@code 409}—, de modo
 * que quien llama no nota si entró por la comprobación previa o por la carrera.
 */
public interface TeamRepository {

  /** ¿Hay un equipo no eliminado con ese nombre, sin distinguir mayúsculas ni acentos? */
  boolean existsAliveName(String name);

  Team save(Team equipo);

  /** El equipo no eliminado, bloqueado: lo que piden la corrección y el cambio de estado. */
  Optional<Team> findAliveByIdForUpdate(UUID id);

  /** Cualquiera, bloqueado: lo pide la baja, que distingue «no existe» de «ya está eliminado». */
  Optional<Team> findByIdForUpdate(UUID id);
}
