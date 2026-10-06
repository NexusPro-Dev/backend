package com.factech.nexus.modules.system.auth.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un código de recuperación (`RN-SP-061`; `requirements/sp.md` §10.23).
 *
 * <p><b>Del código solo se guarda su resumen Argon2id</b>, como de la contraseña: vale lo mismo que
 * el teléfono. <b>Cuelga del factor y no de la persona</b>: retirar el factor lo deja sin efecto
 * sin tocarlo, porque un código solo vale si su factor está activo.
 */
@Entity
@Table(name = "mfa_recovery_codes")
public class RecoveryCode {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "factor_id", nullable = false, updatable = false)
  private UUID factorId;

  @Column(name = "code_hash", nullable = false, updatable = false, length = 255)
  private String codeHash;

  @Column(name = "used_at")
  private OffsetDateTime usedAt;

  @Column(name = "superseded_at")
  private OffsetDateTime supersededAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  /** Exigido por JPA. */
  protected RecoveryCode() {}

  public static RecoveryCode emitir(UUID id, UUID factorId, String codeHash, OffsetDateTime ahora) {
    RecoveryCode codigo = new RecoveryCode();
    codigo.id = id;
    codigo.factorId = factorId;
    codigo.codeHash = codeHash;
    codigo.createdAt = ahora;
    return codigo;
  }

  public void usar(OffsetDateTime ahora) {
    this.usedAt = ahora;
  }

  public UUID getId() {
    return id;
  }

  public UUID getFactorId() {
    return factorId;
  }

  public String getCodeHash() {
    return codeHash;
  }

  public OffsetDateTime getUsedAt() {
    return usedAt;
  }
}
