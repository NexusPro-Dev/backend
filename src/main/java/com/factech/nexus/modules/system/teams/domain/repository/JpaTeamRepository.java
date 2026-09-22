package com.factech.nexus.modules.system.teams.domain.repository;

import com.factech.nexus.modules.system.teams.domain.models.Team;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * {@link TeamRepository} sobre JPA.
 *
 * <p><b>La traducción de la unicidad es lo único que este repositorio decide</b>, y la decide por
 * el <b>nombre</b> de la restricción y no por el texto del driver: {@code uq_teams_name} es
 * `EX-001` del alta y `EX-002` de la corrección, con el mismo `409` y el mismo mensaje.
 */
@Repository
public class JpaTeamRepository implements TeamRepository {

  private static final String UQ_NOMBRE = "uq_teams_name";

  private final EntityManager em;

  public JpaTeamRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public boolean existsAliveName(String name) {
    // El mismo predicado que `uq_teams_name`, a propósito: si esta lectura
    // mirara otra cosa que el índice, la comprobación previa y la traducción de
    // la carrera dejarían pasar casos distintos.
    return !em.createNativeQuery(
            """
            SELECT 1 FROM teams
             WHERE deleted_at IS NULL
               AND f_unaccent(lower(name)) = f_unaccent(lower(CAST(:name AS text)))
             LIMIT 1
            """)
        .setParameter("name", name)
        .getResultList()
        .isEmpty();
  }

  @Override
  public boolean existsAliveNameForOther(String name, UUID id) {
    // La misma expresión del índice que `existsAliveName`, con el propio equipo
    // fuera: sin el `id <> :id`, renombrarse al nombre que ya se tiene daría un
    // 409 contra uno mismo.
    return !em.createNativeQuery(
            """
            SELECT 1 FROM teams
             WHERE deleted_at IS NULL
               AND id <> :id
               AND f_unaccent(lower(name)) = f_unaccent(lower(CAST(:name AS text)))
             LIMIT 1
            """)
        .setParameter("id", id)
        .setParameter("name", name)
        .getResultList()
        .isEmpty();
  }

  @Override
  public void flush() {
    try {
      em.flush();
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public Team save(Team equipo) {
    try {
      em.persist(equipo);
      em.flush();
      return equipo;
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public Optional<Team> findAliveByIdForUpdate(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery("SELECT t FROM Team t WHERE t.id = :id AND t.deletedAt IS NULL", Team.class)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public Optional<Team> findByIdForUpdate(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery("SELECT t FROM Team t WHERE t.id = :id", Team.class)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  private static RuntimeException traducir(PersistenceException fallo) {
    if (UQ_NOMBRE.equals(nombreDeRestriccion(fallo))) {
      String mensaje = "Ya existe un equipo con ese nombre.";
      return new BusinessRuleException(
          "EX-001", mensaje, List.of(new FieldError("name", "EX-001", mensaje)));
    }
    return fallo;
  }

  private static String nombreDeRestriccion(Throwable fallo) {
    for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
      if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion) {
        return violacion.getConstraintName();
      }
    }
    return null;
  }
}
