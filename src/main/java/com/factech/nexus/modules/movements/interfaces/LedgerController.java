package com.factech.nexus.modules.movements.interfaces;

import com.factech.nexus.modules.movements.application.BalancesResponse;
import com.factech.nexus.modules.movements.application.LedgerMovementResponse;
import com.factech.nexus.modules.movements.application.WithdrawalRequests;
import com.factech.nexus.modules.movements.application.WithdrawalResponse;
import com.factech.nexus.modules.movements.domain.models.IdempotencyKey;
import com.factech.nexus.modules.movements.domain.service.BalanceService;
import com.factech.nexus.modules.movements.domain.service.CreditService;
import com.factech.nexus.modules.movements.domain.service.WithdrawalService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La etapa 6 de `MV` (`requirements/mv.md` §4.3): los saldos de cada persona, el retiro y el bono.
 * Bajo el mismo recurso {@code /movements} porque el retiro y el bono <b>son movimientos</b>.
 */
@Tag(
    name = "Saldos",
    description =
        "Lo que cada persona tiene en la plataforma —billetera, retenido y puntos—, los retiros y"
            + " los bonos.")
@RestController
@RequestMapping("/api/v1/movements")
public class LedgerController {

  private final WithdrawalService retiros;
  private final BalanceService saldos;
  private final CreditService abonos;

  public LedgerController(WithdrawalService retiros, BalanceService saldos, CreditService abonos) {
    this.retiros = retiros;
    this.saldos = saldos;
    this.abonos = abonos;
  }

