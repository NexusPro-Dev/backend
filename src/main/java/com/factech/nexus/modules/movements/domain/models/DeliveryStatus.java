package com.factech.nexus.modules.movements.domain.models;

/**
 * El estado de la entrega de una línea (`RN-MV-030`), <b>la única cosa de una línea que cambia
 * después de escribirse</b>.
 *
 * <p>Es de la línea y no de la venta porque los productos de una misma venta no se entregan igual;
 * y es cerrado hacia adelante: de {@code PENDIENTE} se pasa a {@code ENTREGADA} o a {@code
 * RETENIDA}, y de ahí no se sale. {@code ck_movement_details_delivery} ata la fecha a la entrega y
 * el motivo a la retención.
 */
public enum DeliveryStatus {
  /** No se ha entregado: la venta no está confirmada, o es manual y nadie la autorizó. */
  PENDIENTE,
  /** Entregada; {@code delivered_at} es desde cuándo se tiene, y desde ahí corre la vigencia. */
  ENTREGADA,
  /** La venta se confirmó y esta línea no se entregará, por lo que dice el motivo (`RN-MV-029`). */
  RETENIDA
}
