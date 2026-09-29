package com.factech.nexus.modules.commissions.domain.models;

/**
 * De qué clase es una comisión generada (`RN-CM-044`, 29-09-2026): el campo que el responsable del
 * proyecto pidió, «Por venta» o «Por afftrack».
 */
public enum CommissionKind {
  /** Una línea de venta, un nivel de la cadena, una tasa (`RF-CM-013`). */
  POR_VENTA,
  /** Un escalón afftrack pagado en un cierre: sin línea ni nivel (`RF-CM-020`). */
  POR_AFFTRACK
}
