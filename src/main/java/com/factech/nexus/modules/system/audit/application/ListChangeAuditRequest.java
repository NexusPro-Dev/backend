package com.factech.nexus.modules.system.audit.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Parámetros de {@code GET /api/v1/audit/changes} (`RF-SP-011`).
 *
 * <p><b>No hay {@code sort}.</b> El orden es parte del significado de este recurso: `spec.md` §8
 * exige del más reciente al más antiguo, y un registro cronológico que pudiera ordenarse por módulo
 * o por entidad respondería otra pregunta. Ahorra además la lista blanca de ordenamiento que el
 * catálogo de roles necesitó, y con ella su superficie de validación.
 *
 * <p><b>Ningún filtro es obligatorio, ni siquiera el rango de fechas.</b> Limitarlo obligaría a
 * trocear justo la consulta que más valor tiene —la línea de tiempo completa de un registro—; que
 * se sostenga es cuestión de índices y del conteo acotado, no de negocio (`spec.md` §14).
 *
 * <p><b>{@code entityId} y {@code actorId} no se validan contra nada.</b> La auditoría conserva
 * eventos de registros que ya no existen —es su razón de ser—, de modo que exigir que la referencia
 * exista rompería justo la consulta que da sentido al registro.
 */
public record ListChangeAuditRequest(
    Integer page,
    Integer size,
    String module,
    String entity,
    UUID entityId,
    UUID actorId,
    String action,
    OffsetDateTime from,
    OffsetDateTime to,
    UUID correlationId)
    implements AuditFilters {

  public ListChangeAuditRequest {
    module = normalizar(module);
    entity = normalizar(entity);
    action = normalizar(action);
  }

  static String normalizar(String valor) {
    return valor == null || valor.isBlank() ? null : valor.trim();
  }
}
