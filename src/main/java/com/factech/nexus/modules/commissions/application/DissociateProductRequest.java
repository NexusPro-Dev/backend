package com.factech.nexus.modules.commissions.application;

/**
 * Cuerpo de {@code POST /api/v1/user-commission-rates/{id}/products/{productId}/deletion}
 * (`RF-CM-006`).
 *
 * <p>Nació para `RF-CM-008`, la desasociación de la tasa de rol, retirada el 15-09-2026 con
 * `RN-CM-021`: la tasa de rol nace con su producto y no se desasocia — se retira. Queda para la
 * personalizada, que sigue asociándose.
 *
 * <p><b>Es un POST a un subrecurso y no un DELETE</b>, por lo mismo que en `PM`: el motivo es
 * obligatorio (Art. V.13), HTTP no define semántica para el cuerpo de un {@code DELETE} y un
 * intermediario puede descartarlo. Tampoco por cadena de consulta, donde acabaría escrito en los
 * registros de acceso de cualquier proxy.
 *
 * <p><b>Y el motivo importa más aquí que en un retiro cualquiera</b>: la asociación no tiene retiro
 * lógico —se borra la fila—, de modo que el registro de eliminación es <b>lo único que queda</b> de
 * que esa tasa rigió alguna vez sobre ese producto.
 */
public record DissociateProductRequest(String reason) {}
