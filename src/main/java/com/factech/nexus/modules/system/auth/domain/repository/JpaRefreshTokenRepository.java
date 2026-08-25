package com.factech.nexus.modules.system.auth.domain.repository;

import com.factech.nexus.modules.system.auth.domain.models.RefreshToken;
import com.factech.nexus.modules.system.auth.domain.models.RevokedReason;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Adaptador de las sesiones renovables. */
@Repository
public class JpaRefreshTokenRepository implements RefreshTokenRepository {

  private final EntityManager em;

  public JpaRefreshTokenRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public RefreshToken save(RefreshToken token) {
    em.persist(token);
    em.flush();
    return token;
  }

  @Override
  public Optional<RefreshToken> findByHashForUpdate(String tokenHash) {
    return em
        .createQuery("SELECT t FROM RefreshToken t WHERE t.tokenHash = :hash", RefreshToken.class)
        .setParameter("hash", tokenHash)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  /**
   * Sentencia masiva y no una entidad por fila.
   *
   * <p>Al detectar un robo hay que cerrar la familia entera <b>ya</b>, y cargarla para modificarla
   * fila a fila abriría una ventana entre la detección y el cierre.
   *
   * <p>{@code revoked_at IS NULL} en la condición: una fila ya revocada conserva su motivo
   * original, que es el que explica qué pasó. Sobrescribir la rotación con la reutilización
   * borraría la pista.
   */
  @Override
  public int revokeFamily(UUID familyId, RevokedReason motivo, OffsetDateTime ahora) {
    return em.createNativeQuery(
            """
            UPDATE refresh_tokens
               SET revoked_at = :ahora, revoked_reason = :motivo
             WHERE family_id = :familia AND revoked_at IS NULL
            """)
        .setParameter("ahora", ahora)
        .setParameter("motivo", motivo.name())
        .setParameter("familia", familyId)
        .executeUpdate();
  }

  @Override
  public int revokeAllActive(UUID userId, RevokedReason motivo, OffsetDateTime ahora) {
    return em.createNativeQuery(
            """
            UPDATE refresh_tokens
               SET revoked_at = :ahora, revoked_reason = :motivo
             WHERE user_id = :usuario AND revoked_at IS NULL
            """)
        .setParameter("ahora", ahora)
        .setParameter("motivo", motivo.name())
        .setParameter("usuario", userId)
        .executeUpdate();
  }
}
