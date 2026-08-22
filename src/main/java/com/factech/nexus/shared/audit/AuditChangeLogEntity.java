package com.factech.nexus.shared.audit;

import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Fila de {@code audit_change_log} (`architecture.md` §6.6.2). */
@Entity
@Table(name = "audit_change_log")
public class AuditChangeLogEntity extends AuditLogEntity {

  @Column(name = "module", nullable = false, length = 10, updatable = false)
  private String module;

  @Column(name = "entity", nullable = false, length = 50, updatable = false)
  private String entity;

  @Column(name = "entity_id", nullable = false, updatable = false)
  private UUID entityId;

  /**
   * {@code STRING} y no {@code ORDINAL}: la columna es {@code varchar} con un {@code CHECK} sobre
   * el dominio cerrado, y un ordinal guardaría un número que ese {@code CHECK} rechazaría.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false, length = 20, updatable = false)
  private ChangeAction action;

  /** En {@code CREATE}, el estado inicial completo; en {@code UPDATE}, solo lo que cambió. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "changes", nullable = false, updatable = false)
  private Map<String, Object> changes;

  protected AuditChangeLogEntity() {}

  AuditChangeLogEntity(AuditCore core, AuditEvents.ChangeEvent evento) {
    super(core);
    this.module = evento.module();
    this.entity = evento.entity();
    this.entityId = evento.entityId();
    this.action = evento.action();
    this.changes = evento.changes();
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

  public ChangeAction getAction() {
    return action;
  }

  public Map<String, Object> getChanges() {
    return changes;
  }
}
