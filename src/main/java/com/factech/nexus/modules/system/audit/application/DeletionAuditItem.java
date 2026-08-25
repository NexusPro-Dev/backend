package com.factech.nexus.modules.system.audit.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un evento de eliminación (`RF-SP-012`).
 *
 * <p><b>{@code snapshot} es lo que hace útil a este registro.</b> Sin él, la fila diría que un
 * identificador fue eliminado y nadie recordaría qué era: es el estado completo del registro en el
 * momento de borrarse, y por eso los permisos de un rol viajan ahí <b>por código</b> y no por
 * identificador — quien lea esto dentro de dos años no debería tener que resolver referencias
 * contra un catálogo que pudo cambiar.
 *
 * <p><b>{@code reason} es nulo en las eliminaciones de asociación</b>, y eso es correcto y no un
 * dato faltante: retirar un permiso de un rol no exige motivo, porque lo que desaparece es una
 * asociación y no una entidad de negocio (Art. V.13, `FA-001`).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DeletionAuditItem(
    UUID id,
    OffsetDateTime occurredAt,
    UUID actorId,
    String module,
    String entity,
    UUID entityId,
    String deletionType,
    String reason,
    JsonNode snapshot,
    UUID correlationId,
    String ipAddress,
    String userAgent) {}
