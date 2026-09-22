package com.factech.nexus.modules.movements.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Parámetros de {@code GET /api/v1/movements} (`RF-MV-006`).
 *
 * <p><b>Aquí SÍ hay campos para decir sobre quién se pregunta</b>, y esa es la diferencia con
 * {@link MyMovementsRequest} que hay que leer bien: allí su ausencia era el requerimiento, porque
 * nada debía escaparse del actor; aquí el actor ya demostró con {@code movements:read} que puede
 * ver a cualquiera, y el sujeto y el vendedor son <b>filtros</b>. Lo que cierra el agujero no es
 * este registro sino la anotación del controlador.
 *
 * <p><b>Tampoco hay parámetro de ordenamiento</b>, como en `RF-MV-008` y en los cuatro listados de
 * auditoría: el orden cronológico es el significado de un libro.
 *
 * @param status opcional; uno que no exista es un error, no una página vacía
 * @param type opcional (21-09-2026); el código del tipo de movimiento. Uno que no exista en el
 *     catálogo es un error, como el estado: `RN-MV-017` hace del catálogo un conjunto cerrado
 * @param userId el sujeto (`RN-MV-026`). Uno inexistente da página vacía
 * @param sellerId vendedor de <b>alguna</b> línea (`RN-MV-003`). Uno inexistente da página vacía
 * @param paymentMethodId uno inexistente da página vacía
 * @param code el comprobante exacto, sin distinguir mayúsculas
 * @param from desde cuándo ocurrió, inclusive
 * @param to hasta cuándo ocurrió, exclusive
 */
public record ListMovementsRequest(
    Integer page,
    Integer size,
    String status,
    String type,
    UUID userId,
    UUID sellerId,
    UUID paymentMethodId,
    String code,
    OffsetDateTime from,
    OffsetDateTime to) {

  public ListMovementsRequest {
    status = status == null || status.isBlank() ? null : status.trim().toUpperCase();
    type = type == null || type.isBlank() ? null : type.trim().toUpperCase();
    // EN MAYÚSCULAS para que la comparación sea por igualdad y la responda
    // `uq_movements_code`: los comprobantes nacen en mayúsculas (`MovementCode`),
    // y un `upper(code) = …` dejaría el índice sin usar.
    code = code == null || code.isBlank() ? null : code.trim().toUpperCase();
  }
}
