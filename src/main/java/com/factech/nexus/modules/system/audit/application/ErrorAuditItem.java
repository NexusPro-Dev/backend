package com.factech.nexus.modules.system.audit.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un fallo o un rechazo por regla de negocio (`RF-SP-013`).
 *
 * <p><b>{@code message} viene ya saneado desde que se escribió</b>: sin trazas, sin SQL, sin rutas
 * y sin versiones (Art. VI.5). Este endpoint no lo vuelve a limpiar, y no debería: si algo hubiera
 * que sanear aquí, significaría que la fila almacenada ya contiene lo que no debía.
 *
 * <p><b>Qué NO aparece en este registro, y es la frontera que más importa:</b> la denegación de
 * autorización ({@code 403}) va a la auditoría de seguridad, porque no es un fallo del sistema sino
 * el sistema funcionando (`CA-SP-108`). Tampoco la validación de formato, el {@code 401} ni el
 * {@code 404}. No hace falta filtrarlo aquí — lo impone {@code ck_audit_error_log_status} al
 * escribir.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ErrorAuditItem(
    UUID id,
    OffsetDateTime occurredAt,
    UUID actorId,
    AuditActor actor,
    String resource,
    UUID entityId,
    String operation,
    String errorCode,
    String errorType,
    int httpStatus,
    String severity,
    String message,
    UUID correlationId,
    String ipAddress,
    String userAgent) {}
