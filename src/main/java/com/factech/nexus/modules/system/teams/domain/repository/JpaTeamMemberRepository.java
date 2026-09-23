package com.factech.nexus.modules.system.teams.domain.repository;

import com.factech.nexus.modules.system.teams.domain.models.TeamMember;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * {@link TeamMemberRepository} sobre JPA.
 *
 * <p><b>Traduce {@code uq_team_members_vigente} por el nombre de la restricción</b>, igual que
 * {@link JpaTeamRepository} hace con el del equipo. Es la red de `RN-SP-052`: dos peticiones
 * simultáneas que asignan a la misma persona a dos equipos distintos no se pueden ordenar con el
 * bloqueo del equipo —son equipos distintos—, de modo que quien decide es el índice parcial, y el
 * perdedor tiene que recibir el mismo `409` de negocio que habría recibido por la comprobación
 * previa. Sin esta traducción recibiría un `500`.
 */
@Repository
public class JpaTeamMemberRepository implements TeamMemberRepository {

  private static final String UQ_VIGENTE = "uq_team_members_vigente";

  static final String YA_TIENE_EQUIPO =
      "Alguna de las personas acaba de ser asignada a otro equipo. Vuelva a intentarlo.";

  private final EntityManager em;

  public JpaTeamMemberRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public List<TeamMember> findActiveOf(Collection<UUID> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return List.of();
    }
    return em.createQuery(
            "SELECT m FROM TeamMember m WHERE m.userId IN :personas AND m.endedAt IS NULL",
            TeamMember.class)
        .setParameter("personas", userIds)
        .getResultList();
  }

  @Override
  public List<TeamMember> saveAll(Collection<TeamMember> pertenencias) {
    List<TeamMember> guardadas = new ArrayList<>();
    try {
      for (TeamMember pertenencia : pertenencias) {
        em.persist(pertenencia);
        guardadas.add(pertenencia);
      }
      return List.copyOf(guardadas);
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public void flush() {
    try {
      em.flush();
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  private static RuntimeException traducir(PersistenceException fallo) {
    if (UQ_VIGENTE.equals(nombreDeRestriccion(fallo))) {
      return new BusinessRuleException(
          "RN-SP-052",
          YA_TIENE_EQUIPO,
          List.of(new FieldError("memberIds", "RN-SP-052", YA_TIENE_EQUIPO)));
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
