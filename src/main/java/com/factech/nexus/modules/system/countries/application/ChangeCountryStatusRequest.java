package com.factech.nexus.modules.system.countries.application;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo del cambio de estado de un país (`RF-SP-022`).
 *
 * <p><b>Estado destino y no acción</b>: repetir la misma petición deja el mismo resultado, que es
 * lo que `FA-001` describe.
 *
 * <p><b>Booleano y no enumerado.</b> La columna es {@code boolean} y la respuesta ya devuelve
 * {@code isActive}: pedir el cambio con un nombre y devolverlo con otro obligaría al cliente a
 * traducir. Es la diferencia con el estado de un rol, que es un dominio cerrado con nombres
 * propios. Aquí no hay tercer estado posible ni previsible: un país se ofrece o no se ofrece.
 *
 * <p><b>Un solo campo, y el rechazo de los demás es parte del requerimiento.</b> Un cuerpo con
 * {@code reason}, {@code code} o {@code name} devuelve {@code 400}. Sin ese rechazo, `CA-SP-338`
 * —no admite motivo— y `CA-SP-180` —el código y el nombre no cambian— pasarían sin comprobar nada.
 */
public record ChangeCountryStatusRequest(
    @NotNull(message = "VAL-001: El estado destino es obligatorio.") Boolean isActive) {}
