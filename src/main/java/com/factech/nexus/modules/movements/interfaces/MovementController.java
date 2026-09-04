package com.factech.nexus.modules.movements.interfaces;

import com.factech.nexus.modules.movements.application.RegisterSaleRequest;
import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.service.RegisterSaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El libro de movimientos (`MV`).
 *
 * <p><b>El recurso es {@code /movements} y no {@code /sales}</b>, aunque hoy solo se registren
 * ventas. La tabla es el libro y los depósitos entran por aquí en la etapa 2 del módulo; un recurso
 * llamado {@code sales} obligaría a inventar otro para el mismo objeto o a renombrar el publicado.
 */
@Tag(
    name = "Movimientos",
    description = "El libro de hechos económicos: qué se vendió, a quién y a quién se le atribuye.")
@RestController
@RequestMapping("/api/v1/movements")
public class MovementController {

  private final RegisterSaleService alta;

  public MovementController(RegisterSaleService alta) {
    this.alta = alta;
  }

  @Operation(
      summary = "Registrar una venta a nombre de un cliente",
      description =
          """
          Deja constancia de **qué le vendió la empresa a un cliente**, como un hecho que
          **todavía no está pagado**.

          **La venta nace `PENDIENTE`, y eso significa que no concede nada.** No sube de
          nivel a nadie, no habilita ninguna cuenta y no comisiona: registrar una venta
          **no cambia absolutamente nada fuera de este módulo**. Confirmarla es otra
          operación.

          **El precio no se envía: se toma del catálogo.** Se indica qué productos y
          cuántos, nunca cuánto cuestan — un precio que llegara en la petición sería un
          descuento sin autorización y sin rastro. Tampoco se envían la moneda, la
          vigencia ni el vendedor: la moneda y la vigencia salen del producto, y **el
          vendedor sale del cliente** y se congela en la venta.

          **Lo copiado queda congelado.** Corregir mañana el precio de un producto, o
          reasignar el cliente a otro agente, no cambia lo que se vendió hoy.

          Reglas de composición: **como mucho un upgrade** por venta y con cantidad uno,
          sin productos repetidos y todas las líneas en la misma moneda.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Venta registrada, pendiente de pago."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Lo que se ve mirando la petición: falta el cliente o el método de pago, no hay"
                + " líneas, la cantidad no es positiva, un producto se repite, o la fecha del"
                + " hecho está en el futuro.",
        content = @io.swagger.v3.oas.annotations.media.Content()),
    @ApiResponse(
        responseCode = "403",
        description = "Sin el permiso `movements:create`.",
        content = @io.swagger.v3.oas.annotations.media.Content()),
    @ApiResponse(
        responseCode = "409",
        description =
            "Lo que solo se sabe después de resolver: la cuenta no puede operar todavía, el"
                + " cliente no cuelga de ningún vendedor, un producto no está en su oferta, el"
                + " upgrade no sube de nivel, hay dos upgrades, las monedas difieren, o el método"
                + " de pago está desactivado.",
        content = @io.swagger.v3.oas.annotations.media.Content()),
    @ApiResponse(
        responseCode = "422",
        description =
            "Un dato bien formado que no resuelve: el cliente, un producto o el método de pago"
                + " no existen.",
        content = @io.swagger.v3.oas.annotations.media.Content())
  })
  @PostMapping
  @PreAuthorize("hasAuthority('movements:create')")
  public ResponseEntity<SaleResponse> registrar(@Valid @RequestBody RegisterSaleRequest peticion) {
    SaleResponse venta = alta.register(peticion);
    return ResponseEntity.created(URI.create("/api/v1/movements/" + venta.id())).body(venta);
  }
}
