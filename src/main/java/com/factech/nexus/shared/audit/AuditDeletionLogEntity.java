package com.factech.nexus.shared.audit;

import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Fila de {@code audit_deletion_log} (`architecture.md` §6.6.3). */
@Entity
@Table(name = "audit_deletion_log")
public class AuditDeletionLogEntity extends AuditLogEntity {

  @Column(name = "module", nullable = false, length = 10, updatable = false)
  private String module;

  @Column(name = "entity", nullable = false, length = 50, updatable = false)
  private String entity;

  @Column(name = "entity_id", nullable = false, updatable = false)
  private UUID entityId;

  @Enumerated(EnumType.STRING)
  @Column(name = "deletion_type", nullable = false, length = 20, updatable = false)
  private DeletionType deletionType;

  /**
   * Obligatorio salvo en {@code ASSOCIATION} (Art. V.13). El esquema exige contenido, no longitud.
   */
  @Column(name = "reason", updatable = false)
  private String reason;

  /** Estado completo al eliminarse. Pasa por el mismo enmascarador que el resto (Art. IV.8). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "snapshot", nullable = false, updatable = false)
  private Map<String, Object> snapshot;

  protected AuditDeletionLogEntity() {}

  AuditDeletionLogEntity(AuditCore core, AuditEvents.DeletionEvent evento) {
    super(core);
    this.module = evento.module();
    this.entity = evento.entity();
    this.entityId = evento.entityId();
    this.deletionType = evento.deletionType();
    this.reason = evento.reason();
    this.snapshot = evento.snapshot();
  }

  public String getModule() {
    return module;
  }

  public String getEntity() {
    return entity;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public DeletionType getDeletionType() {
    return deletionType;
  }

  public String getReason() {
    return reason;
  }

  public Map<String, Object> getSnapshot() {
    return snapshot;
  }
}
