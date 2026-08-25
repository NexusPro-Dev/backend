package com.factech.nexus.modules.system.audit.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Parámetros de {@code GET /api/v1/audit/deletions} (`RF-SP-012`).
 *
 * <p><b>{@code reason} busca por texto, no por igualdad</b>, insensible a acentos y mayúsculas: el
 * motivo es prosa que escribió una persona, y nadie recuerda cómo la redactó. Es lo que sustituye
 * al catálogo de códigos de eliminación que `D-20` descartó — la búsqueda sobre texto libre cubre
 * la necesidad de filtrar sin obligar a nadie a prever hoy por qué se borrará algo dentro de dos
 * años.
 *
 * <p>Buscar por motivo <b>excluye por construcción las eliminaciones de asociación</b>, que no lo
 * llevan (Art. V.13). No es un efecto colateral: es lo que hace que el índice pueda ser parcial.
 */
public record ListDeletionAuditRequest(
    Integer page,
    Integer size,
    String module,
    String entity,
    UUID entityId,
    UUID actorId,
    String deletionType,
    String reason,
    OffsetDateTime from,
    OffsetDateTime to,
    UUID correlationId)
    implements AuditFilters {

  public ListDeletionAuditRequest {
    module = ListChangeAuditRequest.normalizar(module);
    entity = ListChangeAuditRequest.normalizar(entity);
    deletionType = ListChangeAuditRequest.normalizar(deletionType);
    reason = ListChangeAuditRequest.normalizar(reason);
  }
}
