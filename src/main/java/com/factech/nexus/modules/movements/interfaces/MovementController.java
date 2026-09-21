package com.factech.nexus.modules.movements.interfaces;

import com.factech.nexus.modules.movements.application.ListMovementsRequest;
import com.factech.nexus.modules.movements.application.ListSalesRequest;
import com.factech.nexus.modules.movements.application.MovementResponse;
import com.factech.nexus.modules.movements.application.MyMovementResponse;
import com.factech.nexus.modules.movements.application.MyMovementsRequest;
import com.factech.nexus.modules.movements.application.MyProductResponse;
import com.factech.nexus.modules.movements.application.MyProductsRequest;
import com.factech.nexus.modules.movements.application.RegisterSaleRequest;
import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.application.VoidSaleRequest;
import com.factech.nexus.modules.movements.domain.service.ConfirmSaleService;
import com.factech.nexus.modules.movements.domain.service.GetMyMovementService;
import com.factech.nexus.modules.movements.domain.service.ListMovementsService;
import com.factech.nexus.modules.movements.domain.service.ListMyMovementsService;
import com.factech.nexus.modules.movements.domain.service.ListMyProductsService;
import com.factech.nexus.modules.movements.domain.service.ListSalesService;
import com.factech.nexus.modules.movements.domain.service.RegisterSaleService;
import com.factech.nexus.modules.movements.domain.service.VoidSaleService;
import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.OffsetDateTime;
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
  private final ConfirmSaleService confirmacion;
  private final VoidSaleService anulacion;
  private final ListMovementsService libro;
  private final ListMyMovementsService listado;
  private final ListMyProductsService comprado;
  private final GetMyMovementService detalle;
  private final ListSalesService ventas;

  public MovementController(
      RegisterSaleService alta,
      ConfirmSaleService confirmacion,
      VoidSaleService anulacion,
      ListMovementsService libro,
      ListMyMovementsService listado,
      ListMyProductsService comprado,
      GetMyMovementService detalle,
      ListSalesService ventas) {
    this.alta = alta;
    this.confirmacion = confirmacion;
    this.anulacion = anulacion;
    this.libro = libro;
    this.listado = listado;
    this.comprado = comprado;
    this.detalle = detalle;
    this.ventas = ventas;
  }

  /**
   * <b>{@code POST …/confirmation} y no {@code PATCH …/status}</b>, aunque seis recursos del
   * sistema cambian de estado con el segundo: aquellos tienen un permiso para todas sus
   * transiciones, y aquí confirmar y rechazar comparten permiso y anular tiene el suyo. Un {@code
   * PATCH /status} con tres valores tendría dos modelos de seguridad en un endpoint. El precedente
   * que encaja es {@code POST /{id}/deletion}: una acción con nombre y con su permiso.
   */
  @PostMapping("/{id}/confirmation")
  @PreAuthorize("hasAuthority('movements:confirm')")
  @Operation(
      summary = "Confirmar el pago de una venta pendiente",
      description =
          """
          Da por **pagada** una venta pendiente y, en el mismo acto, **entrega lo que se
          pueda entregar**. Sin cuerpo: confirmar es un hecho, no un formulario — el importe
          es el de la venta, la fecha es ahora y el método ya está en ella.

          **Lo que pasa con cada línea** (`RN-MV-030`), y se ve en la respuesta:
          - `implementation: MANUAL` → queda `PENDIENTE` de autorización (`RN-MV-021`). La
            venta confirma igual.
          - `AUTOMATICA` y **no** es un upgrade → `ENTREGADA`, con `deliveredAt` ahora.
          - `AUTOMATICA` y es un upgrade → **se concede la membresía** destino del producto,
            con la vigencia copiada en la línea **contada desde la confirmación** (`RN-MV-020`),
            cerrando la que la persona tenía — también al renovar el mismo nivel—; **salvo que
            la comprada sea inferior a la vigente en ese instante**: entonces la venta cobra
            igual y la línea queda `RETENIDA` con `deliveryNote` (`RN-MV-029`). Nunca baja de
            nivel a nadie.

          **Confirmar dos veces concede una vez.** La transición es atómica y condicionada al
          estado anterior: la segunda confirmación —o un webhook reentregado— recibe `409`
          diciendo en qué estado está, y **no cambia nada**.

          **Lo que NO hace**: no saca a nadie de `FTD_PENDIENTE` (eso lo hace el primer
          depósito), no devenga comisiones, no adjunta comprobante y no se puede deshacer
          (`RN-MV-005`).
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Confirmada. El cuerpo dice qué se entregó, qué espera y qué se retuvo."),
    @ApiResponse(
        responseCode = "400",
        description = "Identificador malformado (`VAL-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin el permiso `movements:confirm`.",
        content = @Content),
    @ApiResponse(responseCode = "404", description = "No existe (`EX-001`)", content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "No está pendiente (`EX-002`): ya confirmada, rechazada o anulada. El mensaje dice"
                + " en qué estado está, y nada cambió.",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Conceder la membresía falló; nada quedó escrito (`ERR-500`)",
        content = @Content)
  })
  public SaleResponse confirmar(@PathVariable UUID id) {
    return confirmacion.confirm(id);
  }

  /**
   * La misma forma que {@code …/confirmation}: una acción con nombre y con <b>su</b> permiso.
   * `movements:void` y no `movements:confirm`, porque quien concilia pagos no tiene por qué poder
   * hacer desaparecer del embudo ventas ajenas (`requirements/mv.md` §6).
   */
  @PostMapping("/{id}/voiding")
  @PreAuthorize("hasAuthority('movements:void')")
  @Operation(
      summary = "Anular una venta pendiente",
      description =
          """
          Saca del embudo una venta pendiente que **no debía existir** —se registró por error,
          al cliente equivocado, con el producto equivocado— dejando escrito **cuándo y por
          qué**. `reason` es **obligatorio** (hasta 500 caracteres) y se verifica antes de
          tocar la venta.

          **Anular no es rechazar**: una rechazada es un cobro que se intentó y no entró;
          una anulada es una venta que nunca debió estar. Y **anular no es borrar**: la venta
          se queda con su código, sus líneas y sus importes, en estado `ANULADA`, que es
          final. Solo se anula lo **pendiente**: una confirmada ya entregó, y deshacerlo es
          una operación que no existe.

          Nada se retira: una pendiente no había concedido nada. Sus líneas siguen
          `PENDIENTE` de entrega y el registro de lo comprado las muestra `ANULADO`.
          Anular dos veces responde `409` diciendo el estado, y no cambia nada.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Anulada, con `voidedAt` y `voidReason`."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador malformado (`VAL-001`), motivo vacío (`VAL-002`) o demasiado largo"
                + " (`VAL-003`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin el permiso `movements:void` (tener `movements:confirm` no basta).",
        content = @Content),
    @ApiResponse(responseCode = "404", description = "No existe (`EX-001`)", content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "No está pendiente (`EX-002`): ya confirmada, rechazada o anulada. El mensaje dice"
                + " en qué estado está, y nada cambió.",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public SaleResponse anular(
      @PathVariable UUID id, @RequestBody(required = false) VoidSaleRequest peticion) {
    return anulacion.voidSale(id, peticion == null ? null : peticion.reason());
  }

  /**
   * <b>La anotación de permiso es la única línea que separa esta operación de publicar el libro
   * entero a cualquier autenticado</b> (`RF-MV-006` · `plan.md` §5). No hay alcance en la
   * sentencia: aquí el sujeto y el vendedor son filtros, y lo que cierra la puerta es esto.
   * `CA-MV-069` lo ejercita con un actor que sí tiene movimientos propios, y {@code
   * EndpointPermissionsIT} es la segunda red.
   */
  @GetMapping
  @PreAuthorize("hasAuthority('movements:read')")
  @Operation(
      summary = "Consultar todos los movimientos",
      description =
          """
          Devuelve **todos los movimientos del libro**, de quien sean, paginados y del más
          reciente al más antiguo. Es la lectura de administración: exige `movements:read`,
          y con él se ve todo — quien no lo tiene recibe `403` aunque tenga movimientos
          propios, que se consultan por `/mine`.

          **Los filtros se combinan** y responden una pregunta de operación cada uno:
          `status` (qué está pendiente de confirmar), `userId` (qué compró esta persona —el
          SUJETO, a nombre de quién es—), `sellerId` (qué vendió esta persona, como vendedora
          de **alguna de sus líneas**; una venta con varias líneas suyas aparece **una vez**),
          `paymentMethodId` (qué entró por un medio de pago), `code` (un comprobante exacto,
          sin distinguir mayúsculas) y `from`/`to` sobre **cuándo ocurrió**. `from` y `to` son
          instantes con zona horaria y el rango es **semiabierto** —incluye `from`, excluye
          `to`—. Un `userId`, `sellerId` o `paymentMethodId` que no exista da una página vacía;
          un `status` que no exista es `400`. Desde el 21-09-2026, `type` (qué depósitos hubo,
          el día que los haya): **el código del tipo de movimiento**, sin distinguir
          mayúsculas — hoy el único es `VENTA`, y filtrar por él devuelve lo mismo que no
          filtrar. Un `type` que no exista en el catálogo es `400`, como el estado y al revés
          que las personas: el catálogo es cerrado. El catálogo no se publica por ninguna
          ruta; los códigos vigentes son los que este párrafo nombra.

          **Cada fila lleva el tipo de movimiento** (`type`, hoy siempre `VENTA`), el sujeto
          (`user`), los vendedores de sus líneas sin repetir (`sellers`, lista nunca nula y
          vacía cuando no hay ninguno), y **cuándo se confirmó** (`confirmedAt`): presente en
          las confirmadas y **nulo** en las demás. No lleva `role` —quien administra no
          participa en lo que mira— ni las líneas, que son del detalle.

          **El total puede no ser exacto.** El libro crece sin límite, y por encima del techo
          de conteo `totalElements` vale el techo y `totalIsExact` es `false`: hay «más de N»
          y conviene acotar. El orden es fijo y no se puede cambiar.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La página de movimientos."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Paginación inválida, estado no admitido (`VAL-002`), identificador malformado"
                + " (`VAL-001`), `from` posterior a `to` (`VAL-004`) o tipo de movimiento"
                + " inexistente (`VAL-005`). Los problemas se devuelven juntos.",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin el permiso `movements:read`, tenga o no movimientos propios.",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public PageResponse<MovementResponse> todos(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) UUID userId,
      @RequestParam(required = false) UUID sellerId,
      @RequestParam(required = false) UUID paymentMethodId,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to) {
    return libro.list(
        new ListMovementsRequest(
            page, size, status, type, userId, sellerId, paymentMethodId, code, from, to));
  }

  /**
   * <b>El permiso abre; el alcance decide qué se ve</b> (`RF-MV-015` · `plan.md` §5). La anotación
   * es la puerta (`RN-SEG-015`) y {@code CommercialReach} es lo que hace que un director y un
   * manager, con el mismo permiso, vean conjuntos distintos (`RN-MV-031`). No se admite {@code
   * movements:read} como alternativa: rompería `RN-SEG-014` en silencio (`CA-MV-130`).
   */
  @GetMapping("/sales")
  @PreAuthorize("hasAuthority('movements:list-sales')")
  @Operation(
      summary = "Consultar las ventas de mi alcance",
      description =
          """
          Devuelve **las ventas que le tocan a quien pregunta según quién es**, paginadas y del
          más reciente al más antiguo. Es la consulta de las **ventas**; los otros tipos de
          movimiento tendrán la suya.

          **Lo que se ve lo decide el tipo de rol de quien pregunta**, y no un parámetro:
          quien porta un rol de tipo **funcionario** ve todas las ventas; quien porta uno de
          tipo **vendedor** ve las que vendió **él o alguien de su red** —quienes cuelgan de él
          en la estructura comercial vigente, **en toda la profundidad**: el director lo de sus
          agentes, el manager lo de sus directores y por ellos lo de los agentes—; quien porta
          uno de tipo **consumidor** ve solo las ventas **a su nombre**. Un vendedor sin nadie a
          cargo ve lo que vendió él. Un vendedor **no ve aquí lo que compró**: eso es `/mine`.

          **`userId` es una persona de mi red como vendedora** —«las ventas de mi agente tal»—
          y acota **dentro** del alcance: una persona fuera de mi red, o inexistente, da una
          **página vacía** y no un error, para que el filtro no sirva para descubrir quién
          cuelga de quién. `status` y `from`/`to` son los de `GET /movements` y se combinan.

          **Cada fila es la misma de `GET /movements`** (`type` siempre `VENTA`, `user`,
          `sellers`, importes, `confirmedAt` nulo y presente), sin `role`. **El total puede no
          ser exacto** por encima del techo de conteo. Ni `movements:read` ni
          `movements:list-own` abren esta consulta.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La página de ventas, aunque esté vacía."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Paginación inválida, estado no admitido (`VAL-002`), identificador malformado"
                + " (`VAL-001`) o `from` posterior a `to` (`VAL-004`). Los problemas se"
                + " devuelven juntos.",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin el permiso `movements:list-sales` (`AUTH-002`).",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public PageResponse<MovementResponse> misVentas(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) UUID userId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to) {
    return ventas.list(new ListSalesRequest(page, size, userId, status, from, to));
  }

  @Operation(
      summary = "Registrar una venta a nombre de un cliente",
      description =
          """
          Deja constancia de **qué le vendió la empresa a alguien**, como un hecho que
          **todavía no está pagado**. `userId` es **a nombre de quién** es la venta —quien
          compra—, nunca quien la registra desde oficina.

          **La venta nace `PENDIENTE`, y eso significa que no concede nada.** No sube de
          nivel a nadie, no habilita ninguna cuenta y no comisiona: registrar una venta
          **no cambia absolutamente nada fuera de este módulo**. Confirmarla es otra
          operación.

          **El precio no se envía: se toma del catálogo.** Se indica qué productos y
          cuántos, nunca cuánto cuestan — un precio que llegara en la petición sería un
          descuento sin autorización y sin rastro. Tampoco se envían la moneda, la
          vigencia ni el vendedor: la moneda y la vigencia salen del producto, y **el
          vendedor sale de quien compra** —su superior vigente, o **él mismo** si no cuelga
          de nadie— y se congela **en cada línea** (`lines[].seller`). En una venta ninguna
          línea viene sin vendedor.

          **Lo copiado queda congelado.** Corregir mañana el precio de un producto, o
          reasignar al comprador a otro agente, no cambia lo que se vendió hoy.

          **Esta entrada no aplica descuentos.** Cada línea trae su descuento (`lineDiscount`,
          hoy cero), las rebajas que lo explican (`discounts`, hoy vacía) y el paquete del que
          salió (`packageId`, hoy nulo); la cabecera es la suma de las líneas en las tres
          cifras. La primera entrada que rebaje será la compra de paquetes.

          Reglas de composición: **como mucho un upgrade** por venta y con cantidad uno,
          sin productos repetidos y todas las líneas en la misma moneda.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Venta registrada, pendiente de pago."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Lo que se ve mirando la petición: falta el comprador o el método de pago, no hay"
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
            "Lo que solo se sabe después de resolver: la cuenta no puede operar todavía, un"
                + " producto no está en su oferta, el"
                + " upgrade BAJA de nivel —renovar el mismo sí se admite—, hay dos upgrades, las"
                + " monedas difieren, o el método"
                + " de pago está desactivado.",
        content = @io.swagger.v3.oas.annotations.media.Content()),
    @ApiResponse(
        responseCode = "422",
        description =
            "Un dato bien formado que no resuelve: el comprador, un producto o el método de pago"
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
  // `movements:list-own` desde el 21-09-2026 (`RF-SP-062`, `RN-SEG-015`); el
  // detalle es `movements:read-own`, porque RN-SEG-014 es estricto también con
  // listado y detalle. Hasta entonces, solo el token.
  @GetMapping("/mine")
  @PreAuthorize("hasAuthority('movements:list-own')")
  @Operation(
      summary = "Consultar los movimientos propios",
      description =
          """
          Devuelve **los movimientos en los que usted participó**, paginados y del más
          reciente al más antiguo.

          **«Propio» son DOS papeles.** Un movimiento lleva a su sujeto —`user`, a nombre de
          quién es— y a los vendedores de sus líneas —`sellers`—, y usted puede ser cualquiera
          de los dos — o **los dos a la vez**, si compró algo que se le atribuye, que es lo
          que ocurre siempre que compra quien no cuelga de nadie. Cada movimiento dice en qué
          papel aparece usted con `role`: `BUYER`, `SELLER` o `BOTH`. El que es las dos
          cosas **aparece una sola vez**.

          **No hay forma de preguntar por otra persona**, ni indicándola ni teniendo
          permisos: quien pregunta sale de la credencial. Consultar las ventas de terceros es
          otra operación, con su permiso.

          **Las líneas no viajan aquí.** Una venta puede llevar varias, y meterlas
          multiplicaría la respuesta por un dato que solo se mira al abrir uno: están en el
          detalle.

          **`sellers` es una lista, sin repetir y nunca nula**: el vendedor es de cada línea y
          una venta podría llevar varios. Hoy lleva uno. Va **vacía** en los movimientos que
          no tienen vendedor, que no es el caso de ninguna venta.

          **Cada fila dice su tipo** (`type`, hoy siempre `VENTA`) desde el 21-09-2026, el
          mismo día que se puede filtrar por él: `type` admite **el código del tipo de
          movimiento**, sin distinguir mayúsculas, y se combina con `status`. Un `type` que no
          exista en el catálogo es `400`, como el estado: el catálogo es cerrado y no se
          publica por ninguna ruta.

          El orden es fijo y no se puede cambiar. `status` filtra por estado.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La página de movimientos propios."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Paginación inválida (`VAL-002`), estado no admitido (`VAL-003`) o tipo de"
                + " movimiento inexistente (`VAL-004`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `movements:list-own` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public PageResponse<MyMovementResponse> mios(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String type) {
    return listado.list(new MyMovementsRequest(page, size, status, type));
  }

  /**
   * <b>Antes que {@code /mine/{id}}</b>, por lo mismo que {@code /mine} va antes que {@code /{id}}:
   * {@code products} no es un identificador, Spring resolvería igual por especificidad, y una
   * prueba fija el orden para que el síntoma de romperlo —un {@code 400} por identificador
   * inválido— no aparezca en la ruta que se acaba de estrenar.
   */
  // `movements:read-own-products` desde el 21-09-2026 (`RF-SP-062`).
  @GetMapping("/mine/products")
  @PreAuthorize("hasAuthority('movements:read-own-products')")
  @Operation(
      summary = "Consultar los productos comprados propios",
      description =
          """
          Devuelve **los productos de las ventas a su nombre**, uno por línea de venta, del más
          reciente al más antiguo, y **en qué estado está cada uno**:

          - `PENDIENTE_PAGO`: la venta no se ha confirmado; todavía no lo tiene.
          - `PENDIENTE_AUTORIZACION`: pagado, pero el producto es de implementación manual y
            alguien tiene que autorizar la entrega.
          - `ACTIVO`: entregado, con `deliveredAt` y —si caduca— `validUntil`, que es la
            entrega más la vigencia comprada. La vigencia corre **desde la entrega**, no desde
            la compra.
          - `VENCIDO`: entregado y con la vigencia pasada.
          - `RETENIDO`: pagado y **no se entregará** — `deliveryNote` dice por qué (`RN-MV-029`).
          - `RECHAZADO` / `ANULADO`: la venta terminó así.

          **Solo lo que compró usted** —el sujeto de la venta—: lo que vendió a otros no
          aparece aquí (está en `/movements/mine` con papel `SELLER`). Dos compras del mismo
          producto son dos filas, cada una con su vigencia. El nombre es **el que tenía el
          producto el día de la compra**. `state` filtra por estado; el orden es fijo.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La página de productos comprados."),
    @ApiResponse(
        responseCode = "400",
        description = "Paginación inválida o estado no admitido (`VAL-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `movements:read-own-products` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public PageResponse<MyProductResponse> misProductos(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String state) {
    return comprado.list(new MyProductsRequest(page, size, state));
  }

  /**
   * <b>Un movimiento ajeno responde {@code 404} y no {@code 403}</b>, igual que uno inexistente
   * (`EX-002`). Un {@code 403} diría «existe pero no es tuyo», y con un identificador que alguien
   * esté probando eso ya es información.
   */
  // `movements:read-own` desde el 21-09-2026 (`RF-SP-062`). El 404 del ajeno
  // sigue saliendo del servicio: el permiso abre la ruta, el alcance es el mismo.
  @GetMapping("/mine/{id}")
  @PreAuthorize("hasAuthority('movements:read-own')")
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
        responseCode = "403",
        description = "Autenticado sin `movements:read-own` (`AUTH-002`)",
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
