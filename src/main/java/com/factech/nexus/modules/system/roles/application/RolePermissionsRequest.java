package com.factech.nexus.modules.system.roles.application;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/**
 * Cuerpo de las dos operaciones sobre los permisos de un rol (`RF-SP-005`, `RF-SP-006`).
 *
 * <p><b>Un solo tipo para agregar y para retirar</b>, y el mismo límite en las dos: `RF-SP-006` §13
 * lo dice sin rodeos —dos límites distintos sobre el mismo recurso serían una trampa—. Lo que
 * distingue las operaciones es la ruta, no la forma del cuerpo.
 *
 * <p><b>{@code Set} y no {@code List}</b>: los duplicados de la petición se normalizan a una sola
 * ocurrencia (`spec.md` §13) sin que nadie tenga que recordarlo, porque el tipo no admite dos.
 *
 * <p><b>El cien no es un número redondo elegido al azar</b>: es el tope que `VAL-006` declara para
 * acotar el tamaño de una sola petición. Quien necesite más lo hace en varias, y cada una es
 * idempotente.
 */
public record RolePermissionsRequest(
    @NotEmpty(message = "VAL-001: Debe indicar al menos un permiso.")
        @Size(max = 100, message = "VAL-006: No es posible indicar más de 100 permisos.")
        Set<UUID> permissionIds) {}
