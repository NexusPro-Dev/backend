package com.factech.nexus.modules.movements.application;

/**
 * Parámetros de {@code GET /api/v1/movements/mine/products} (`RF-MV-014`).
 *
 * <p>Como {@link MyMovementsRequest}, <b>sin identificador de persona</b>: quien pregunta sale de
 * la credencial, y no hay forma de preguntar por otro.
 *
 * @param state opcional, uno de {@link PurchasedProductState}. Ausente, todos
 */
public record MyProductsRequest(Integer page, Integer size, String state) {

  public MyProductsRequest {
    state = state == null || state.isBlank() ? null : state.trim().toUpperCase();
  }
}
