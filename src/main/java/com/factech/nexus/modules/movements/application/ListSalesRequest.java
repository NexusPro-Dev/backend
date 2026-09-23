package com.factech.nexus.modules.movements.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Parámetros de {@code GET /api/v1/movements/sales} (`RF-MV-015`).
 *
 * <p><b>Sobre quién se pregunta NO es un dato de entrada, y esa es la mitad del requerimiento</b>:
 * el alcance sale de quién es quien pregunta —su tipo de rol y su lugar en la estructura,
 * `RN-MV-031`— y no hay forma de pedir el alcance de otra persona. {@code userId} <b>acota
 * dentro</b> del alcance: «las ventas de mi agente tal»; no lo cambia.
 *
 * <p><b>No hay {@code type}</b>: el tipo es el de la ruta. Los otros tipos de movimiento tendrán la
 * suya (`spec.md` §2.1).
 *
 * @param userId opcional; una persona <b>de mi alcance</b> como vendedora de alguna línea. Fuera de
 *     él, o inexistente, página vacía y no un error
 * @param status opcional; uno que no exista es un error
 * @param paymentMethodId opcional (21-09-2026); uno inexistente da página vacía
 * @param code opcional (21-09-2026); el comprobante exacto, sin distinguir mayúsculas, <b>si está
 *     en mi alcance</b>; si no, página vacía
 * @param from desde cuándo ocurrió, inclusive
 * @param to hasta cuándo ocurrió, exclusive
 */
public record ListSalesRequest(
    Integer page,
    Integer size,
    UUID userId,
    String status,
    String typeStatus,
    UUID paymentMethodId,
    String code,
    OffsetDateTime from,
    OffsetDateTime to) {

  public ListSalesRequest {
    status = status == null || status.isBlank() ? null : status.trim().toUpperCase();
    typeStatus =
        typeStatus == null || typeStatus.isBlank() ? null : typeStatus.trim().toUpperCase();
    code = code == null || code.isBlank() ? null : code.trim().toUpperCase();
  }
}
