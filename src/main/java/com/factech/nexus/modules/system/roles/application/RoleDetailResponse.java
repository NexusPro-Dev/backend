package com.factech.nexus.modules.system.roles.application;

import com.factech.nexus.modules.system.permissions.application.PermissionResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * El detalle de un rol en el contrato de la API (`RF-SP-003`).
 *
 * <p>Responde la pregunta «¿qué puede hacer alguien con este rol?» — y por eso lleva la lista
 * <b>completa y sin paginar</b> de los permisos que declara, ordenada por código. `architecture.md`
 * §7.4 exige paginar las colecciones y aquí se aparta a conciencia: los permisos de un rol son
 * decenas, no constituyen un recurso navegable por sí mismos, y paginarlos obligaría a un segundo
 * endpoint para responder la única pregunta del requerimiento.
 *
 * <p><b>No es {@link RoleResponse}.</b> Se estudió ampliar aquel con los dos conteos: se descarta
 * porque lo devuelven también el alta y las operaciones de edición, y ninguna necesita saber
 * cuántos usuarios tiene el rol —en el alta el número es cero por construcción—. Añadir dos
 * subconsultas a seis endpoints para que uno las use es coste sin destinatario.
 *
 * <p><b>{@code childRoleCount} es un número, nunca una lista</b>: el listado de hijos se obtiene
 * con {@code GET /api/v1/roles?parentRoleId={id}}, que ya existe y ya está paginado (`CA-SP-150`).
 * El conteo excluye los eliminados e incluye los inactivos, de modo que coincide con el {@code
 * totalElements} de esa consulta — con {@code includeDeleted=true} no coincidirá, y eso es
 * correcto.
 *
 * <p><b>No existe {@code deletedAt}</b>: un rol eliminado devuelve {@code 404}, de modo que el
 * campo sería siempre nulo. Ni {@code createdBy}: el actor no vive en la tabla de negocio (Art.
 * V.7), y quién creó o modificó el rol lo responde `RF-SP-011` sobre la auditoría de cambios.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RoleDetailResponse(
    UUID id,
    String code,
    String name,
    String description,
    String roleType,
    String status,
    boolean isSystem,
    RoleSummaryResponse parentRole,
    List<PermissionResponse> permissions,
    long childRoleCount,
    long assignedUserCount,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
