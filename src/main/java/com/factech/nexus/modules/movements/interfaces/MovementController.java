package com.factech.nexus.modules.movements.interfaces;

import com.factech.nexus.modules.movements.application.MyMovementResponse;
import com.factech.nexus.modules.movements.application.MyMovementsRequest;
import com.factech.nexus.modules.movements.application.RegisterSaleRequest;
import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.domain.service.GetMyMovementService;
import com.factech.nexus.modules.movements.domain.service.ListMyMovementsService;
import com.factech.nexus.modules.movements.domain.service.RegisterSaleService;
import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
  private final ListMyMovementsService listado;
  private final GetMyMovementService detalle;

  public MovementController(
      RegisterSaleService alta, ListMyMovementsService listado, GetMyMovementService detalle) {
    this.alta = alta;
    this.listado = listado;
    this.detalle = detalle;
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

  /**
   * <b>Va declarado antes que cualquier variable de ruta a propósito.</b> Hoy este controlador no
   * tiene ninguna, pero `RF-MV-007` traerá {@code GET /api/v1/movements/{id}} y entonces {@code
   * mine} empezaría a parecerse a un identificador. Spring resuelve por especificidad —el segmento
   * literal gana— de modo que <b>funcionará igual</b>; lo que se declara aquí es el orden en que se
   * escribe, para que quien lea el archivo lo entienda. Una prueba lo fija, porque el síntoma de
   * romperlo sería un {@code 400} por identificador inválido en la ruta que más se usa.
   *
   * <p><b>{@code mine} y no {@code me}</b>: `SP` usa {@code /users/me} porque el recurso <b>es</b>
   * la persona. Aquí el recurso son los movimientos, y {@code me} no es uno de ellos.
   */
  @GetMapping("/mine")
  @Operation(
      summary = "Consultar los movimientos propios",
      description =
          """
          Devuelve **los movimientos en los que usted participó**, paginados y del más
          reciente al más antiguo.

          **«Propio» son DOS papeles.** Un movimiento lleva a quien recibe lo comprado y a
          quien lo vendió, y usted puede ser cualquiera de los dos — o **los dos a la vez**,
          si compró para sí mismo algo que se le atribuye. Cada movimiento dice en qué papel
          aparece usted con `role`: `BUYER`, `SELLER` o `BOTH`. El que es las dos cosas
          **aparece una sola vez**.

          **No hay forma de preguntar por otra persona**, ni indicándola ni teniendo
          permisos: quien pregunta sale de la credencial. Consultar las ventas de terceros es
          otra operación, con su permiso.

          **Las líneas no viajan aquí.** Una venta puede llevar varias, y meterlas
          multiplicaría la respuesta por un dato que solo se mira al abrir uno: están en el
          detalle.

          **El vendedor puede ser nulo**, y viaja igual: es el caso normal de quien no cuelga
          de ningún vendedor.

          El orden es fijo y no se puede cambiar. `status` filtra por estado.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La página de movimientos propios."),
    @ApiResponse(
        responseCode = "400",
        description = "Paginación inválida (`VAL-002`) o estado no admitido (`VAL-003`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public PageResponse<MyMovementResponse> mios(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String status) {
    return listado.list(new MyMovementsRequest(page, size, status));
  }

  /**
   * <b>Un movimiento ajeno responde {@code 404} y no {@code 403}</b>, igual que uno inexistente
   * (`EX-002`). Un {@code 403} diría «existe pero no es tuyo», y con un identificador que alguien
   * esté probando eso ya es información.
   */
  @GetMapping("/mine/{id}")
  @Operation(
      summary = "Consultar el detalle de un movimiento propio",
      description =
          """
          Devuelve **lo mismo que devuelve registrar una venta**, con sus líneas: qué
          productos, cuántos, a qué precio y con qué vigencia. Quien registró una venta y
          quien la consulta después ven la misma forma.

          **Un movimiento que no es suyo responde `404`**, exactamente igual que uno que no
          existe. No es un descuido: un `403` confirmaría que el identificador existe.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El movimiento, con sus líneas."),
    @ApiResponse(
        responseCode = "400",
        description = "Identificador malformado (`VAL-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe **o no es suyo** (`VAL-002`). Las dos son la misma respuesta",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public SaleResponse mio(@PathVariable UUID id) {
    return detalle.get(id);
  }
}
