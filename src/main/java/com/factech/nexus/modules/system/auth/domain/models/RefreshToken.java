package com.factech.nexus.modules.system.auth.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una sesión renovable (`RF-SP-034`, `RF-SP-035`, `RF-SP-036`).
 *
 * <p><b>Del token solo se guarda su hash.</b> Quien lea esta tabla no puede usar lo que ve, y el
 * valor no existe en el servidor más allá del instante en que se entrega.
 *
 * <p><b>{@code familyStartedAt} mide desde el INICIO DE SESIÓN, no desde el último refresco.</b> Es
 * lo único que impide que una sesión rotada con disciplina no caduque nunca: sin ella, quien
 * refresca cada catorce minutos mantiene una sesión viva indefinidamente.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false, length = 255, updatable = false)
  private String tokenHash;

  @Column(name = "family_id", nullable = false, updatable = false)
  private UUID familyId;

  @Column(name = "family_started_at", nullable = false, updatable = false)
  private OffsetDateTime familyStartedAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "revoked_reason", length = 20)
  private RevokedReason revokedReason;

  @Column(name = "replaced_by_id")
  private UUID replacedById;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  /** Exigido por JPA. */
  protected RefreshToken() {}

  /**
   * Abre una sesión nueva.
   *
   * <p>La familia empieza aquí: {@code familyId} es el propio identificador y {@code
   * familyStartedAt} el instante del inicio de sesión.
   */
  public static RefreshToken abrirSesion(
      UUID id, UUID userId, String tokenHash, OffsetDateTime ahora, OffsetDateTime expira) {
    RefreshToken token = nuevo(id, userId, tokenHash, ahora, expira);
    token.familyId = id;
    token.familyStartedAt = ahora;
    return token;
  }

  /** Continúa una familia existente, conservando su instante de inicio. */
  public static RefreshToken rotar(
      UUID id,
      RefreshToken anterior,
      String tokenHash,
      OffsetDateTime ahora,
      OffsetDateTime expira) {
    RefreshToken token = nuevo(id, anterior.userId, tokenHash, ahora, expira);
    token.familyId = anterior.familyId;
    token.familyStartedAt = anterior.familyStartedAt;
    return token;
  }

  private static RefreshToken nuevo(
      UUID id, UUID userId, String tokenHash, OffsetDateTime ahora, OffsetDateTime expira) {
    RefreshToken token = new RefreshToken();
    token.id = id;
    token.userId = userId;
    token.tokenHash = tokenHash;
    token.expiresAt = expira;
    token.createdAt = ahora;
    return token;
  }

  /** Revoca con su motivo. Los dos van juntos o ninguno: el esquema lo exige. */
  public void revocar(RevokedReason motivo, OffsetDateTime ahora) {
    if (revokedAt == null) {
      revokedAt = ahora;
      revokedReason = motivo;
    }
  }

  public void sustituidoPor(UUID nuevo) {
    replacedById = nuevo;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getFamilyId() {
    return familyId;
  }

  public OffsetDateTime getFamilyStartedAt() {
    return familyStartedAt;
  }

  public boolean estaRevocado() {
    return revokedAt != null;
  }

  public RevokedReason getRevokedReason() {
    return revokedReason;
  }

  public boolean haExpirado(OffsetDateTime ahora) {
    return !expiresAt.isAfter(ahora);
  }
}
