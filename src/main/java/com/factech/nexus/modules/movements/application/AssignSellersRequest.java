package com.factech.nexus.modules.movements.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Asignar los vendedores de una venta (`RF-MV-016`): una pareja línea-vendedor por cada línea que
 * se asigna o se corrige.
 *
 * <p><b>La línea se nombra por el producto</b>, que no se repite dentro de una venta (`RN-MV-011`)
 * y es lo que el detalle publica; la línea no tiene un identificador público.
 */
public record AssignSellersRequest(
    @Schema(
            description =
                "Al menos una. Se puede asignar una parte de las líneas: la venta sigue en"
                    + " VALIDAR_COMISIONES mientras quede alguna sin vendedor.")
        List<Line> lines) {

  @Schema(name = "SellerAssignment")
  public record Line(
      @Schema(description = "El producto de la línea, que la identifica dentro de la venta.")
          UUID productId,
      @Schema(description = "El vendedor que se le atribuye: uno de los del cliente.")
          UUID sellerId) {}
}
