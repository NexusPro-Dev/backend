package com.factech.nexus.modules.commissions.domain.models;

/**
 * De cuál de las dos piezas salió la comisión resuelta (`RN-CM-004`).
 *
 * <p><b>Son dos y no cuatro</b>, y ahí está el rediseño del 01-09-2026: donde antes había cuatro
 * grados que la ausencia determinaba —persona y producto, persona, producto, rol—, ahora hay <b>una
 * pregunta y una respuesta de reserva</b>.
 *
 * <p><b>El orden de declaración es el orden de precedencia.</b> No es documentación: la sentencia
 * que resuelve ordena por él, de modo que reordenar estas constantes sin tocar la consulta
 * <b>desalinearía la regla de su implementación</b> — y no fallaría: devolvería un porcentaje
 * plausible.
 */
public enum RateSource {

  /** La excepción de la persona. Gana siempre, y <b>sin mirar el producto</b>. */
  PERSONALIZADA,

  /** La tasa que el rol vendedor tiene <b>asociada a ese producto</b>. */
  ROL,

  /**
   * La comisión por venta directa del producto (`RN-CM-045`, 29-09-2026): la cobra en el nivel
   * {@code 0} quien no es el último eslabón, en lugar de su tasa de rol. <b>No sale de la sentencia
   * de `RF-CM-005`</b>, y por eso no altera el orden de arriba: la pone el devengo (`RF-CM-013`)
   * cuando lo resuelto no es una personalizada. Su {@code rateId} es el producto.
   */
  DIRECTA
}
