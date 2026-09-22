package com.factech.nexus.modules.movements.application;

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
 */
public record MyMovementsRequest(Integer page, Integer size, String status, String type) {

  public MyMovementsRequest {
    status = status == null || status.isBlank() ? null : status.trim().toUpperCase();
    type = type == null || type.isBlank() ? null : type.trim().toUpperCase();
  }
}
