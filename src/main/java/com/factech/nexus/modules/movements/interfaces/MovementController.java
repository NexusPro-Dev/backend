package com.factech.nexus.modules.movements.interfaces;

import com.factech.nexus.modules.movements.application.AssignSellersRequest;
import com.factech.nexus.modules.movements.application.ListMovementsRequest;
import com.factech.nexus.modules.movements.application.ListSalesRequest;
import com.factech.nexus.modules.movements.application.MovementResponse;
import com.factech.nexus.modules.movements.application.MyMovementResponse;
import com.factech.nexus.modules.movements.application.MyMovementsRequest;
import com.factech.nexus.modules.movements.application.MyProductResponse;
import com.factech.nexus.modules.movements.application.MyProductsRequest;
import com.factech.nexus.modules.movements.application.RegisterSaleRequest;
import com.factech.nexus.modules.movements.application.SaleLineItem;
import com.factech.nexus.modules.movements.application.SaleLinesRequest;
import com.factech.nexus.modules.movements.application.SaleResponse;
import com.factech.nexus.modules.movements.application.VoidSaleRequest;
import com.factech.nexus.modules.movements.domain.models.IdempotencyKey;
import com.factech.nexus.modules.movements.domain.service.ActivateMyProductService;
import com.factech.nexus.modules.movements.domain.service.AssignSellersService;
import com.factech.nexus.modules.movements.domain.service.GetMovementService;
import com.factech.nexus.modules.movements.domain.service.GetMyMovementService;
import com.factech.nexus.modules.movements.domain.service.ListMovementsService;
import com.factech.nexus.modules.movements.domain.service.ListMyMovementsService;
import com.factech.nexus.modules.movements.domain.service.ListMyProductsService;
import com.factech.nexus.modules.movements.domain.service.ListSaleLinesService;
import com.factech.nexus.modules.movements.domain.service.ListSalesService;
import com.factech.nexus.modules.movements.domain.service.RegisterSaleService;
import com.factech.nexus.modules.movements.domain.service.VoidSaleService;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
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
import org.springframework.web.bind.annotation.RequestHeader;
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
  private final VoidSaleService anulacion;
  private final ListMovementsService libro;
  private final ListMyMovementsService listado;
  private final ListMyProductsService comprado;
  private final GetMyMovementService detalle;
  private final ListSalesService ventas;
  private final AssignSellersService asignacion;
  private final ListSaleLinesService lineas;
  private final ActivateMyProductService activacion;
  private final GetMovementService comprobante;
  private final AuthenticatedActor actor;

  public MovementController(
      RegisterSaleService alta,
      VoidSaleService anulacion,
      ListMovementsService libro,
      ListMyMovementsService listado,
      ListMyProductsService comprado,
      GetMyMovementService detalle,
      ListSalesService ventas,
      AssignSellersService asignacion,
      ListSaleLinesService lineas,
      ActivateMyProductService activacion,
      GetMovementService comprobante,
      AuthenticatedActor actor) {
    this.alta = alta;
    this.anulacion = anulacion;
    this.libro = libro;
    this.listado = listado;
    this.comprado = comprado;
    this.detalle = detalle;
    this.ventas = ventas;
    this.asignacion = asignacion;
    this.lineas = lineas;
    this.activacion = activacion;
    this.comprobante = comprobante;
    this.actor = actor;
  }

  /**
   * La misma forma que {@code /movements/payments/{id}/confirmation}: una acción con nombre y con
   * <b>su</b> permiso. `movements:void` y no `movements:confirm-payment`, porque quien concilia
   * pagos no tiene por qué poder hacer desaparecer del embudo ventas ajenas (`requirements/mv.md`
   * §6).
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

          **Desde el 01-10-2026, si el pago pendiente tiene cobro abierto en la pasarela, primero lo
          cancela** (`RN-MV-058`): si la pasarela ya lo cobró, `409` (`EX-005`) y no se anula; si no
          responde, `503` (`EX-006`) y tampoco.
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
        description =
            "Sin el permiso `movements:void` (tener `movements:confirm-payment` no basta).",
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
        content = @Content),
    @ApiResponse(
        responseCode = "503",
        description =
            "La pasarela de pago no respondió; nada se escribió y se puede reintentar"
                + " (`RN-MV-057`)",
        content = @Content)
  })
  public SaleResponse anular(
      @PathVariable UUID id, @RequestBody(required = false) VoidSaleRequest peticion) {
    return anulacion.voidSale(id, peticion == null ? null : peticion.reason());
  }

  /**
   * La misma forma que {@code /movements/payments/{id}/confirmation} y {@code …/voiding}: una
   * acción con nombre y con <b>su</b> permiso (`RN-SEG-014`). `movements:assign-sellers` y no
   * `movements:confirm-payment`: confirmar responde «¿entró el dinero?» y esto «¿a quién se le
   * paga?» (`RF-MV-016` · `plan.md` §5).
   */
  @PostMapping("/{id}/seller-assignments")
  @PreAuthorize("hasAuthority('movements:assign-sellers')")
  @Operation(
      summary = "Asignar los vendedores de una venta",
      description =
          """
          Atribuye cada línea de una venta a **uno de los vendedores de quien compra**. Es lo
          que saca de `VALIDAR_COMISIONES` a una venta cuyo cliente tenía **varios**
          vendedores y que por eso nació con sus líneas **sin vendedor** (`RN-MV-034`).

          `lines` lleva una pareja `productId` → `sellerId` por línea —el producto identifica
          la línea dentro de la venta— y **puede ser una parte**: la venta sigue en
          `VALIDAR_COMISIONES` mientras quede alguna sin vendedor, y **pasa sola a
          `VALIDADO`** en cuanto no queda ninguna.

          **Qué se puede tocar** (`RN-MV-035`): una línea **sin vendedor** se asigna siempre,
          también después de confirmar el pago; **corregir** una que ya lo tiene se admite
          mientras la venta no esté `CONFIRMADA` y, **desde el 30-09-2026, también confirmada
          mientras su comisión no se haya pagado** (`RN-MV-053`): se le pregunta a `CM`, que
          revierte la comisión de la cadena vieja, y la de la nueva se devenga como si la línea
          se acabara de atribuir. Si algún nivel de la cadena está pagado, o la línea es un FTD
          ya contado, `409`. En una venta `RECHAZADA` o
          `ANULADA` no se asigna nada. El vendedor tiene que ser **uno de los del cliente**
          —de registro o de hotlink—: elegir a cualquiera sería atribuir la venta a quien se
          quisiera.

          **Todo o nada**: si una sola pareja no procede, no se escribe ninguna. La respuesta
          es la venta como queda, con `typeStatus` y el vendedor de cada línea.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La venta como queda: el vendedor de cada línea y `typeStatus`."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador malformado (`VAL-001`), sin líneas (`VAL-002`), una pareja sin"
                + " producto o sin vendedor (`VAL-003`) o un producto repetido (`VAL-004`).",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description =
            "Sin el permiso `movements:assign-sellers` (ni `movements:confirm-payment` ni"
                + " `movements:create` bastan).",
        content = @Content),
    @ApiResponse(responseCode = "404", description = "No existe (`EX-001`)", content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "La venta está rechazada o anulada (`EX-002`), o está confirmada y se intenta"
                + " corregir una línea cuya comisión ya se pagó o que ya se contó como FTD"
                + " (`EX-003`). Nada cambió, en ninguna línea.",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description =
            "Un producto que no es una línea de la venta (`EX-004`) o un vendedor que no es de"
                + " los del cliente (`EX-005`). Nada cambió.",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public SaleResponse asignarVendedores(
      @PathVariable UUID id, @RequestBody(required = false) AssignSellersRequest peticion) {
    return asignacion.assign(id, peticion);
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
          `paymentMethodId` (qué entró por un medio de pago), `code` (**una PARTE del
          comprobante**, sin distinguir mayúsculas: `a1b2` encuentra `VTA-A1B2C3D4`, y
          `%` y `_` son texto y no comodines, `RN-MV-037`) y `from`/`to` sobre **cuándo ocurrió**. `from` y `to` son
          instantes con zona horaria y el rango es **semiabierto** —incluye `from`, excluye
          `to`—. Un `userId`, `sellerId` o `paymentMethodId` que no exista da una página vacía;
          un `status` que no exista es `400`. Desde el 21-09-2026, `type` (qué depósitos hubo,
          el día que los haya): **el código del tipo de movimiento**, sin distinguir
          mayúsculas — hoy el único es `VENTA`, y filtrar por él devuelve lo mismo que no
          filtrar. Un `type` que no exista en el catálogo es `400`, como el estado y al revés
          que las personas: el catálogo es cerrado. El catálogo no se publica por ninguna
          ruta; los códigos vigentes son los que este párrafo nombra. Desde el 23-09-2026,
          `typeStatus` (qué ventas **faltan por validar**, `RF-MV-016`): el estado del tipo,
          sin distinguir mayúsculas — en una venta, `VALIDAR_COMISIONES` o `VALIDADO`. Uno que
          no exista es `400`, como el tipo.

          **Cada fila lleva el tipo de movimiento** (`type`, hoy siempre `VENTA`), el sujeto
          (`user`), **el estado del tipo** (`typeStatus`), los vendedores de sus líneas sin
          repetir (`sellers`, lista nunca nula y vacía cuando no hay ninguno —también en una
          venta por validar a la que no se le ha asignado ninguno—), y **cuándo se confirmó** (`confirmedAt`): presente en
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
                + " (`VAL-001`), `from` posterior a `to` (`VAL-004`), tipo de movimiento"
                + " inexistente (`VAL-005`) o estado del tipo inexistente (`VAL-006`). Los"
                + " problemas se devuelven juntos.",
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
      @RequestParam(required = false) String typeStatus,
      @RequestParam(required = false) UUID userId,
      @RequestParam(required = false) UUID sellerId,
      @RequestParam(required = false) UUID paymentMethodId,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to,
      @io.swagger.v3.oas.annotations.Parameter(
              description =
                  "La incidencia del último pago (`RN-MV-060`, desde el 01-10-2026): REEMBOLSADO,"
                      + " EN_DISPUTA, DISPUTA_GANADA, DISPUTA_PERDIDA, o CUALQUIERA para los que"
                      + " tienen alguna.")
          @RequestParam(required = false)
          String paymentIncident) {
    return libro.list(
        new ListMovementsRequest(
            page,
            size,
            status,
            type,
            typeStatus,
            userId,
            sellerId,
            paymentMethodId,
            code,
            from,
            to,
            paymentIncident));
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
          cuelga de quién. `code` acepta **una PARTE del comprobante**, sin distinguir
          mayúsculas (`RN-MV-037`). `status`, `paymentMethodId`, `code` y `from`/`to` son los de
          `GET /movements` y se combinan, y también `typeStatus` (`VALIDAR_COMISIONES` o
          `VALIDADO`, desde el 23-09-2026); **el comprobante de una venta que no es de mi
          alcance tampoco aparece**, escrito como sea. **Una venta por validar no está en el
          alcance de ningún vendedor** mientras ninguna de sus líneas sea suya: aparece en
          cuanto se le asigna una (`RF-MV-016`).

          **Cada fila es la misma de `GET /movements`** (`type` siempre `VENTA`, `typeStatus`, `user`,
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
                + " (`VAL-001`), `from` posterior a `to` (`VAL-004`) o estado del tipo"
                + " inexistente (`VAL-005`). Los problemas se devuelven juntos.",
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
      @RequestParam(required = false) String typeStatus,
      @RequestParam(required = false) UUID paymentMethodId,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to) {
    return ventas.list(
        new ListSalesRequest(
            page, size, userId, status, typeStatus, paymentMethodId, code, from, to));
  }

  /**
   * <b>Bajo {@code /sales} y no {@code /lines} sueltas</b>: se acota a las ventas. El día que un
   * depósito tenga líneas, su consulta será otra ruta y no un parámetro de esta.
   */
  @GetMapping("/sales/lines")
  @PreAuthorize("hasAuthority('movements:list-sale-lines')")
  @Operation(
      summary = "Consultar las líneas de venta",
      description =
          """
          Devuelve **una fila por LÍNEA de venta** —no por venta—, paginadas y de la más
          reciente a la más antigua. Es la consulta que responde **qué se ha vendido**:
          `GET /movements` y `GET /movements/sales` devuelven una fila por venta con sus
          importes agregados, de modo que para ver los productos hay que abrir cada una.

          **Una venta de tres productos aporta tres filas**, con los datos de la venta
          repetidos en cada una. El identificador de la línea (`lineId`) no se publica en
          ninguna otra consulta.

          **Es la lectura de ADMINISTRACIÓN: con el permiso se ve todo el libro**, de quien
          sea. **No tiene alcance por estructura** —el vendedor NO ve aquí solo su red—:
          eso es `GET /movements/sales`, y quien quiera lo suyo tiene
          `GET /movements/mine/products`. Quien no porta `movements:list-sale-lines` recibe
          `403` aunque tenga compras propias; ni `movements:read` ni `movements:list-sales`
          abren esta consulta.

          **El nombre del producto es el que tenía el día de la venta**, no el del catálogo
          de hoy: si alguien lo renombró después, aquí sigue diciendo qué se vendió. El
          **código**, en cambio, se lee del catálogo, que es inmutable.

          **`seller` puede venir presente y NULO**, y la fila **no desaparece** por eso: el
          vendedor es de la línea y hay movimientos que no lo llevan.

          **El estado de entrega va crudo** —`deliveryStatus`, `deliveredAt`,
          `deliveryNote`, `implementation`—, y no derivado como en
          `GET /movements/mine/products`: administración necesita saber por qué algo está
          donde está.

          **Solo trae las líneas de ventas CONFIRMADAS** (`RN-MV-038`, 24-09-2026). Las de
          una venta `PENDIENTE`, `ANULADA` o `RECHAZADA` **no aparecen**, y no hay forma de
          pedirlas: lo decide la consulta y no un filtro. `movementStatus` viaja en cada
          línea y dirá siempre `CONFIRMADA`.

          **Los ocho filtros se combinan** y cada uno responde una pregunta: `movementId`
          (las líneas de una venta), `userId` (qué compró esta persona, el sujeto),
          `sellerId` (qué vendió esta persona, **como vendedora de la línea**), `hasSeller`
          (`false`: **las líneas sin vendedor asignado**, las que faltan por atribuir; `true`:
          solo las que lo tienen; desde el 02-10-2026), `productId`
          (qué se vendió de este producto),
          `deliveryStatus` (el de la LÍNEA, que **no** es el de la venta: una confirmada
          tiene líneas `ENTREGADA`, `PENDIENTE` de autorización y `RETENIDA`), `typeStatus` (el estado del TIPO de la venta,
          `VALIDAR_COMISIONES` o `VALIDADO`: la pregunta «qué falta por validar»),
          `code` (**una PARTE del comprobante**, sin
          distinguir mayúsculas, `RN-MV-037`) y `from`/`to` sobre **cuándo ocurrió la venta**, con el rango
          **semiabierto** —incluye `from`, excluye `to`—. Un identificador inexistente da
          **página vacía**; un estado que no existe es `400`, porque el catálogo es cerrado.
          Los problemas de forma se devuelven **juntos**.

          **`typeStatus` se puede filtrar pero NO viaja en la fila**, y conviene saberlo antes
          de integrar: para ver el estado del tipo de una venta está
          `GET /movements` —que lo publica— y el detalle. Aquí se puede acotar por él y la
          fila no lo trae, por decisión del responsable del proyecto del 23-09-2026.

          **El total puede no ser exacto**: por encima del techo de conteo vale el techo y
          `totalIsExact` lo declara. Aquí importa más que en ningún otro listado, porque una
          venta de cinco productos son cinco filas.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La página de líneas, aunque esté vacía."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Paginación inválida, estado o estado de entrega no admitidos (`VAL-002`,"
                + " `VAL-003`), estado del tipo inexistente (`VAL-005`), `hasSeller` que no es"
                + " `true` ni `false` (`VAL-007`), identificador"
                + " malformado (`VAL-001`) o `from` posterior a `to` (`VAL-004`). Los"
                + " problemas se devuelven juntos.",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin el permiso `movements:list-sale-lines` (`AUTH-002`).",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public PageResponse<SaleLineItem> lineasDeVenta(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) UUID movementId,
      @RequestParam(required = false) UUID userId,
      @RequestParam(required = false) UUID sellerId,
      @RequestParam(required = false) String hasSeller,
      @RequestParam(required = false) UUID productId,
      @RequestParam(required = false) String deliveryStatus,
      @RequestParam(required = false) String typeStatus,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to) {
    return lineas.list(
        new SaleLinesRequest(
            page,
            size,
            movementId,
            userId,
            sellerId,
            hasSeller,
            productId,
            deliveryStatus,
            typeStatus,
            code,
            from,
            to));
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
          vendedor sale de quien compra** (`RN-MV-034`): si tiene **un** vendedor, ese va en
          cada línea (`lines[].seller`) y la venta nace `typeStatus: VALIDADO`; si tiene
          **varios**, las líneas vienen **sin vendedor** y la venta nace
          `VALIDAR_COMISIONES`, hasta que se asignen por `POST /{id}/seller-assignments`; si
          no es cliente de nadie, su superior vigente o **él mismo**, `VALIDADO`.

          **Lo copiado queda congelado.** Corregir mañana el precio de un producto, o
          reasignar al comprador a otro agente, no cambia lo que se vendió hoy.

          **Esta entrada no aplica descuentos.** Cada línea trae su descuento (`lineDiscount`,
          hoy cero), las rebajas que lo explican (`discounts`, hoy vacía) y el paquete del que
          salió (`packageId`, hoy nulo); la cabecera es la suma de las líneas en las tres
          cifras. La primera entrada que rebaje será la compra de paquetes.

          Reglas de composición: **como mucho un upgrade** por venta y con cantidad uno,
          sin productos repetidos y todas las líneas en la misma moneda.

          **Con puntos** (`POINTS`, `RF-MV-030`) solo se paga una venta **a nombre de quien la
          registra**: sus puntos se descuentan a la tasa vigente y la venta queda **confirmada**
          en la misma respuesta. A nombre de otra persona, `409`: nadie gasta los puntos de
          otro.

          **Con tarjeta no abre ningún cobro** (`RN-MV-057`, desde el 01-10-2026): no hay nadie al
          otro lado para escribirla. La venta nace pendiente y quien compró la paga desde su app con
          `POST /movements/mine/{id}/card-charge`.
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
                + " monedas difieren, el método"
                + " de pago está desactivado, o se paga con puntos a nombre de otra persona o sin"
                + " puntos suficientes.",
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
  public ResponseEntity<SaleResponse> registrar(
      @Valid @RequestBody RegisterSaleRequest peticion,
      @RequestHeader(value = IdempotencyKey.CABECERA, required = false) String clave) {
    SaleResponse venta = alta.register(peticion, IdempotencyKey.opcional(clave), actor.id());
    return ResponseEntity.created(URI.create("/api/v1/movements/" + venta.id())).body(venta);
  }

  /**
   * <b>Va declarado antes que cualquier variable de ruta a propósito.</b> Desde el 30-09-2026
   * `RF-MV-007` tiene {@code GET /api/v1/movements/{id}}, y {@code mine} se parece a un
   * identificador. Spring resuelve por especificidad —el segmento literal gana— de modo que
   * <b>funcionará igual</b>; lo que se declara aquí es el orden en que se escribe, para que quien
   * lea el archivo lo entienda. Una prueba lo fija, porque el síntoma de romperlo sería un {@code
   * 400} por identificador inválido en la ruta que más se usa.
   *
   * <p><b>{@code mine} y no {@code me}</b>: `SP` usa {@code /users/me} porque el recurso <b>es</b>
   * la persona. Aquí el recurso son los movimientos, y {@code me} no es uno de ellos.
   */
  // `movements:list-own` desde el 21-09-2026 (`RF-SP-062`, `RN-SEG-015`); el
  // detalle es `movements:read-own`, porque RN-SEG-014 es estricto también con
  // listado y detalle. Hasta entonces, solo el token.
  @GetMapping("/mine/shopping")
  @PreAuthorize("hasAuthority('movements:list-own')")
  @Operation(
      summary = "Consultar mis compras",
      description =
          """
          Devuelve **los movimientos a nombre de usted** —lo que compró—, paginados y del más
          reciente al más antiguo.

          **Esta operación se mudó aquí el 22-09-2026**, desde `GET /api/v1/movements/mine`,
          que **ya no existe**: el listado dejó de traer lo vendido ese mismo día y el nombre
          tenía que decirlo. No hay alias —cada operación exige un permiso que ninguna otra
          exige—, de modo que la ruta anterior responde `404`.

          **Desde el 22-09-2026 este listado trae SOLO lo comprado.** Hasta esa fecha traía
          también lo que usted hubiera **vendido**, y cada fila decía con `role` en qué papel
          aparecía —`BUYER`, `SELLER` o `BOTH`—. **Lo que usted vendió se consulta ahora por
          `GET /api/v1/movements/sales`**, que además le trae lo que vendió su red si usted
          tiene gente a cargo. Con la mitad de vendedor **desaparece `role`**: aquí valdría
          siempre `BUYER`. Es un cambio incompatible y está declarado.

          **Su compra a sí mismo sigue apareciendo, una sola vez.** Quien pertenece a la
          fuerza comercial y no cuelga de nadie es su propio vendedor, y lo que compra es una
          compra: sale aquí, con usted como `user` y como único `sellers`.

          **No hay forma de preguntar por otra persona**, ni indicándola ni teniendo
          permisos: quien pregunta sale de la credencial. Consultar las ventas de terceros es
          otra operación, con su permiso.

          **El detalle NO se acotó con el listado**: `GET /api/v1/movements/mine/{id}` sigue
          abriendo un movimiento suyo **de cualquiera de las dos formas**, también una venta
          que usted hizo y que este listado ya no le muestra. Es deliberado: sin eso, un
          vendedor no tendría ninguna forma de ver el detalle de lo que vendió.

          **Cada fila trae sus líneas (`lines`) desde el 03-10-2026**, con **la misma forma
          que el detalle**: producto, cantidad, precio, vigencia, descuento, vendedor y
          entrega. Así la pantalla pinta qué se compró sin abrir cada compra. La lista nunca
          es nula. **Los pagos no viajan aquí**: siguen en el detalle.

          **`sellers` es una lista, sin repetir y nunca nula**: el vendedor es de cada línea y
          una venta podría llevar varios. Hoy lleva uno. Va **vacía** en los movimientos que
          no tienen vendedor y en una venta por validar a la que aún no se le asignó ninguno.

          **Desde el 26-09-2026 son solo VENTAS** (`RN-MV-047`): los retiros, abonos y bonos
          de la persona no son compras, y se consultan por sus saldos. **El filtro `type` se
          retiró ese día**: solo podía tomar un valor útil. Cada fila sigue diciendo su tipo
          (`type`, siempre `VENTA`). El método de pago de la fila, y el del filtro, son los
          del **último pago** de la venta (`RN-MV-039`).

          **Y desde ese mismo día, los tres filtros de `GET /movements`**: `paymentMethodId`
          (uno que no exista da página vacía), `code` (**una PARTE del comprobante**, sin distinguir
          mayúsculas, `RN-MV-037`; **uno ajeno sigue sin devolver nada**: el alcance va
          antes que el filtro, y buscar por fragmento no lo ensancha) y
          `from`/`to` sobre **cuándo ocurrió**, instantes con zona horaria, rango semiabierto
          —incluye `from`, excluye `to`—; `from` posterior a `to` es `400`. Todos se combinan.

          El orden es fijo y no se puede cambiar. `status` filtra por estado.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La página de movimientos propios."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Paginación inválida (`VAL-002`), estado no admitido (`VAL-003`), `from`"
                + " posterior a `to` (`VAL-005`) o"
                + " identificador malformado (`VAL-006`, que el conversor global emite como"
                + " `VAL-001`)",
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
      @RequestParam(required = false) UUID paymentMethodId,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to) {
    return listado.list(
        new MyMovementsRequest(page, size, status, paymentMethodId, code, from, to));
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
          - `PENDIENTE_ACTIVACION`: pagado, pero el producto es de implementación manual y
            **usted** tiene que activarlo —`POST /movements/mine/products/{lineId}/activation`—.
            **Hasta el 28-09-2026 se llamaba `PENDIENTE_AUTORIZACION`**: es un cambio
            incompatible.
          - `ACTIVO`: entregado, con `deliveredAt` y —si caduca— `validUntil`, que es la
            entrega más la vigencia comprada. La vigencia corre **desde la entrega**, no desde
            la compra.
          - `VENCIDO`: entregado y con la vigencia pasada.
          - `RETENIDO`: pagado y **no se entregará** — `deliveryNote` dice por qué (`RN-MV-029`).
          - `RECHAZADO` / `ANULADO`: la venta terminó así.

          **Solo lo que compró usted** —el sujeto de la venta—: lo que vendió a otros no
          aparece aquí (está en `/movements/sales`). Dos compras del mismo
          producto son dos filas, cada una con su vigencia. `state` filtra por estado; el orden
          es fijo.

          **`product` viaja con la misma forma que en `GET /api/v1/products/available`**
          (desde el 28-09-2026): tipo, descripción, icono, portada, enlaces resueltos,
          destino, `price`, moneda, `exchange`, vigencia, alcance, implementación y
          valoración — **como está hoy en el catálogo**, también si el producto se retiró
          después. **Sin precio de compra**. Hasta esa fecha `product` traía solo `id`,
          `code` y `name`: es un cambio incompatible.

          **`product.links` depende de la línea** (`RN-MV-032`): **desde que la venta
          está pagada** —`PENDIENTE_ACTIVACION`, `ACTIVO`, `VENCIDO`, `CANCELADO` y
          `RETENIDO`— trae **todos** los enlaces del producto, **los de entrega
          incluidos**: `CUPON_BOT`, dónde registra su cuenta en el bot —sirve para
          activarlo—, y `DESCARGA`, dónde descarga lo que compró. En `PENDIENTE_PAGO`,
          `RECHAZADO` y `ANULADO` trae **los mismos que la oferta**, sin los de entrega
          aunque el producto los declare. Se leen
          del catálogo de hoy: si el cupón se añade o cambia después de la venta, llega el
          vigente. **`couponUrl` ya no existe** (desde el 28-09-2026): el cupón se busca en
          `product.links` por su `type`. Es un cambio incompatible.

          **`purchasedName` es el nombre que tenía el producto el día de la compra**
          (`RN-MV-002`); `product.name` es el de hoy.

          **Cada fila trae `lineId`** (desde el 28-09-2026): la línea de venta, que es lo que se
          activa.
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
   * <b>Antes que {@code /mine/{id}}</b>, como {@code /mine/products}: Spring resolvería igual por
   * especificidad —el segmento literal gana—, y el orden de lectura es el de las rutas.
   *
   * <p><b>La línea ajena responde {@code 404} y no {@code 403}</b>, como el detalle: el alcance va
   * en la búsqueda y ningún permiso lo ensancha (`RN-MV-048`).
   */
  // `movements:activate-own-product` desde el 28-09-2026 (`RF-MV-010`, `V50`).
  @PostMapping("/mine/products/{lineId}/activation")
  @PreAuthorize("hasAuthority('movements:activate-own-product')")
  @Operation(
      summary = "Activar un producto comprado",
      description =
          """
          Activa **un producto que usted compró** y cuya implementación es **manual**: la línea
          `lineId` de `GET /api/v1/movements/mine/products`, que aparece allí como
          `PENDIENTE_ACTIVACION`. **Sin cuerpo.**

          **Activar es entregar**: desde este instante usted lo tiene, y **la vigencia corre desde
          la activación**, no desde la compra ni desde la confirmación del pago. Si el producto
          es un **upgrade de membresía**, se le concede el nivel — **salvo que baje del que tiene
          ahora**: entonces el producto queda `RETENIDO` con el motivo en `deliveryNote`, y su
          nivel no cambia.

          **Solo lo activa quien lo compró.** Ningún permiso abre la compra de otra persona, y
          su línea responde `404`, **exactamente igual que una que no existe**.

          **No se deshace**: de una entrega no se sale. Activar dos veces responde `409` la
          segunda y entrega una sola vez.

          Devuelve el producto como queda, con la misma forma que el listado —con todos los
          enlaces del producto en `product.links`, que ya llegaban antes de activar—.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Activado —o `RETENIDO` si era un upgrade que bajaría de nivel—."),
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
        description = "Autenticado sin `movements:activate-own-product` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description =
            "No existe **o no es de una compra suya** (`EX-001`). Las dos son la misma respuesta",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "La venta no está confirmada (`EX-002`), el producto no es de implementación manual"
                + " (`EX-003`) o ya no está pendiente de activación (`EX-004`); el mensaje dice"
                + " en qué estado está",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public MyProductResponse activarProducto(@PathVariable UUID lineId) {
    return activacion.activate(lineId);
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

  /**
   * El detalle de <b>cualquier</b> movimiento (`RF-MV-007`): el `GET` del recurso que {@code POST}
   * crea y {@code GET /movements} lista. Sin alcance, con la misma forma que el detalle propio de
   * arriba, y con su propio permiso: `movements:read` es el del listado (`RN-SEG-014`).
   *
   * <p><b>La variable solo admite la forma de un UUID.</b> Sin la expresión, cualquier segmento
   * suelto bajo {@code /movements} caería aquí y respondería {@code 400} por identificador
   * malformado —{@code /movements/mine}, la ruta que `RF-MV-008` retiró el 22-09-2026 y que
   * `CA-MV-140` promete en {@code 404}—. Con ella, lo que no tiene forma de identificador no es
   * esta ruta y responde {@code 404}, como cualquier ruta que no existe (`spec.md` §11).
   */
  @GetMapping("/{id:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}")
  @PreAuthorize("hasAuthority('movements:read-detail')")
  @Operation(
      summary = "Consultar el detalle de un movimiento",
      description =
          """
          Devuelve **el comprobante de cualquier movimiento** —una venta, un retiro, un
          bono—, sea quien sea su sujeto y quien lo haya vendido: código, tipo, estado,
          estado del tipo, sujeto, moneda, totales, **pagos** y **líneas**, cada una con lo
          que se vendió **tal como se vendió** y a quién se le acredita. Un movimiento sin
          líneas —un retiro, un bono— las trae **vacías**.

          **Un retiro trae `withdrawalDestination`** desde el 01-10-2026 (`RN-MV-056`): a
          dónde se paga, **copiado al pedirlo** —entidad, tipo de cuenta, número y titular con
          su documento—. Es lo que lee quien lo aprueba para saber a dónde enviar el dinero, y
          no cambia aunque la cuenta se edite o se dé de baja. Falta en todo lo demás y en los
          retiros pedidos antes de esa fecha.

          **Es la misma forma que `GET /api/v1/movements/mine/{id}`**, el detalle propio:
          sobre un movimiento en el que usted participó, las dos responden lo mismo. Aquí
          no hay alcance —quien tiene el permiso abre cualquier fila del libro— y por eso
          tampoco hay un «no es suyo»: lo único que responde `404` es lo que no existe, o un
          identificador que no tiene forma de identificador.

          **El comprobante no es un documento fiscal** ni un soporte de pago adjunto: es el
          documento interno del movimiento.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El comprobante del movimiento."),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin `movements:read-detail` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description =
            "No existe ningún movimiento con ese identificador (`EX-001`), o el identificador"
                + " no tiene forma de UUID",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public SaleResponse movimiento(@PathVariable UUID id) {
    return comprobante.get(id);
  }
}
