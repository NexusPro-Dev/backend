package com.factech.nexus.modules.movements.domain.models;

/**
 * Los cuatro estados de un movimiento (`RN-MV-005`, {@code ck_movements_status}).
 *
 * <p><b>{@code PENDIENTE} es el único con el que se nace</b>, y no existe forma de construir un
 * movimiento en otro: registrar una venta significa exactamente que alguien dijo que iba a pagar y
 * que nadie ha comprobado que pagara.
 *
 * <p><b>De {@code CONFIRMADA} no se sale</b>, y {@code RECHAZADA} y {@code ANULADA} son finales. El
 * dominio cerrado lo declara el esquema; <b>la transición la decide el caso de uso</b>, que aquí es
 * `RF-MV-003` a `RF-MV-005` y no este requerimiento.
 *
 * <p><b>Anular y rechazar no son sinónimos</b>, y por eso son dos: rechazar es decir que no se pagó
 * —caja diaria—, anular es decir que la venta no debió existir.
 *
 * <p>Solo {@code CONFIRMADA} produce efectos (`RN-MV-004`): concede el nivel, habilita la cuenta y
 * comisiona. Las otras tres no cambian nada fuera del módulo.
 */
public enum MovementStatus {
  PENDIENTE,
  CONFIRMADA,
  RECHAZADA,
  ANULADA
}
