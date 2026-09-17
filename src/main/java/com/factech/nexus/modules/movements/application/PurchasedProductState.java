package com.factech.nexus.modules.movements.application;

/**
 * En qué estado está un producto comprado (`RF-MV-014` · `spec.md` §2.1), <b>calculado</b> de lo
 * que la venta, la entrega y la vigencia ya dicen — no se guarda en ningún sitio.
 *
 * <p>Son seis y cerrados, y el contrato los enumera para que quien pinte la pantalla no adivine.
 */
public enum PurchasedProductState {
  /** La venta está pendiente: lo comprado todavía no se tiene (`RN-MV-004`). */
  PENDIENTE_PAGO,
  /** La venta se confirmó y la línea es manual: espera a que alguien la autorice (`RN-MV-021`). */
  PENDIENTE_AUTORIZACION,
  /** Entregada y con la vigencia por delante, o sin vigencia (`RN-PM-015`). */
  ACTIVO,
  /** Entregada y con la vigencia pasada. */
  VENCIDO,
  /** La venta se confirmó y esta línea no se entregará (`RN-MV-029`). */
  RETENIDO,
  /** La venta se rechazó. */
  RECHAZADO,
  /** La venta se anuló. */
  ANULADO
}
