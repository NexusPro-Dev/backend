package com.factech.nexus.modules.system.roles.application;

import com.factech.nexus.modules.system.permissions.application.PermissionItem;
import com.factech.nexus.modules.system.permissions.application.PermissionResponse;
import com.factech.nexus.modules.system.roles.domain.models.Role;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Un rol en el contrato de la API (`RF-SP-001` · `T-16`).
 *
 * <p><b>No incluye {@code createdBy} ni equivalente.</b> El actor no vive en la tabla de negocio
 * (Art. V.7): quién creó el rol se responde consultando {@code audit_change_log} con `RF-SP-011`.
 * Añadir la columna aquí duplicaría un dato que la auditoría ya guarda y que puede cambiar de
 * significado —el actor puede eliminarse— sin que la fila del rol se entere.
 *
 * <p><b>{@code @JsonInclude(ALWAYS)}, por la misma razón que en {@link PermissionResponse}:</b>
 * {@code application.yml} declara {@code non_null} para todo el sistema, y sin esta anotación un
 * rol sin descripción llegaría al cliente sin la propiedad en lugar de con {@code null}. Un cliente
 * que recorra las claves del objeto no debe encontrarse con que unos roles tienen once y otros
 * diez.
 *
 * <p><b>Los instantes se publican en UTC.</b> El controlador JDBC devuelve la marca en el desfase
 * de la máquina que ejecuta la aplicación; sin normalizar, el mismo rol se vería con un desfase
 * distinto según dónde corriera el servidor.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RoleResponse(
    UUID id,
    String code,
    String name,
    String description,
    String roleType,
    String status,
    boolean isSystem,
    RoleSummaryResponse parentRole,
    List<PermissionResponse> permissions,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static RoleResponse from(Role rol, Role padre, Collection<PermissionItem> permisos) {
    return new RoleResponse(
        rol.getId(),
        rol.getCode().value(),
        rol.getName(),
        rol.getDescription(),
        rol.getRoleType().name(),
        rol.getStatus().name(),
        rol.isSystem(),
        padre == null ? null : RoleSummaryResponse.from(padre),
        permisos.stream().map(PermissionResponse::from).toList(),
        enUtc(rol.getCreatedAt()),
        enUtc(rol.getUpdatedAt()));
  }

  private static OffsetDateTime enUtc(OffsetDateTime instante) {
    return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
  }
}
