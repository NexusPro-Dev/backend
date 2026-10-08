package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.domain.models.BatchStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * <b>Tras un pago, se borran todos los pendientes vacíos</b> (`RN-CM-052`, `RF-CM-011` `plan.md`
 * §13, `RF-CM-025` `plan.md` §12), de todas las personas.
 *
 * <p><b>No es {@code @Transactional}, y no debe serlo</b>: se llama cuando el pago ya confirmó, y
 * el borrado abre la suya. <b>Tampoco deja salir una excepción</b>: el pago está hecho y su
 * respuesta no puede cambiar por esto; los vacíos esperan al siguiente pago o a la orden de
 * `RF-CM-027`.
 */
@Component
public class EmptyBatchesAfterPayment {

  private static final Logger LOG = LoggerFactory.getLogger(EmptyBatchesAfterPayment.class);
  private static final String MOTIVO =
      "RN-CM-052: el lote pendiente no tenía comisiones y se borró tras un pago.";

  private final DeleteEmptyBatchesService vacios;

  public EmptyBatchesAfterPayment(DeleteEmptyBatchesService vacios) {
    this.vacios = vacios;
  }

  public void run() {
    try {
      vacios.deleteEmpty(MOTIVO, BatchStatus.PENDIENTE);
    } catch (RuntimeException e) {
      LOG.error("No se pudieron borrar los lotes pendientes vacíos tras un pago.", e);
    }
  }
}
