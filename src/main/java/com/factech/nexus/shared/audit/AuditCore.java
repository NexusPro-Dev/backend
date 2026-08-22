package com.factech.nexus.shared.audit;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Valores del núcleo común ya resueltos, listos para escribirse.
 *
 * <p>Lo arma {@link JpaAuditWriter} leyendo el contexto de la petición y la identidad autenticada;
 * quien emite un evento nunca lo construye. Las tres columnas de origen viajan juntas y en nulo
 * cuando la operación no vino de la red —migraciones, tareas programadas—, que es lo que el {@code
 * CHECK} de origen del esquema exige y lo que hace que una fila sin IP signifique inequívocamente
 * «no vino de la red» y nunca «se olvidó registrarla» (Art. V.15).
 */
record AuditCore(
    UUID id,
    OffsetDateTime occurredAt,
    UUID actorId,
    UUID correlationId,
    InetAddress ipAddress,
    String userAgent) {}
