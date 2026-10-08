package com.factech.nexus.modules.commissions.domain.models;

/**
 * Cómo se paga lo que cierra un cierre programado (`RN-CM-053`, `RN-CM-054`).
 *
 * <p>Se elige por turno en las 48 horas anteriores (`RF-CM-029`); <b>sin elección, {@link
 * #AUTOMATICO}</b>.
 */
public enum PaymentMode {
  /** El cierre paga cada lote que cerró, en cuanto termina. */
  AUTOMATICO,
  /** Los lotes se quedan {@code PENDIENTE} y los paga Finanzas. */
  MANUAL
}
