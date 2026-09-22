package com.factech.nexus.modules.commissions.interfaces;

import com.factech.nexus.modules.commissions.application.ProductAssociationResponse;
import com.factech.nexus.modules.commissions.domain.service.ListProductAssociationsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Qué paga un producto, y a qué rol.
 *
 * <p>Hasta el 15-09-2026 era «la otra dirección de la tabla de asociación»; desde `RN-CM-021` la
 * tasa nace con su producto y esta lectura es la del catálogo filtrada por producto, sin paginar y
 * con la forma de siempre. Se conserva porque es lo que mira quien va a vender el producto o quien
 * revisa por qué una venta pagó lo que pagó — y porque retirarla no ahorraría nada a nadie.
 *
 * <p><b>Recurso raíz propio y no {@code /commission-rates/by-product/{id}}</b>: ese camino habría
 * competido en forma con {@code /commission-rates/{id}}, y aunque Spring resuelve antes el segmento
 * literal, el día que alguien lo renombrara el síntoma sería un {@code 400} por identificador
 * inválido en una ruta que no se tocó.
 */
@Tag(name = "Comisiones", description = "Tasas de comisión por rol: qué paga cada producto.")
@RestController
@RequestMapping("/api/v1/product-commission-rates")
public class ProductCommissionRateController {

  private final ListProductAssociationsService asociaciones;

  public ProductCommissionRateController(ListProductAssociationsService asociaciones) {
    this.asociaciones = asociaciones;
  }

  @Operation(
      summary = "Consultar qué comisiona un producto, y a qué rol",
      description =
          """
          Devuelve una entrada **por cada rol** que cobra comisión por ese producto,
          con el valor ya resuelto: las tasas de rol **vivas** de ese producto
          (`RN-CM-021`). Es lo mismo que `GET /api/v1/commission-rates?productId=`,
          sin paginar y con la forma de siempre.

          **Una lista vacía significa que ese producto no paga comisión a nadie**
          — nadie registró una tasa sobre él (`RN-CM-012`).

          **Esto no resuelve la comisión de una persona, y solo devuelve roles.** Las
          tasas personalizadas también se asocian a productos desde el 11-09-2026 y
          ganan sobre estas, pero **no aparecen aquí**: qué personas tienen excepción
          en este producto lo responde `GET /api/v1/user-commission-rates?productId=`,
          paginado y con su historial. Para saber qué cobra alguien concreto, use
          `GET /api/v1/commissions/effective`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Las asociaciones del producto"),
    @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
    @ApiResponse(responseCode = "403", description = "Sin permiso")
  })
  @GetMapping
  @PreAuthorize("hasAuthority('product-commission-rates:read')")
  public ProductAssociationResponse porProducto(@RequestParam UUID productId) {
    return asociaciones.byProduct(productId);
  }
}
