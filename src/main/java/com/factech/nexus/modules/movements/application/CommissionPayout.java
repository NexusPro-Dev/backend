package com.factech.nexus.modules.movements.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * <b>Abonar el pago de un lote de comisión</b> (`RF-MV-024`, `RN-MV-044`): la operación que `MV`
 * publica para que `CM` la invoque al marcar un lote como pagado (`RF-CM-011`, `RN-CM-030`).
 *
 * <p>Es una <b>interfaz de aplicación de escritura</b>, con la forma que **D-26** fijó para {@code
 * MembershipGrant} (`architecture.md` §15.2): recibe una orden con lo mínimo —a quién, cuánto, en
 * qué moneda y de qué lote— y responde lo que quedó. <b>No recibe ni devuelve entidades</b>, y
 * <b>`MV` no sabe qué es un lote</b>: la referencia del lote a su abono la guarda `CM`.
 *
 * <p><b>Corre dentro de la transacción de quien llama, y exige que haya una</b>: el abono y la
 * marca del lote son un solo acto, o ninguno.
 *
 * <p><b>Una vez por lote</b>: la misma orden repetida devuelve el mismo abono. <b>El importe se
 * redondea</b> a los decimales de la moneda, a la mitad hacia arriba —el lote suma con cuatro—; un
 * lote que redondea a cero se abona con un movimiento de importe cero y sin asientos.
 */
public interface CommissionPayout {

  PayoutResult pay(PayoutOrder orden);

  /**
   * @param amount el total del lote, con sus cuatro decimales; no negativo
   * @param batchId la identidad del lote: es lo que impide abonarlo dos veces
   * @param batchCode el código del lote, para el concepto que la persona lee en su historial
   */
  record PayoutOrder(
      UUID userId, UUID currencyId, BigDecimal amount, UUID batchId, String batchCode) {}

  /** El movimiento {@code PAGO_COMISION} que quedó, con el importe ya redondeado. */
  record PayoutResult(UUID movementId, String code, BigDecimal amount) {}
}
