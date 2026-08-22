package com.factech.nexus.modules.system.roles.application;

import com.factech.nexus.modules.system.roles.domain.models.RoleType;
import java.util.Set;
import java.util.UUID;

/**
 * Entrada del caso de uso de alta de rol (`RF-SP-001` · `T-15`).
 *
 * <p><b>Sin tipos de HTTP.</b> Es lo que separa el caso de uso del transporte: el mismo comando
 * podría llegar de un endpoint, de una tarea o de una prueba, y ninguno de los tres necesita saber
 * de los otros.
 *
 * <p><b>Sin {@code status} ni {@code isSystem}</b>, igual que el DTO de entrada: el rol nace {@code
 * ACTIVO} y nunca de sistema, y no admitirlos como argumento es lo que hace que no exista camino
 * hacia otro valor (`CA-SP-146`).
 *
 * @param permissionIds ya sin duplicados; el DTO los colapsa antes de construir este comando
 */
public record CreateRoleCommand(
    String code,
    String name,
    String description,
    RoleType roleType,
    UUID parentRoleId,
    Set<UUID> permissionIds) {}
