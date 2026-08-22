package com.factech.nexus.modules.system.permissions.application;

import java.util.UUID;

/**
 * Modelo de lectura de un permiso (`RF-SP-010` · `T-05`).
 *
 * <p>Seis campos: los tres que `RF-SP-001` necesita para el detalle de un rol —{@code id}, {@code
 * code}, {@code name}— más {@code resource}, {@code action} y {@code description}, que el catálogo
 * sí muestra.
 *
 * <p><b>Se amplía en lugar de duplicarse.</b> La alternativa era un segundo tipo casi idéntico para
 * el catálogo, que obligaría a mantener dos representaciones del mismo concepto y a decidir en cada
 * endpoint futuro cuál usar. Que el detalle de un rol devuelva también el recurso y la acción es
 * información correcta y no contradice ninguna especificación (`plan.md` §3).
 *
 * <p>No lleva {@code createdAt} ni {@code updatedAt}: el catálogo cambia por migración, y esas
 * marcas cuentan cuándo se desplegó una, no información de negocio.
 *
 * @param description puede ser nula; el contrato la devuelve como {@code null}, nunca omitida
 */
public record PermissionItem(
    UUID id, String code, String resource, String action, String name, String description) {}
