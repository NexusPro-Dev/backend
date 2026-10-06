package com.factech.nexus.modules.system.auth.domain.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * El authenticator de una persona (`RF-SP-071`, `RN-SP-058`; `requirements/sp.md` §10.22).
 *
 * <p><b>Nace pendiente y solo protege al confirmarse.</b> Un secreto que nadie probó no se exige al
 * entrar: la persona puede no haber terminado de escanear. Uno activo y uno pendiente por persona,
 * como mucho, y eso lo dice el esquema con dos índices únicos parciales, no esta clase.
 *
 * <p><b>Las filas retiradas no se borran</b>: cuándo tuvo alguien segundo factor y por qué dejó de
 * tenerlo es una pregunta de seguridad. {@link Retiro} dice cuál de los cuatro caminos lo retiró.
 *
 * <p>El secreto se guarda <b>cifrado</b> y no resumido —hay que recalcular el código—, y esta clase
 * solo conoce el cifrado: el valor en claro lo maneja {@code MfaSecrets} y nunca se guarda.
 */
@Entity
@Table(name = "user_mfa_factors")
public class MfaFactor {

  /** Por qué dejó de valer un factor. Los cuatro de `ck_user_mfa_factors_retired_reason`. */
  public enum Retiro {
    /** Otro teléfono lo sustituyó (`RF-SP-071`). */
    REEMPLAZADO,
    /** Su titular lo retiró (`RF-SP-075`). */
    DESACTIVADO,
    /** Un administrador lo retiró (`RF-SP-076`). */
    RESTABLECIDO,
    /** Un pendiente que nadie confirmó a tiempo, o que otro inicio sustituyó. */
    CADUCADO
  }

  public static final String PENDIENTE = "PENDIENTE";
  public static final String ACTIVO = "ACTIVO";
  public static final String RETIRADO = "RETIRADO";

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "factor_type", nullable = false, updatable = false, length = 20)
  private String factorType;

  @Column(name = "secret_ciphertext", nullable = false, updatable = false)
  private String secretCiphertext;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "last_used_step")
  private Long lastUsedStep;

  @Column(name = "pending_expires_at")
  private OffsetDateTime pendingExpiresAt;

  @Column(name = "confirmed_at")
  private OffsetDateTime confirmedAt;

  @Column(name = "retired_at")
  private OffsetDateTime retiredAt;

  @Column(name = "retired_reason", length = 30)
  private String retiredReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** Exigido por JPA. */
  protected MfaFactor() {}

  /** Un factor pendiente nuevo, con el secreto ya cifrado. */
  public static MfaFactor iniciar(
      UUID id, UUID userId, String secretCiphertext, OffsetDateTime caduca, OffsetDateTime ahora) {
    MfaFactor factor = new MfaFactor();
    factor.id = id;
    factor.userId = userId;
    factor.factorType = "TOTP";
    factor.secretCiphertext = secretCiphertext;
    factor.status = PENDIENTE;
    factor.pendingExpiresAt = caduca;
    factor.createdAt = ahora;
    factor.updatedAt = ahora;
    return factor;
  }

  /** ¿Un pendiente que todavía puede confirmarse? */
  public boolean pendienteVigente(OffsetDateTime ahora) {
    return PENDIENTE.equals(status) && pendingExpiresAt.isAfter(ahora);
  }

  public boolean activo() {
    return ACTIVO.equals(status);
  }

  /** Lo vuelve activo y anota el periodo del código con que se confirmó (`RN-SP-060`). */
  public void confirmar(long periodo, OffsetDateTime ahora) {
    this.status = ACTIVO;
    this.confirmedAt = ahora;
    this.pendingExpiresAt = null;
    this.lastUsedStep = periodo;
    this.updatedAt = ahora;
  }

  /** Anota el periodo del último código aceptado: un código no sirve dos veces (`RN-SP-060`). */
  public void usarPeriodo(long periodo, OffsetDateTime ahora) {
    this.lastUsedStep = periodo;
    this.updatedAt = ahora;
  }

  public void retirar(Retiro motivo, OffsetDateTime ahora) {
    this.status = RETIRADO;
    this.retiredAt = ahora;
    this.retiredReason = motivo.name();
    this.pendingExpiresAt = null;
    this.updatedAt = ahora;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getSecretCiphertext() {
    return secretCiphertext;
  }

  public String getStatus() {
    return status;
  }

  public Long getLastUsedStep() {
    return lastUsedStep;
  }

  public OffsetDateTime getPendingExpiresAt() {
    return pendingExpiresAt;
  }

  public OffsetDateTime getConfirmedAt() {
    return confirmedAt;
  }

  public String getRetiredReason() {
    return retiredReason;
  }
}
