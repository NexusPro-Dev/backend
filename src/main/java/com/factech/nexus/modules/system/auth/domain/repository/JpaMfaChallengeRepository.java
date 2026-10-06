package com.factech.nexus.modules.system.auth.domain.repository;

import com.factech.nexus.modules.system.auth.domain.models.MfaChallenge;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaMfaChallengeRepository implements MfaChallengeRepository {

  private final EntityManager em;

  public JpaMfaChallengeRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public void guardar(MfaChallenge desafio) {
    em.persist(desafio);
    em.flush();
  }

  @Override
  public Optional<MfaChallenge> buscarPorHashParaActualizar(String challengeHash) {
    if (challengeHash == null || challengeHash.isBlank()) {
      return Optional.empty();
    }
    return em
        .createQuery(
            "SELECT d FROM MfaChallenge d WHERE d.challengeHash = :hash", MfaChallenge.class)
        .setParameter("hash", challengeHash)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }
}
