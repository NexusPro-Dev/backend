package com.factech.nexus.modules.movements.domain.models;

/**
 * El hecho que escribe un grupo de asientos (`RN-MV-042`). Los asientos de un mismo {@code
 * (movimiento, evento)} suman cero, y lo comprueba {@code tg_movement_entries_cuadre} al cerrar la
 * transacción.
 */
public enum EntryEvent {
  /** El retiro se pide: la billetera pasa a retenido. */
  SOLICITUD,
  /** El retiro se aprueba: lo retenido sale hacia la cuenta de retiros de la empresa. */
  APROBACION,
  /** El retiro se niega: lo retenido vuelve a la billetera. */
  RECHAZO,
  /**
   * Un bono o el pago de un lote entran en la billetera; o, desde el 30-09-2026, los puntos de una
   * compra confirmada entran en la cuenta de puntos (`RF-MV-028`).
   */
  ABONO,
  /** Una venta se paga con puntos: salen de la cuenta de puntos (`RN-MV-052`). */
  PAGO
}
