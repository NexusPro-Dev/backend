package com.factech.nexus.modules.system.audit.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Parámetros de {@code GET /api/v1/audit/errors} (`RF-SP-013`).
 *
 * <p><b>{@code correlationId} es el filtro que justifica este endpoint.</b> Alguien reporta un
 * error citando el identificador que la respuesta le devolvió, y con él se llega al fallo concreto
 * y a la traza técnica que lo acompaña (`FA-001`). Todo lo demás son formas de mirar el conjunto.
 *
 * <p><b>{@code errorCode} es el otro filtro con cardinalidad real</b> —«cuántas veces falló esto»—;
 * {@code errorType} tiene tres valores y {@code severity} dos, de modo que sirven para afinar y no
 * para acotar.
 */
public record ListErrorAuditRequest(
    Integer page,
    Integer size,
    String errorType,
    String severity,
    String errorCode,
    String resource,
    UUID actorId,
    OffsetDateTime from,
    OffsetDateTime to,
    UUID correlationId)
    implements AuditFilters {

  public ListErrorAuditRequest {
    errorType = ListChangeAuditRequest.normalizar(errorType);
    severity = ListChangeAuditRequest.normalizar(severity);
    errorCode = ListChangeAuditRequest.normalizar(errorCode);
    resource = ListChangeAuditRequest.normalizar(resource);
  }
}
