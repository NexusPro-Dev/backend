package com.factech.nexus.modules.system.roles.application;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de {@code PATCH /api/v1/roles/{id}/status} (`RF-SP-007`).
 *
 * <p><b>Estado destino y no acción</b>: repetir la misma petición deja el mismo resultado, que es
 * lo que `FA-001` describe. Con {@code activate}/{@code deactivate} habría dos rutas para un solo
 * hecho y la idempotencia dependería de cuál se llamara.
 *
 * <p><b>Enumerado y no booleano</b>, al revés que en el catálogo de países: el estado de un rol es
 * un dominio cerrado con nombres propios —{@code ACTIVO}, {@code INACTIVO}— que la respuesta ya
 * devuelve como texto. Pedirlo con un booleano obligaría al cliente a traducir en un sentido y
 * volver a traducir en el otro.
 *
 * <p><b>Un solo campo, y el rechazo de los demás es parte del requerimiento.</b> Un cuerpo con
 * {@code reason} o con {@code name} devuelve {@code 400}: cambiar el estado no admite motivo —el
 * Art. V.13 lo exige solo en las eliminaciones— ni toca ningún otro dato del rol.
 */
public record ChangeRoleStatusRequest(
    @NotBlank(message = "VAL-001: El estado destino es obligatorio.") String status) {}
