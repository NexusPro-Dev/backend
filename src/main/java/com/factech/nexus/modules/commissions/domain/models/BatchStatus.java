package com.factech.nexus.modules.commissions.domain.models;

/**
 * El estado de un lote de comisión (`RN-CM-030`, `RN-CM-033`). Las transiciones son dos y ninguna
 * vuelve: el cierre pasa {@link #ABIERTO} a {@link #PENDIENTE} y pagar pasa {@link #PENDIENTE} a
 * {@link #PAGADO}.
 */
public enum BatchStatus {
  /** Creciendo: cada comisión nueva de esa persona y moneda se suma aquí. Sin fin de periodo. */
  ABIERTO,
  /** Cerrado: su importe ya no cambia, y espera a que Finanzas lo pague. */
  PENDIENTE,
  /** Abonado en la billetera de su persona. */
  PAGADO
}
