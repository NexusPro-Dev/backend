package com.factech.nexus.modules.system.users.application;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/users/{id}/roles/revocations} (`RF-SP-031`).
 *
 * <p><b>No lleva motivo</b>, y no es un olvido: es la eliminación de una asociación, que es la
 * excepción del Art. V.13 que `RN-SP-005` ya aplicó a los permisos de un rol. Pedirlo aquí
 * obligaría a inventar un vocabulario de motivos que nadie consultaría.
 *
 * <p><b>Tampoco se comprueba que los roles existan en el catálogo.</b> Es la asimetría con
 * `RF-SP-030` que más fácilmente se implementa de más: retirar un rol eliminado del catálogo es
 * legítimo, porque la asignación sigue ahí y debe poder soltarse. Un rol que la persona no tiene no
 * es un error, es `FA-001`.
 */
public record RevokeRolesRequest(
    @NotEmpty(message = "VAL-002: Debe indicar al menos un rol.")
        @Size(max = 100, message = "VAL-005: No se admiten más de 100 roles en una sola petición.")
        List<UUID> roleIds) {

  public RevokeRolesRequest {
    roleIds =
        roleIds == null
            ? null
            : List.copyOf(new LinkedHashSet<>(roleIds.stream().filter(Objects::nonNull).toList()));
  }
}
