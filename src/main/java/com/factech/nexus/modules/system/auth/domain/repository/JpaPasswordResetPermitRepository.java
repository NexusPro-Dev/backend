package com.factech.nexus.modules.system.auth.domain.repository;

import com.factech.nexus.modules.system.auth.domain.models.PasswordResetPermit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Adaptador de los permisos de recuperación. */
@Repository
public class JpaPasswordResetPermitRepository implements PasswordResetPermitRepository {

  private final EntityManager em;

  public JpaPasswordResetPermitRepository(EntityManager em) {
    this.em = em;
  }

  /**
   * Sentencia masiva y no una entidad cargada.
   *
   * <p>Lo que hace falta es que <b>no quede</b> ninguno vivo antes de insertar el siguiente, y
   * cargarlo para modificarlo abriría una ventana entre la lectura y la escritura justo donde el
   * índice único parcial va a chocar.
   */
  @Override
  public int sustituirVigente(UUID userId, OffsetDateTime ahora) {
    int sustituidos =
        em.createNativeQuery(
                """
                UPDATE password_reset_permits
                   SET superseded_at = :ahora
                 WHERE user_id = :usuario
                   AND consumed_at IS NULL
                   AND superseded_at IS NULL
                """)
            .setParameter("ahora", ahora)
            .setParameter("usuario", userId)
            .executeUpdate();

    // Se vacía el contexto antes de insertar: sin esto, el `INSERT` del permiso
    // nuevo podría llegar a la base ANTES que este `UPDATE` y chocar con
    // `uq_password_reset_permits_vigente`. El orden importa y no se deja al
    // criterio del proveedor de persistencia.
    em.flush();
    return sustituidos;
  }

  @Override
  public void guardar(PasswordResetPermit permiso) {
    em.persist(permiso);
    em.flush();
  }

  @Override
  public Optional<PasswordResetPermit> buscarPorHashParaActualizar(String permitHash) {
    if (permitHash == null || permitHash.isBlank()) {
      return Optional.empty();
    }
    return em
        .createQuery(
            "SELECT p FROM PasswordResetPermit p WHERE p.permitHash = :hash",
            PasswordResetPermit.class)
        .setParameter("hash", permitHash)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }
}
