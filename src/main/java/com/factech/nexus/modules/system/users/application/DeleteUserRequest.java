package com.factech.nexus.modules.system.users.application;

/**
 * Cuerpo de {@code POST /api/v1/users/{id}/deletion} (`RF-SP-029`).
 *
 * <p><b>`POST` sobre un subrecurso y no `DELETE` con cuerpo</b>: RFC 9110 no define semántica para
 * el cuerpo de un {@code DELETE} y un intermediario puede descartarlo, con lo que la petición se
 * convertiría en un rechazo por motivo ausente que el actor no puede entender ni corregir. Y
 * tampoco por <i>query string</i>, o el motivo acabaría en los registros de acceso de los proxies.
 *
 * <p>El motivo es <b>obligatorio</b> y se verifica el primero de todo: el Art. V.13 exige rechazar
 * la eliminación sin motivo <b>antes de ejecutarla</b>.
 */
public record DeleteUserRequest(String reason) {}
