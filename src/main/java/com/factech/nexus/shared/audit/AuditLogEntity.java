package com.factech.nexus.shared.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Núcleo común de los cuatro registros (`architecture.md` §6.6.1, `RF-SP-001` · `T-07`).
 *
 * <p>Es lo que permite consultarlas en conjunto —{@code v_audit_timeline}— y correlacionarlas con
 * {@code request_log}. Se modela como superclase mapeada y no como tabla: las cuatro tablas son
 * independientes y ninguna hereda de otra en la base de datos.
 *
 * <p><b>Sin {@code created_at}, {@code updated_at} ni {@code deleted_at}.</b> No son tablas de
 * negocio: son append-only y {@code occurred_at} es su única marca temporal. Por el mismo motivo
 * ninguna entidad expone setters más allá de lo que el constructor fija.
 *
 * <p><b>{@code actor_id} no lleva clave foránea a {@code users}</b>, y es deliberado: una clave
 * foránea impediría conservar el evento si la persona se elimina, que es justo lo contrario de lo
 * que se busca. La integridad referencial se sacrifica aquí de forma consciente.
 */
@MappedSuperclass
public abstract class AuditLogEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private OffsetDateTime occurredAt;

  @Column(name = "actor_id", updatable = false)
  private UUID actorId;

  @Column(name = "correlation_id", updatable = false)
  private UUID correlationId;

  /**
   * {@code inet} es el tipo nativo de PostgreSQL para direcciones: valida el formato, admite IPv4 e
   * IPv6 sin decidir longitudes y permite consultar por rango de red. Sobre un {@code varchar} esa
   * consulta obliga a recorrer la tabla entera.
   */
  @JdbcTypeCode(SqlTypes.INET)
  @Column(name = "ip_address", updatable = false)
  private InetAddress ipAddress;

  @Column(name = "user_agent", updatable = false)
  private String userAgent;

  protected AuditLogEntity() {}

  protected AuditLogEntity(AuditCore core) {
    this.id = core.id();
    this.occurredAt = core.occurredAt();
    this.actorId = core.actorId();
    this.correlationId = core.correlationId();
    this.ipAddress = core.ipAddress();
    this.userAgent = core.userAgent();
  }

  public UUID getId() {
    return id;
  }

  public OffsetDateTime getOccurredAt() {
    return occurredAt;
  }

  public UUID getActorId() {
    return actorId;
  }

  public UUID getCorrelationId() {
    return correlationId;
  }

  public InetAddress getIpAddress() {
    return ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }
}
