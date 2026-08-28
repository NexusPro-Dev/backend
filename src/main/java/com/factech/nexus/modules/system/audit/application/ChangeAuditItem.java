package com.factech.nexus.modules.system.audit.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un evento de creación o edición (`RF-SP-011`).
 *
 * <p><b>{@code changes} viaja como objeto JSON, no como cadena.</b> La columna es {@code jsonb};
 * serializarla como texto obligaría al cliente a un segundo análisis y a tratar como opaco algo que
 * no lo es. <b>Este endpoint no la interpreta</b>: en un {@code CREATE} es el estado inicial y en
 * un {@code UPDATE} el diff con {@code before} y {@code after}, y se devuelve tal como se escribió.
 * Eso es lo que satisface `CA-SP-082` y `CA-SP-083` sin una línea de código que distinga ambos
 * casos.
 *
 * <p><b>{@code correlationId} e {@code ipAddress} son nulos a la vez o no lo es ninguno</b>
 * (`CA-SP-086`). No hace falta código que lo garantice: lo impone {@code
 * ck_audit_change_log_origen} en el esquema. Lo que este modelo sí hace es <b>no omitir los
 * campos</b> cuando son nulos, porque un campo ausente es indistinguible de uno que el cliente no
 * conoce.
 *
 * <p><b>{@code actorId} nulo significa «lo hizo el sistema»</b> —una migración, una tarea
 * programada—, no «se perdió el dato» (Art. V.15).
 *
 * <p><b>{@code actor} se añadió el 28-08-2026</b>, y este documento decía hasta entonces que el
 * nombre <b>no</b> se devolvía. El argumento de entonces no se descarta, se acota: sigue siendo
 * cierto que un nombre es una foto del momento en que se consulta, y por eso {@link AuditActor}
 * declara cuál de sus dos campos es evidencia y cuál es comodidad. El {@code actorId} sigue siendo
 * el que manda, y la adición es aditiva — no rompe a ningún cliente, tal como aquella nota preveía.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ChangeAuditItem(
    UUID id,
    OffsetDateTime occurredAt,
    UUID actorId,
    AuditActor actor,
    String module,
    String entity,
    UUID entityId,
    String action,
    JsonNode changes,
    UUID correlationId,
    String ipAddress,
    String userAgent) {}
