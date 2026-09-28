package com.factech.nexus.modules.commissions.interfaces;

import com.factech.nexus.modules.commissions.domain.service.CommissionAccrualService;
import com.factech.nexus.modules.movements.application.CommissionableLinesEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Escucha el aviso de `MV` y devenga (`RN-CM-031`, `RN-MV-049`).
 *
 * <p><b>{@code AFTER_COMMIT}</b>: si la transacción de `MV` se revierte, el aviso no llega —no hay
 * comisión de una venta que no existe—; si se confirma, llega en el mismo hilo, después del commit.
 * <b>Nada sale de aquí</b>: una excepción en {@code afterCommit} la recibiría quien confirmó la
 * venta, que ya está confirmada. Si devengar falla, las líneas quedan sin desenlace y las recoge el
 * barrido del siguiente cierre (`RN-CM-034`).
 */
@Component
public class CommissionableLinesListener {

  private static final Logger LOG = LoggerFactory.getLogger(CommissionableLinesListener.class);

  private final CommissionAccrualService devengo;

  public CommissionableLinesListener(CommissionAccrualService devengo) {
    this.devengo = devengo;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCommissionableLines(CommissionableLinesEvent aviso) {
    try {
      devengo.accrue(aviso.detailIds());
    } catch (RuntimeException e) {
      LOG.error(
          "No se pudo devengar la venta {}; la recogerá el siguiente cierre.",
          aviso.movementId(),
          e);
    }
  }
}
