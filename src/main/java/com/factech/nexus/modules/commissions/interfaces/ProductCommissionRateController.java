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
 * La asociación leída <b>desde el producto</b>.
 *
 * <p><b>Es la otra dirección de la misma tabla, y es otra pregunta.</b> Desde la tasa se responde
 * «sobre qué productos rige esto», que es lo que mira quien administra el catálogo; desde el
 * producto se responde «qué paga esto a cada rol», que es lo que mira quien va a venderlo o quien
 * revisa por qué una venta pagó lo que pagó.
 *
 * <p><b>Recurso raíz propio y no {@code /commission-rates/by-product/{id}}</b>: ese camino habría
 * competido en forma con {@code /commission-rates/{id}}, y aunque Spring resuelve antes el segmento
 * literal, el día que alguien lo renombrara el síntoma sería un {@code 400} por identificador
 * inválido en una ruta que no se tocó.
 */
@Tag(
    name = "Comisiones",
    description = "Tasas de comisión por rol y su asociación con los productos.")
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
          con el porcentaje ya resuelto.

          **Una lista vacía significa que ese producto no paga comisión a nadie**
          — ni siquiera a los roles que tienen tasa en el catálogo, si nadie la
          asoció (`RN-CM-012`).

          **Esto no resuelve la comisión de una persona.** Una tasa personalizada
          gana sobre todas estas y no aparece aquí, porque no se asocia a productos.
          Para saber qué cobra alguien concreto, use
          `GET /api/v1/commissions/effective`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Las asociaciones del producto"),
    @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
    @ApiResponse(responseCode = "403", description = "Sin permiso")
  })
  @GetMapping
  @PreAuthorize("hasAuthority('commissions:read')")
  public ProductAssociationResponse porProducto(@RequestParam UUID productId) {
    return asociaciones.byProduct(productId);
  }
}
