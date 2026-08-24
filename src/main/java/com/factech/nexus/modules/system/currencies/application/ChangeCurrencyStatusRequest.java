package com.factech.nexus.modules.system.currencies.application;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de {@code PATCH /api/v1/currencies/{id}/status` (`RF-SP-023`).
 *
 * <p><b>Estado destino y no acción</b>, y booleano y no enumerado: {@code {"isActive": false}} es
 * idempotente por construcción —pedir dos veces lo mismo deja el mismo estado— mientras que
 * {@code {"action": "deactivate"}} obliga a definir qué significa repetirla.
 *
 * <p><b>Un solo campo, y el rechazo de los demás es parte del requerimiento.</b> Con {@code
 * FAIL_ON_UNKNOWN_PROPERTIES} activo, un cuerpo que traiga {@code decimalPlaces}, {@code symbol},
 * {@code name} o {@code isDefault} devuelve {@code 400} y <b>no se ignora en silencio</b>. Sin ese
 * rechazo, `CA-SP-188` —que protege el campo del que depende todo redondeo financiero— no
 * comprobaría nada.
 *
 * <p><b>No admite motivo</b> (`CA-SP-340`): el Art. V.13 lo exige solo en las eliminaciones, y esta
 * operación no elimina nada.
 */
public record ChangeCurrencyStatusRequest(
    @NotNull(message = "VAL-001: El estado destino es obligatorio.") Boolean isActive) {}
