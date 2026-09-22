package com.factech.nexus.modules.commissions.application;

/**
 * Cuerpo de {@code POST /api/v1/commission-rates/{id}/deletion} (`RF-CM-004`).
 *
 * <p><b>Es un POST a un subrecurso y no un DELETE</b>, por lo mismo que en `PM`: el motivo es
 * obligatorio (Art. V.13), HTTP no define semantica para el cuerpo de un {@code DELETE} y un
 * intermediario puede descartarlo. Tampoco por cadena de consulta, donde acabaria escrito en los
 * registros de acceso de cualquier proxy.
 */
public record DeleteCommissionRateRequest(String reason) {}
