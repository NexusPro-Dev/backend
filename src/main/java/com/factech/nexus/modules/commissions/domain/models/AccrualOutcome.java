package com.factech.nexus.modules.commissions.domain.models;

/**
 * Qué pasó con una línea de venta a efectos de comisión (`RN-CM-032`).
 *
 * <p><b>Solo {@link #RECHAZADA} se reintenta.</b> {@link #SIN_COMISION} dice lo que regía —nadie
 * tenía tasa el día de la venta— y una tasa de rol registrada después no tiene fecha que la
 * distinga de una antigua: reintentar pagaría hacia atrás lo que nunca se configuró.
 */
public enum AccrualOutcome {
  /** La cadena cobró; sus filas están en {@code commissions}. */
  DEVENGADA,
  /** Nadie de la cadena tenía tasa sobre el producto (`RN-CM-012`). Definitivo. */
  SIN_COMISION,
  /** La cadena pasaba del importe de la línea (`RN-CM-026`). Se reintenta en cada cierre. */
  RECHAZADA
}