  @PostMapping("/mine/withdrawals")
  @PreAuthorize("hasAuthority('movements:request-withdrawal')")
  @Operation(
      summary = "Solicitar un retiro",
      description =
          """
          Pide que le paguen a la persona lo que tiene en su **billetera**, en una moneda
          (`RF-MV-019`). **El importe se aparta en el acto**: sale de la billetera y queda
          **retenido** hasta que alguien lo apruebe o lo niegue (`RN-MV-043`), de modo que no
          se puede gastar ni pedir dos veces. Si la billetera no alcanza, `409` y no queda
          nada registrado.

          **Desde el 01-10-2026 el retiro dice a dónde se paga** (`RN-MV-056`): exige una
          **cuenta de cobro** propia y viva, de una entidad activa —`payoutAccountId`, o la
          **principal** si no viene— y que la persona tenga documento. **Sin ninguna cuenta no
          se puede pedir**: hay que registrar una antes (`POST /movements/mine/payout-accounts`).
          El destino —entidad, tipo de cuenta, número y titular— **se copia al pedir** y no
          cambia aunque la cuenta se edite o se dé de baja. Ninguna de estas comprobaciones
          toca un saldo.

          El retiro nace `PENDIENTE`. La respuesta trae el retiro, **los tres saldos** de esa
          moneda como quedan y **el destino**. Sin clave de idempotencia: un retiro repetido
          retiene dos veces y no puede pagar de más.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Retiro pedido, con los saldos y el destino."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Moneda o importe ausentes, importe no positivo o con decimales de más, o una cuenta"
                + " de cobro que no es un identificador",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:request-withdrawal` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "La billetera no alcanza, con el disponible (`EX-003`); la cuenta no opera"
                + " (`EX-004`); no tiene ninguna cuenta de cobro (`EX-006`); la entidad de la"
                + " cuenta está desactivada (`EX-008`); o la persona no tiene documento"
                + " (`EX-009`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description =
            "La moneda no existe (`EX-002`), o la cuenta de cobro indicada no existe, no es"
                + " suya o está dada de baja (`EX-007`)",
        content = @Content)
  })
  public ResponseEntity<WithdrawalResponse> solicitarRetiro(
      @RequestBody(required = false) WithdrawalRequests.Request peticion) {
    WithdrawalResponse hecho = retiros.request(peticion);
    return ResponseEntity.created(URI.create("/api/v1/movements/mine/" + hecho.movement().id()))
        .body(hecho);
  }

  @PostMapping("/{id}/withdrawal-approval")
  @PreAuthorize("hasAuthority('movements:approve-withdrawal')")
  @Operation(
      summary = "Aprobar un retiro",
      description =
          """
          Declara que **el dinero de un retiro pendiente salió** (`RF-MV-020`). Hasta que se
          integren las pasarelas de salida se aprueba **a mano**: el pago que lo liquida se
          escribe con el método `MANUAL` y, si la hay, la referencia de la transferencia en
          `providerReference`. Lo retenido sale de la cuenta de la persona; la billetera no se
          toca. Aprobar dos veces aprueba una.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Aprobado, con su pago."),
    @ApiResponse(
        responseCode = "400",
        description = "Referencia de más de 120 caracteres",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:approve-withdrawal` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe, o no es un retiro",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "No está pendiente, con el estado",
        content = @Content)
  })
  public LedgerMovementResponse aprobarRetiro(
      @PathVariable UUID id, @RequestBody(required = false) WithdrawalRequests.Approval peticion) {
    return retiros.approve(id, peticion);
  }

  @PostMapping("/{id}/withdrawal-rejection")
  @PreAuthorize("hasAuthority('movements:reject-withdrawal')")
  @Operation(
      summary = "Negar un retiro",
      description =
          """
          Niega un retiro pendiente **por motivos internos** (`RF-MV-021`): queda `RECHAZADA`
          con su motivo, obligatorio, y **lo retenido vuelve a la billetera**. No se escribe
          ningún pago.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Negado."),
    @ApiResponse(
        responseCode = "400",
        description = "Motivo vacío o demasiado largo",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:reject-withdrawal` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe, o no es un retiro",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "No está pendiente, con el estado",
        content = @Content)
  })
  public LedgerMovementResponse negarRetiro(
      @PathVariable UUID id, @RequestBody(required = false) WithdrawalRequests.Rejection peticion) {
    return retiros.reject(id, peticion);
  }

  @GetMapping("/mine/balances")
  @PreAuthorize("hasAuthority('movements:read-own-balances')")
  @Operation(
      summary = "Consultar mis saldos",
      description =
          """
          Por cada moneda en la que la persona tenga algo: la **billetera** —lo que puede
          retirar—, lo **retenido** en retiros sin resolver y los **puntos** (`RF-MV-022`). Una
          persona sin cuentas recibe una lista vacía.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Los saldos, uno por moneda."),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:read-own-balances` (`AUTH-002`)",
        content = @Content)
  })
  public List<BalancesResponse> misSaldos() {
    return saldos.balances();
  }

  @GetMapping("/mine/balances/entries")
  @PreAuthorize("hasAuthority('movements:list-own-entries')")
  @Operation(
      summary = "Consultar el historial de mis saldos",
      description =
          """
          Cada cambio en las cuentas propias, **asiento a asiento**, del más reciente al más
          antiguo, con su signo —positivo entró—, el saldo que dejó, el evento y el movimiento
          que lo produjo (`RF-MV-022`). Es donde la persona ve sus retiros, abonos y bonos.
          Filtros: `currencyId`, `account` (`BILLETERA`, `RETENIDO`, `PUNTOS`), `from` y `to`
          sobre cuándo se escribió, rango semiabierto. Los filtros se combinan; una cuenta de
          la empresa o un periodo invertido son `400`, y los dos errores salen juntos.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Una página del historial."),
    @ApiResponse(
        responseCode = "400",
        description =
            "`account` que no es `BILLETERA`, `RETENIDO` ni `PUNTOS` (`VAL-002`), o `from`"
                + " posterior a `to` (`VAL-003`)",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:list-own-entries` (`AUTH-002`)",
        content = @Content)
  })
  public PageResponse<BalanceService.EntryResponse> miHistorial(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) UUID currencyId,
      @RequestParam(required = false) String account,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to) {
    return saldos.entries(page, size, currencyId, account, from, to);
  }

  @PostMapping("/bonuses")
  @PreAuthorize("hasAuthority('movements:grant-bonus')")
  @Operation(
      summary = "Otorgar un bono",
      description =
          """
          Abona un bono en la **billetera** de una persona, desde la cuenta de bonos de la
          empresa (`RF-MV-023`). Nace `CONFIRMADA` y sin pago. **El motivo (`concept`) y la
          cabecera `Idempotency-Key` son obligatorios**: sin pago detrás, la clave es la única
          defensa contra otorgarlo dos veces. La misma petición repetida responde `200` con el
          bono ya otorgado. Es retirable.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Otorgado."),
    @ApiResponse(responseCode = "200", description = "La misma petición repetida."),
    @ApiResponse(
        responseCode = "400",
        description = "Datos ausentes o malformados, o clave ausente",
        content = @Content),
    @ApiResponse(responseCode = "401", description = "Sin token (`AUTH-001`)", content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Sin `movements:grant-bonus` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "La clave es de otro bono (`EX-005`)",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "La persona o la moneda no existen",
        content = @Content)
  })
  public ResponseEntity<LedgerMovementResponse> otorgarBono(
      @RequestBody(required = false) WithdrawalRequests.Bonus peticion,
      @Parameter(in = ParameterIn.HEADER, required = true, description = "Una por bono.")
          @RequestHeader(value = IdempotencyKey.CABECERA, required = false)
          String clave) {
    CreditService.BonusResult hecho = abonos.grantBonus(peticion, clave);
    if (!hecho.created()) {
      return ResponseEntity.ok(hecho.bonus());
    }
    return ResponseEntity.created(URI.create("/api/v1/movements/" + hecho.bonus().id()))
        .body(hecho.bonus());
  }
}
