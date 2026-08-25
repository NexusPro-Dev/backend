package com.factech.nexus.modules.system.audit.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Parámetros de {@code GET /api/v1/audit/security} (`RF-SP-014`).
 *
 * <p><b>{@code targetUserId} es el filtro de la investigación de una cuenta</b> (`FA-001`): quién
 * entró, a quién bloquearon, a quién le cambiaron los roles o el estado. Con un rango de fechas al
 * lado, es la consulta para la que existe este registro.
 *
 * <p><b>Los intentos de acceso fallidos NO llevan usuario afectado</b>, y por eso no aparecen al
 * filtrar por él: `CA-SP-109` lo prohíbe, porque la presencia de ese campo delataría que la cuenta
 * existe. Se localizan por tipo de evento y rango, leyendo el identificador intentado en el
 * detalle.
 *
 * <p><b>{@code ipAddress} se compara por igualdad</b> y no por prefijo de red: la columna es {@code
 * inet} y quien investiga un origen concreto lo tiene entero. Un filtro por subred es una necesidad
 * distinta y tendría que declararse como tal.
 */
public record ListSecurityAuditRequest(
    Integer page,
    Integer size,
    String eventType,
    String severity,
    String outcome,
    UUID actorId,
    UUID targetUserId,
    String ipAddress,
    OffsetDateTime from,
    OffsetDateTime to)
    implements AuditFilters {

  public ListSecurityAuditRequest {
    eventType = ListChangeAuditRequest.normalizar(eventType);
    severity = ListChangeAuditRequest.normalizar(severity);
    outcome = ListChangeAuditRequest.normalizar(outcome);
    ipAddress = ListChangeAuditRequest.normalizar(ipAddress);
  }

  /**
   * Este registro no se filtra por correlación.
   *
   * <p>`spec.md` §6.1 no lo declara, y no es un olvido: el enlace entre registros se recorre desde
   * los otros tres, donde la pregunta es «qué más pasó en esta petición». Aquí la pregunta es quién
   * hizo qué sobre quién.
   */
  @Override
  public UUID correlationId() {
    return null;
  }
}
