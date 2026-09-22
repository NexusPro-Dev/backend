package com.factech.nexus.modules.movements.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Parámetros de {@code GET /api/v1/movements/mine} (`RF-MV-008`).
 *
 * <p><b>Lo que este registro NO tiene es el requerimiento.</b> No hay ningún campo por el que decir
 * sobre quién se pregunta: quien pregunta sale de la credencial, y un parámetro que lo dijera sería
 * exactamente el agujero que `RF-MV-008` existe para no abrir. Consultar los movimientos de otra
 * persona es `RF-MV-006`, con su permiso.
 *
 * <p><b>Tampoco hay parámetro de ordenamiento</b>, y es una decisión y no un olvido: el orden es
 * fijo, del más reciente al más antiguo. Ofrecer ordenar por importe o por estado invitaría a
 * construir informes sobre un endpoint que existe para que alguien mire lo suyo.
 *
 * @param status opcional. Ausente, devuelve todos los estados
 * @param type opcional (21-09-2026); el código del tipo de movimiento. Ausente, todos los tipos.
 *     Uno que no exista en el catálogo es un error, como el estado (`RF-MV-006` §6.1)
 * @param paymentMethodId opcional (21-09-2026); uno inexistente da página vacía
 * @param code opcional (21-09-2026); el comprobante exacto, sin distinguir mayúsculas. Uno ajeno no
 *     devuelve nada: el alcance va antes que el filtro
 * @param from desde cuándo ocurrió, inclusive (21-09-2026)
 * @param to hasta cuándo ocurrió, exclusive (21-09-2026)
 */
public record MyMovementsRequest(
    Integer page,
    Integer size,
    String status,
    String type,
    UUID paymentMethodId,
    String code,
    OffsetDateTime from,
    OffsetDateTime to) {

  public MyMovementsRequest {
    status = status == null || status.isBlank() ? null : status.trim().toUpperCase();
    type = type == null || type.isBlank() ? null : type.trim().toUpperCase();
    // EN MAYÚSCULAS para que la igualdad la responda `uq_movements_code`, como
    // en `RF-MV-006`.
    code = code == null || code.isBlank() ? null : code.trim().toUpperCase();
  }
}
