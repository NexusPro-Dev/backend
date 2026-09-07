package com.factech.nexus.modules.products.domain.models;

/**
 * Hasta dónde se muestra un producto (`RN-PM-019`).
 *
 * <p><b>Es una escala, no un reparto.</b> {@link #HOTLINKS} <b>incluye</b> {@link #TIENDA}: un
 * producto de ese alcance se ve en la tienda <b>y</b> en los hotlinks. La pregunta que responde el
 * campo es «hasta dónde llega», no «en cuál de los dos está».
 *
 * <p><b>De ahí sale lo que cuesta, y conviene tenerlo escrito donde se lee el enumerado</b>: no
 * existe forma de publicar algo <b>solo</b> en hotlinks. El día que ese caso exista, lo que entra
 * es un <b>tercer valor</b> —{@code SOLO_HOTLINKS}— y no un cambio de significado de estos dos:
 * cambiárselo reescribiría en silencio cada fila ya declarada.
 *
 * <p><b>No filtra la oferta</b> (`RF-PM-007`). Precisamente porque la escala es acumulativa, los
 * dos valores llegan a la tienda y un predicado sobre este campo devolvería siempre lo mismo que no
 * ponerlo. Su único filtro vive en el catálogo administrativo (`RF-PM-002`).
 */
public enum ProductScope {

  /** Solo la tienda. Es el alcance más corto. */
  TIENDA,

  /** La tienda <b>y</b> los hotlinks. Incluye al anterior. */
  HOTLINKS
}
