package com.factech.nexus.modules.movements.interfaces;

import com.factech.nexus.modules.movements.application.HotlinkPurchaseRequest;
import com.factech.nexus.modules.movements.application.PurchaseResponse;
import com.factech.nexus.modules.movements.domain.service.BuyByHotlinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * La compra por el hotlink de un vendedor (`RF-MV-011`).
 *
 * <p><b>Bajo {@code /hotlinks} y no bajo {@code /products}</b>, aunque quien responda sea `MV`: el
 * recurso sobre el que se actúa <b>es el enlace</b>, y es lo único que distingue esta compra de la
 * ordinaria. `RF-MV-013` fija la misma forma para el paquete.
 *
 * <p><b>Controlador propio y no un método más en {@code MovementController}</b>: aquel cuelga de
 * {@code /movements} y esta ruta no. Colgarla de allí obligaría a escribir la ruta completa en cada
 * anotación y dejaría la clase respondiendo por dos recursos distintos.
 */
@RestController
@RequestMapping("/api/v1/hotlinks")
@Tag(name = "Movimientos")
public class HotlinkPurchaseController {

  private final BuyByHotlinkService compras;

  public HotlinkPurchaseController(BuyByHotlinkService compras) {
    this.compras = compras;
  }

  @PostMapping("/{username}/{code}/purchases")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('products:buy-by-hotlink')")
  @Operation(
      summary = "Comprar un producto por el hotlink de un vendedor",
      description =
          """
          Registra la compra del producto que llegó por el enlace de un vendedor, y
          **se la acredita a ese vendedor** (`RN-MV-025`) — también cuando quien
          compra tiene otro agente principal.

          **Produce la misma venta que una compra ordinaria**: tipo `VENTA`, estado
          `PENDIENTE`, una línea con el precio y la vigencia **congelados**. Nada se
          entrega ni se cobra aquí; eso es `RF-MV-003`.

          **Y crea el vínculo**: si quien compra no era todavía cliente de ese
          vendedor, pasa a serlo con origen `HOTLINK` y esta venta como la primera
          (`RN-SP-049`). Si ya lo era, no se crea nada y la venta se atribuye igual.

          **Lo que NO hace, y conviene leerlo:** no cambia el agente **principal**.
          Esa es la persona que registró al cliente, es inmutable, y comprar por el
          enlace de otro **suma** un vendedor sin sustituir a ninguno.

          **La venta nace `VALIDADO`** aunque el cliente quede con varios vendedores.
          Es la excepción a `RN-MV-034` —que deja por validar las ventas de quien
          tiene más de uno— y la razón es que aquí **no hay nada que elegir**: el
          enlace ya dice quién trajo la venta.

          **El vendedor no viaja en el cuerpo.** Sale de la ruta, que es la prueba
          de qué enlace se usó. En el cuerpo, cualquiera podría acreditarle una
          venta a cualquiera.

          **El `404` es uno solo y no dice qué falló**: que el vendedor no exista,
          que no sea vendedor, que esté inactivo o que el producto no se ofrezca por
          enlace responden lo mismo. Es la decisión de seguridad de `RF-PM-008`, y
          esta ruta la hereda porque el enlace es público y los nombres de usuario
          se pueden probar.
          """,
      extensions =
          @Extension(
              properties =
                  @ExtensionProperty(
                      name = "x-required-permission",
                      value = "products:buy-by-hotlink")))
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description =
            "La venta registrada, **sin el vendedor**: a quien compra no se le enseña a"
                + " quién se le acreditó (`RF-MV-002` §3.2)",
        content = @Content(schema = @Schema(implementation = PurchaseResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Sin autenticar (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `products:buy-by-hotlink` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "El enlace no resuelve (`EX-001`), sin distinguir cuál de los casos",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description =
            "Comprar por el **propio** enlace (`EX-003`), o cualquiera de los rechazos de negocio"
                + " de una venta: nivel, moneda o método de pago",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public PurchaseResponse comprar(
      @PathVariable String username,
      @PathVariable String code,
      @RequestBody(required = false) HotlinkPurchaseRequest peticion) {
    return compras.buy(username, code, peticion);
  }
}
