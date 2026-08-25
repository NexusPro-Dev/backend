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
 * <p><b>No se devuelve el nombre del actor.</b> El valor probatorio del registro está en el
 * identificador, que no cambia nunca; un nombre es una foto del momento en que se consulta y no del
 * momento en que ocurrió el evento, y en una auditoría esa diferencia importa. Añadirlo más
 * adelante es aditivo y no rompe a ningún cliente.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ChangeAuditItem(
    UUID id,
    OffsetDateTime occurredAt,
    UUID actorId,
    String module,
    String entity,
    UUID entityId,
    String action,
    JsonNode changes,
    UUID correlationId,
    String ipAddress,
    String userAgent) {}
