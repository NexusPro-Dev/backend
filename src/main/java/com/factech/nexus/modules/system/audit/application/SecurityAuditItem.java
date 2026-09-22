package com.factech.nexus.modules.system.audit.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un evento de control de acceso (`RF-SP-014`).
 *
 * <p><b>{@code targetUserId} es nulo en los intentos de acceso fallidos</b>, y no por descuido:
 * `CA-SP-109` lo prohíbe, porque un identificador afectado en un intento fallido delataría que la
 * cuenta existe. El identificador que se intentó viaja en {@code detail}, donde es un dato del
 * intento y no una afirmación sobre quién existe.
 *
 * <p><b>{@code detail} no contiene credenciales en ninguna forma</b> (`CA-SP-106`) — ni
 * contraseñas, ni resúmenes, ni tokens—, y eso se garantiza al escribir y no al leer: este endpoint
 * devuelve la columna tal cual. Si alguna vez apareciera algo ahí, el defecto estaría en quien lo
 * escribió.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SecurityAuditItem(
    UUID id,
    OffsetDateTime occurredAt,
    UUID actorId,
    AuditActor actor,
    String eventType,
    String severity,
    String outcome,
    UUID targetUserId,
    AuditActor targetUser,
    JsonNode detail,
    UUID correlationId,
    String ipAddress,
    String userAgent) {}
