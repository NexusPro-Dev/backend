package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * En qué papel aparece en un movimiento la persona que lo consulta (`RF-MV-008`).
 *
 * <p><b>Viaja aunque se pueda deducir</b>, y esa es la decisión que este tipo encarna. Quien
 * consume la respuesta ya tiene su propio identificador y podría compararlo con las dos partes;
 * hacerlo bien —incluido {@link #BOTH}, que es el que se olvida— es lógica que acabaría escrita en
 * cada cliente de la API, y escrita distinto en cada uno (`spec.md` §6.2).
 *
 * <p><b>Es un tipo y no una cadena suelta</b> para que el contrato publicado enumere los tres
 * valores en lugar de decir «texto».
 */
@Schema(description = "El papel de quien consulta dentro del movimiento.")
public enum MovementRole {

  /** Recibió lo comprado. */
  BUYER,

  /** Se le atribuye la venta. */
  SELLER,

  /**
   * Las dos cosas <b>en el mismo movimiento</b>.
   *
   * <p>Ocurre desde que comprar dejó de ser cosa solo de los clientes (`RF-MV-002`): alguien de la
   * fuerza comercial compra para sí mismo y la venta se le atribuye. El movimiento aparece <b>una
   * sola vez</b> —duplicarlo contaría dos veces un solo hecho (`FA-002`)—.
   */
  BOTH
}
