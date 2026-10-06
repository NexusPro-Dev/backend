package com.factech.nexus.modules.system.auth.domain.repository;

import com.factech.nexus.modules.system.auth.domain.models.MfaFactor;
import com.factech.nexus.modules.system.auth.domain.models.RecoveryCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaMfaFactorRepository implements MfaFactorRepository {

  private final EntityManager em;

  public JpaMfaFactorRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public Optional<MfaFactor> activoParaActualizar(UUID userId) {
    return porEstado(userId, MfaFactor.ACTIVO, true);
  }

  @Override
  public Optional<MfaFactor> pendienteParaActualizar(UUID userId) {
    return porEstado(userId, MfaFactor.PENDIENTE, true);
  }

  @Override
  public Optional<MfaFactor> activo(UUID userId) {
    return porEstado(userId, MfaFactor.ACTIVO, false);
  }

  @Override
  public void guardar(MfaFactor factor) {
    em.persist(factor);
    em.flush();
  }

  @Override
  public void sincronizar() {
    em.flush();
  }

  @Override
  public void guardarCodigos(List<RecoveryCode> codigos) {
    codigos.forEach(em::persist);
    em.flush();
  }

  @Override
  public List<RecoveryCode> codigosVigentesParaActualizar(UUID factorId) {
    return em.createQuery(
            """
            SELECT c FROM RecoveryCode c
             WHERE c.factorId = :factor
               AND c.usedAt IS NULL
               AND c.supersededAt IS NULL
            """,
            RecoveryCode.class)
        .setParameter("factor", factorId)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getResultList();
  }

  @Override
  public int anularCodigosVigentes(UUID factorId, OffsetDateTime ahora) {
    int anulados =
        em.createNativeQuery(
                """
                UPDATE mfa_recovery_codes
                   SET superseded_at = :ahora
                 WHERE factor_id = :factor
                   AND used_at IS NULL
                   AND superseded_at IS NULL
                """)
            .setParameter("ahora", ahora)
            .setParameter("factor", factorId)
            .executeUpdate();
    em.flush();
    return anulados;
  }

  @Override
  public String nombreDeUsuario(UUID userId) {
    return (String)
        em.createNativeQuery("SELECT username FROM users WHERE id = :id")
            .setParameter("id", userId)
            .getSingleResult();
  }

  private Optional<MfaFactor> porEstado(UUID userId, String estado, boolean bloquear) {
    var consulta =
        em.createQuery(
                "SELECT f FROM MfaFactor f WHERE f.userId = :usuario AND f.status = :estado",
                MfaFactor.class)
            .setParameter("usuario", userId)
            .setParameter("estado", estado)
            .setMaxResults(1);
    if (bloquear) {
      consulta.setLockMode(LockModeType.PESSIMISTIC_WRITE);
    }
    return consulta.getResultList().stream().findFirst();
  }
}
