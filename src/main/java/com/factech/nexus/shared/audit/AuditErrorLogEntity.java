package com.factech.nexus.shared.audit;

import com.factech.nexus.shared.audit.AuditEnums.ErrorType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Fila de {@code audit_error_log} (`architecture.md` §6.6.4).
 *
 * <p>El esquema declara con {@code ck_audit_error_log_status} qué estados <b>no</b> puede
 * registrar: {@code 400}, {@code 401}, {@code 403} y {@code 404}. Escribir un {@code 403} aquí por
 * descuido no produce un dato incorrecto que nadie nota, sino un {@code INSERT} que falla.
 */
@Entity
@Table(name = "audit_error_log")
public class AuditErrorLogEntity extends AuditLogEntity {

  @Column(name = "resource", nullable = false, length = 100, updatable = false)
  private String resource;

  @Column(name = "entity_id", updatable = false)
  private UUID entityId;

  @Column(name = "operation", nullable = false, length = 100, updatable = false)
  private String operation;

  @Column(name = "error_code", nullable = false, length = 50, updatable = false)
  private String errorCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "error_type", nullable = false, length = 20, updatable = false)
  private ErrorType errorType;

  @Column(name = "http_status", nullable = false, updatable = false)
  private short httpStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "severity", nullable = false, length = 20, updatable = false)
  private Severity severity;

  /** Mensaje saneado: sin trazas, SQL, rutas ni versiones (Art. VI.5). */
  @Column(name = "message", nullable = false, updatable = false)
  private String message;

  protected AuditErrorLogEntity() {}

  AuditErrorLogEntity(AuditCore core, AuditEvents.ErrorEvent evento) {
    super(core);
    this.resource = evento.resource();
    this.entityId = evento.entityId();
    this.operation = evento.operation();
    this.errorCode = evento.errorCode();
    this.errorType = evento.errorType();
    this.httpStatus = (short) evento.httpStatus();
    this.severity = evento.severity();
    this.message = evento.message();
  }

  public String getResource() {
    return resource;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public String getOperation() {
    return operation;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public ErrorType getErrorType() {
    return errorType;
  }

  public short getHttpStatus() {
    return httpStatus;
  }

  public Severity getSeverity() {
    return severity;
  }

  public String getMessage() {
    return message;
  }
}
