package com.factech.nexus.modules.movements.interfaces;

import com.factech.nexus.modules.movements.application.PointsPurchaseResponse;
import com.factech.nexus.modules.movements.application.PointsRateResponse;
import com.factech.nexus.modules.movements.application.PointsRequests;
import com.factech.nexus.modules.movements.domain.models.IdempotencyKey;
import com.factech.nexus.modules.movements.domain.service.PointsPurchaseService;
import com.factech.nexus.modules.movements.domain.service.PointsRateService;
import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La etapa 3 de `MV` (`requirements/mv.md` §4.4): las tasas de puntos y la compra de puntos. Bajo
 * {@code /movements} porque la compra <b>es un movimiento</b>, y la tasa solo existe para ella.
 */
@Tag(
    name = "Puntos",
    description =
        "A cuánto se venden los puntos en cada moneda y su compra. Confirmar o rechazar su pago es"
            + " `POST /api/v1/movements/payments/{paymentId}/…`, como el de una venta. Pagar con"
            + " puntos no tiene ruta propia: es el método `POINTS` en las compras.")
@RestController
@RequestMapping("/api/v1/movements")
public class PointsController {

  private final PointsRateService tasas;
  private final PointsPurchaseService compras;

  public PointsController(PointsRateService tasas, PointsPurchaseService compras) {
    this.tasas = tasas;
    this.compras = compras;
  }

  @PostMapping("/points-rates")
  @PreAuthorize("hasAuthority('movements:set-points-rate')")
  @Operation(
      summary = "Fijar la tasa de puntos de una moneda",
      description =
          """
          Fija cuántos puntos da **una unidad** de una moneda —`100` es «1 = 100 puntos»—, con
          hasta cuatro decimales (`RF-MV-025`). **Rige desde ese instante**, y la anterior **no
          se toca**: queda en el histórico, porque explica las compras que se hicieron con ella
          (`RN-MV-050`). Fijar la que ya rige responde `200` con ella y no escribe nada. Solo en
          monedas activas.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Tasa nueva, vigente desde ahora."),
    @ApiResponse(responseCode = "200", description = "Ya era la vigente: nada cambió."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Moneda ausente, o valor ausente, no positivo, con más de cuatro decimales o más de"
                + " ocho cifras enteras. Los errores salen juntos",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:set-points-rate` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "La moneda está inactiva (`EX-003`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "La moneda no existe (`EX-002`)",
        content = @Content)
  })
  public ResponseEntity<PointsRateResponse> fijarTasa(
      @RequestBody(required = false) PointsRequests.SetRate peticion) {
    PointsRateService.SetResult hecho = tasas.set(peticion);
    return hecho.created()
        ? ResponseEntity.created(URI.create("/api/v1/movements/points-rates")).body(hecho.rate())
        : ResponseEntity.ok(hecho.rate());
  }

  @GetMapping("/points-rates")
  @PreAuthorize("hasAuthority('movements:read-points-rates')")
  @Operation(
      summary = "Consultar las tasas de puntos vigentes",
      description =
          """
          La tasa que rige hoy en cada moneda **activa** que venda puntos, ordenadas por código
          de moneda y sin paginar (`RF-MV-026`). El histórico no se publica. Con ella se calcula
          cuántos puntos da una compra y cuántos cuesta pagar con ellos; el cálculo definitivo
          lo hace la operación, con la tasa de ese momento.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Las vigentes; vacía si ninguna."),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:read-points-rates` (`AUTH-002`)",
        content = @Content)
  })
  public List<PointsRateResponse> tasasVigentes() {
    return tasas.current();
  }

  @PostMapping("/mine/points-purchases")
  @PreAuthorize("hasAuthority('movements:buy-points')")
  @Operation(
      summary = "Comprar puntos",
      description =
          """
          Compra puntos para quien tiene la sesión, pagando un **importe** en una moneda con un
          método de pago (`RF-MV-027`). **La compra nace `PENDIENTE` y no abona nada**: los
          puntos llegan cuando administración confirme que el pago entró (`RF-MV-028`). Lo que
          sí queda fijado desde ya es **cuántos**: `amount × tasa vigente`, redondeado hacia
          abajo, a la tasa de este momento aunque cambie después (`RN-MV-051`). No se paga con
          `POINTS` ni con un método interno. La cabecera `Idempotency-Key` es obligatoria: la
          misma petición repetida responde `200` con la compra ya registrada. No comisiona.

          **Con tarjeta (`CREDIT_CARD`), desde el 01-10-2026, abre el cobro en la pasarela** en el mismo
          acto (`RF-MV-040`): la respuesta trae `cardCharge.clientSecret`, con el que la app pide la
          tarjeta con Stripe Elements. **Nada queda confirmado**: lo confirma la notificación de la
          pasarela. Si la pasarela no responde, `503` y no se registra nada; por debajo del mínimo
          (0,50 USD), `422`. Con la pasarela apagada, el pago queda pendiente sin cobro, como antes.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Compra registrada, pendiente."),
    @ApiResponse(responseCode = "200", description = "La misma petición repetida."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Datos ausentes o malformados, clave ausente o malformada, o un importe que no da"
                + " ningún punto",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:buy-points` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "Moneda inactiva o sin tasa (`EX-003`); método desactivado, interno o `POINTS`"
                + " (`EX-005`); cuenta que todavía no opera (`EX-006`); o la clave es de otra"
                + " petición (`EX-008`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "La moneda (`EX-002`) o el método (`EX-004`) no existen",
        content = @Content),
    @ApiResponse(
        responseCode = "503",
        description =
            "La pasarela de pago no respondió; nada se escribió y se puede reintentar"
                + " (`RN-MV-057`)",
        content = @Content)
  })
  public ResponseEntity<PointsPurchaseResponse> comprarPuntos(
      @RequestBody(required = false) PointsRequests.Purchase peticion,
      @Parameter(in = ParameterIn.HEADER, required = true, description = "Una por compra.")
          @RequestHeader(value = IdempotencyKey.CABECERA, required = false)
          String clave) {
    PointsPurchaseService.BuyResult hecho = compras.buy(peticion, clave);
    if (!hecho.created()) {
      return ResponseEntity.ok(hecho.purchase());
    }
    return ResponseEntity.created(URI.create("/api/v1/movements/" + hecho.purchase().id()))
        .body(hecho.purchase());
  }

  @GetMapping("/mine/points-purchases")
  @PreAuthorize("hasAuthority('movements:list-own-points-purchases')")
  @Operation(
      summary = "Consultar mis compras de puntos",
      description =
          """
          Las compras de puntos de quien tiene la sesión, **las más recientes primero**, cada
          una con su tasa, sus puntos, sus pagos y, si se rechazó, el motivo (`RF-MV-031`).
          Filtros: `status` (`PENDIENTE`, `CONFIRMADA`, `RECHAZADA`), `currencyId`, `code` —un
          fragmento del comprobante, sin distinguir mayúsculas— y `from`/`to` sobre cuándo se
          compró, rango semiabierto. Un estado desconocido y un periodo invertido son `400`, y
          los dos errores salen juntos.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Una página de compras."),
    @ApiResponse(
        responseCode = "400",
        description = "Estado desconocido (`VAL-001`) o `from` posterior a `to` (`VAL-002`)",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:list-own-points-purchases` (`AUTH-002`)",
        content = @Content)
  })
  public PageResponse<PointsPurchaseResponse> misComprasDePuntos(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID currencyId,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to) {
    return compras.listMine(page, size, status, currencyId, code, from, to);
  }
}
