package com.factech.nexus.modules.products.application;

/**
 * Cuerpo de {@code POST /api/v1/products/{id}/deletion} (`RF-PM-006`).
 *
 * <p><b>`POST` sobre un subrecurso y no `DELETE` con cuerpo.</b> El `plan.md` proponía lo segundo y
 * lo justificaba diciendo que era lo que hacían `RF-SP-009` y `RF-SP-029`; <b>no es cierto</b>: los
 * dos acabaron en {@code POST /{id}/deletion}, y por un motivo que sigue valiendo aquí — RFC 9110
 * no define semántica para el cuerpo de un {@code DELETE} y un intermediario puede descartarlo, con
 * lo que la petición se convertiría en un rechazo por motivo ausente que el actor no puede entender
 * ni corregir. Se corrigió el plan (Art. I.7).
 *
 * <p>Tampoco por <i>query string</i>: ahí el motivo acabaría escrito en los registros de acceso de
 * cualquier proxy. Eso sí lo decía bien el plan.
 *
 * <p>El motivo es <b>obligatorio</b> y se verifica el primero de todo: el Art. V.13 exige rechazar
 * la eliminación sin motivo <b>antes de ejecutarla</b>, y hacerlo primero significa además que un
 * motivo vacío no cuesta ni una consulta.
 */
public record DeleteProductRequest(String reason) {}
