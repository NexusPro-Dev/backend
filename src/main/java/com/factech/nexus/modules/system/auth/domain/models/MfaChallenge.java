package com.factech.nexus.modules.system.auth.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * El paso entre la contraseña y el código (`RF-SP-072`, `RN-SP-059`; `requirements/sp.md` §10.24).
 *
 * <p><b>La forma de {@link PasswordResetPermit}</b>: solo el resumen, caducidad evaluada al
 * consultar y ningún proceso que lo limpie. <b>Cinco minutos y cinco intentos</b>: al quinto fallo
 * muere y hay que volver a la contraseña. Un desafío nuevo <b>no</b> invalida los anteriores: dos
 * pestañas que inician sesión a la vez son un caso legítimo.
 */
@Entity
@Table(name = "mfa_challenges")
public class MfaChallenge {

  public static final int INTENTOS = 5;

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "challenge_hash", nullable = false, updatable = false, length = 255)
  private String challengeHash;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "failed_attempts", nullable = false)
  private short failedAttempts;

  @Column(name = "consumed_at")
  private OffsetDateTime consumedAt;

  @JdbcTypeCode(SqlTypes.INET)
  @Column(name = "requested_ip", updatable = false)
  private InetAddress requestedIp;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  /** Exigido por JPA. */
  protected MfaChallenge() {}

  public static MfaChallenge emitir(
      UUID id,
      UUID userId,
      String challengeHash,
      OffsetDateTime expiresAt,
      InetAddress requestedIp,
      OffsetDateTime ahora) {
    MfaChallenge desafio = new MfaChallenge();
    desafio.id = id;
    desafio.userId = userId;
    desafio.challengeHash = challengeHash;
    desafio.expiresAt = expiresAt;
    desafio.requestedIp = requestedIp;
    desafio.createdAt = ahora;
    return desafio;
  }

  /**
   * ¿Sirve todavía? Las cuatro razones por las que no —caducado, consumido, agotado, inexistente—
   * <b>no se distinguen hacia fuera</b> (`EX-001`): la distinción vive aquí.
   */
  public boolean vigente(OffsetDateTime ahora) {
    return consumedAt == null && failedAttempts < INTENTOS && expiresAt.isAfter(ahora);
  }

  public void fallar() {
    failedAttempts++;
  }

  public void consumir(OffsetDateTime ahora) {
    consumedAt = ahora;
  }

  public UUID getUserId() {
    return userId;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }
}
