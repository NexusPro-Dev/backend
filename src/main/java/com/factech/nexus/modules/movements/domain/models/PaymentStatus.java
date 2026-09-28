package com.factech.nexus.modules.movements.domain.models;

/**
 * El estado de un intento de pago (`RN-MV-039`, `RF-MV-018`).
 *
 * <p><b>En masculino</b> porque son de <b>un pago</b>, como {@code PAGADO} es de un lote en `CM`;
 * la venta sigue en femenino. De {@code PENDIENTE} se sale una vez, y los otros dos son finales.
 */
public enum PaymentStatus {
  PENDIENTE,
  CONFIRMADO,
  RECHAZADO
}
