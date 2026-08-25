package com.factech.nexus.modules.system.roles.application;

/**
 * Cuerpo de {@code POST /api/v1/roles/{id}/deletion} (`RF-SP-009`).
 *
 * <p><b>`POST` sobre un subrecurso y no `DELETE` con cuerpo</b>: RFC 9110 no define semántica para
 * el cuerpo de un {@code DELETE} y un intermediario puede descartarlo, con lo que la petición se
 * convertiría en un rechazo por motivo ausente que el actor no puede entender ni corregir. Tampoco
 * por <i>query string</i>, o el motivo acabaría en los registros de acceso de los proxies. Es la
 * misma forma que la baja de una persona.
 *
 * <p>El motivo es <b>obligatorio</b> y se verifica el primero de todo: el Art. V.13 exige rechazar
 * la eliminación sin motivo <b>antes de ejecutarla</b>. No se admite un valor generado por el
 * sistema — un motivo automático es no tener motivo.
 */
public record DeleteRoleRequest(String reason) {}
