package com.factech.nexus.shared.audit;

import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Fila de {@code audit_security_log} (`security.md` §8.2). */
@Entity
@Table(name = "audit_security_log")
public class AuditSecurityLogEntity extends AuditLogEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 50, updatable = false)
  private SecurityEventType eventType;

  @Enumerated(EnumType.STRING)
  @Column(name = "severity", nullable = false, length = 20, updatable = false)
  private Severity severity;

  @Enumerated(EnumType.STRING)
  @Column(name = "outcome", nullable = false, length = 10, updatable = false)
  private Outcome outcome;

  /** Usuario <b>objeto</b> del evento, distinto del actor (`security.md` §8.2). */
  @Column(name = "target_user_id", updatable = false)
  private UUID targetUserId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "detail", updatable = false)
  private Map<String, Object> detail;

  protected AuditSecurityLogEntity() {}

  AuditSecurityLogEntity(AuditCore core, AuditEvents.SecurityEvent evento) {
    super(core);
    this.eventType = evento.eventType();
    this.severity = evento.severity();
    this.outcome = evento.outcome();
    this.targetUserId = evento.targetUserId();
    this.detail = evento.detail();
  }

  public SecurityEventType getEventType() {
    return eventType;
  }

  public Severity getSeverity() {
    return severity;
  }

  public Outcome getOutcome() {
    return outcome;
  }

  public UUID getTargetUserId() {
    return targetUserId;
  }

  public Map<String, Object> getDetail() {
    return detail;
  }
}
