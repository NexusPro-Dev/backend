package com.factech.nexus.modules.commissions.interfaces;

import com.factech.nexus.modules.commissions.application.CommissionBatchDetailResponse;
import com.factech.nexus.modules.commissions.application.CommissionBatchPageResponse;
import com.factech.nexus.modules.commissions.application.CommissionClosingResponse;
import com.factech.nexus.modules.commissions.application.ListCommissionBatchesRequest;
import com.factech.nexus.modules.commissions.application.MyCommissionBatchesRequest;
import com.factech.nexus.modules.commissions.domain.service.CloseCommissionPeriodService;
import com.factech.nexus.modules.commissions.domain.service.CommissionBatchQueryService;
import com.factech.nexus.modules.commissions.domain.service.PayCommissionBatchService;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los lotes de comisión (`RF-CM-009` a `RF-CM-012`): cerrarlos, consultarlos y pagarlos.
 *
 * <p><b>No hay ruta para crear un lote</b>: nace solo, con la primera comisión de una persona en
 * una moneda (`RF-CM-013`, `RN-CM-033`).
 *
 * <p><b>{@code /mine} no lo captura {@code /{id}}</b>: la variable es un UUID y la ruta literal
 * gana, como {@code /movements/mine}.
 */
@Tag(
    name = "Lotes de comisión",
    description =
        "Lo que se le debe a cada persona, por periodo y moneda: abierto mientras crece, pendiente"
            + " tras el cierre, pagado cuando Finanzas lo abona en la billetera.")
@RestController
@RequestMapping("/api/v1/commission-batches")
public class CommissionBatchController {

  private final CloseCommissionPeriodService cierre;
  private final CommissionBatchQueryService consultas;
  private final PayCommissionBatchService pago;
  private final AuthenticatedActor actor;

  public CommissionBatchController(
      CloseCommissionPeriodService cierre,
      CommissionBatchQueryService consultas,
      PayCommissionBatchService pago,
      AuthenticatedActor actor) {
    this.cierre = cierre;
    this.consultas = consultas;
    this.pago = pago;
    this.actor = actor;
  }

  @Operation(
      summary = "Cerrar el periodo a mano",
      description =
          """
          **Hace lo mismo que el cierre programado**, y existe para relanzar el que no corrió
          (`RF-CM-009`, `RN-CM-035`). Primero **barre**: devenga las líneas cobradas y con
          vendedor que se quedaron sin desenlace, y reintenta las rechazadas por pasar del 100 %
          (`RN-CM-034`). Después **liquida lo afftrack** (`RF-CM-020`, 29-09-2026): cuenta los
          FTD activados de cada persona y de su red, paga el mayor escalón alcanzado en su lote
          abierto y guarda el remanente —se consulta en `GET /afftrack-settlements`—. Por último
          pasa **todos** los lotes abiertos a `PENDIENTE`, con el instante del cierre como fin de
          periodo. Lo que devengue un segundo después abre un lote nuevo. **Si la liquidación
          afftrack falla, no se cierra nada.**

          **Sin cuerpo.** Responde la constancia del cierre, que queda además consultable en
          `GET /commission-closings`. **`200` y no `201`**: se ejecuta una acción, no se crea un
          recurso que se vaya a leer por su dirección.

          Si hay otro cierre en curso —el programado, u otro manual—, **`409`** y no se cierra
          nada.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Cerrado, aunque fuesen cero lotes"),
    @ApiResponse(responseCode = "401", description = "Sin token"),
    @ApiResponse(responseCode = "403", description = "Sin `commission-batches:settle`"),
    @ApiResponse(responseCode = "409", description = "Hay un cierre en curso")
  })
  @PostMapping("/closing")
  @PreAuthorize("hasAuthority('commission-batches:settle')")
  public CommissionClosingResponse cerrar() {
    return cierre.closeManually(actor.id());
  }

  @Operation(
      summary = "Consultar los lotes de comisión",
      description =
          """
          Los lotes de **todas** las personas (`RF-CM-010`), del periodo más reciente al más
          antiguo: `ABIERTO` —crece con cada venta, sin fin de periodo y con su total **al
          día**—, `PENDIENTE` —cerrado, esperando el pago— y `PAGADO` —abonado en la billetera,
          con `paidAmount` redondeado a la moneda—.

          Filtros combinables: `status`, `userId`, `currencyId`, y `from`/`to` sobre el inicio
          del periodo. Los errores de los filtros salen **todos juntos**.

          **Alcance global**: quien porta el permiso ve todos los lotes. Ver solo los de la red
          depende de **D-22**; los propios son `GET /commission-batches/mine`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de lotes"),
    @ApiResponse(responseCode = "400", description = "Filtros inválidos"),
    @ApiResponse(responseCode = "401", description = "Sin token"),
    @ApiResponse(responseCode = "403", description = "Sin `commission-batches:read`")
  })
  @GetMapping
  @PreAuthorize("hasAuthority('commission-batches:read')")
  public CommissionBatchPageResponse listar(@ModelAttribute ListCommissionBatchesRequest filtros) {
    return consultas.list(filtros, null);
  }

  @Operation(
      summary = "Consultar un lote de comisión",
      description =
          """
          Un lote con **cada comisión**, línea a línea y nivel a nivel (`RF-CM-010`): de qué
          venta y producto sale, qué nivel de la cadena cobra —`0` quien vendió—, de qué tasa
          exacta salió (`source`, `rateId`), la forma y el valor **copiados** al devengar, la base,
          lo devengado, el día de la venta con que se resolvió la tasa (`resolvedOn`) y el
          instante del devengo (`accruedAt`).

          **Lo copiado, no lo de hoy** (`RN-CM-008`): corregir la tasa después no cambia lo que
          aquí se lee.

          **`source` tiene tres valores** desde el 29-09-2026: `PERSONALIZADA`, `ROL` y
          **`DIRECTA`** —la comisión por venta directa del producto, que cobra en el nivel `0`
          quien vendió sin ser el último eslabón y sin personalizada (`RN-CM-045`)—. Con
          `DIRECTA`, **`rateId` es el producto**.

          **Cada comisión dice su clase en `commissionKind`** (`RN-CM-044`, 29-09-2026):
          `POR_VENTA`, con todo lo anterior; o `POR_AFFTRACK`, un escalón pagado en un cierre,
          que **no trae** `movementDetailId`, `movementId`, `movementCode`, `chainLevel` ni
          `unitPrice` —no sale de una línea—: `productId` es el producto FTD, `quantity` los FTD
          pagados, `fixedAmount` el valor por FTD y `afftrackSettlementId` la liquidación.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El lote con sus comisiones"),
    @ApiResponse(responseCode = "400", description = "Identificador malformado"),
    @ApiResponse(responseCode = "401", description = "Sin token"),
    @ApiResponse(responseCode = "403", description = "Sin `commission-batches:read-detail`"),
    @ApiResponse(responseCode = "404", description = "No existe")
  })
  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('commission-batches:read-detail')")
  public CommissionBatchDetailResponse detalle(@PathVariable UUID id) {
    return consultas.get(id, null);
  }

  @Operation(
      summary = "Pagar un lote de comisión",
      description =
          """
          Marca un lote `PENDIENTE` como **pagado** y, en el mismo acto, **abona su total en la
          billetera** de su persona (`RF-CM-011`, `RN-CM-030`, `RN-MV-044`): el importe se
          redondea a los decimales de la moneda, y el abono es un movimiento `PAGO_COMISION`
          que queda enlazado en `movementId`. Si el abono falla, el lote no cambia.

          **Solo un lote `PENDIENTE`**: uno `ABIERTO` sigue creciendo y se paga después del
          cierre; uno `PAGADO` no se paga dos veces. Los dos responden **`409`** con el estado en
          el mensaje. **Sin cuerpo**: se paga el total, entero.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Pagado, con el lote y `paidAmount`"),
    @ApiResponse(responseCode = "400", description = "Identificador malformado"),
    @ApiResponse(responseCode = "401", description = "Sin token"),
    @ApiResponse(responseCode = "403", description = "Sin `commission-batches:pay`"),
    @ApiResponse(responseCode = "404", description = "No existe"),
    @ApiResponse(responseCode = "409", description = "Abierto o ya pagado")
  })
  @PostMapping("/{id}/payment")
  @PreAuthorize("hasAuthority('commission-batches:pay')")
  public CommissionBatchDetailResponse pagar(@PathVariable UUID id) {
    return pago.pay(id);
  }

  @Operation(
      summary = "Consultar mis lotes de comisión",
      description =
          """
          **Los lotes propios** (`RF-CM-012`): el abierto, que **crece con cada venta** —la
          comisión nace en cuanto la venta se confirma con vendedor—, los pendientes de pago y
          los pagados. Incluye lo que se cobra como superior por las ventas de la red: cada nivel
          de la cadena está en el lote de quien cobra.

          La persona la pone el token: **no hay filtro de persona**. Filtros `status`,
          `currencyId`, `from`, `to`, con los errores todos juntos.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de mis lotes"),
    @ApiResponse(responseCode = "400", description = "Filtros inválidos"),
    @ApiResponse(responseCode = "401", description = "Sin token"),
    @ApiResponse(responseCode = "403", description = "Sin `commission-batches:list-own`")
  })
  @GetMapping("/mine")
  @PreAuthorize("hasAuthority('commission-batches:list-own')")
  public CommissionBatchPageResponse misLotes(@ModelAttribute MyCommissionBatchesRequest filtros) {
    return consultas.list(filtros.comoListado(), actor.id());
  }

  @Operation(
      summary = "Consultar uno de mis lotes",
      description =
          """
          Uno de **mis** lotes, con sus comisiones, en la forma del detalle de administración
          (`RF-CM-012`), **con la clase de cada comisión** —`POR_VENTA` o `POR_AFFTRACK`—.
          **Un lote ajeno responde `404`**, igual que uno que no existe: no se confirma que
          exista.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El lote con sus comisiones"),
    @ApiResponse(responseCode = "400", description = "Identificador malformado"),
    @ApiResponse(responseCode = "401", description = "Sin token"),
    @ApiResponse(responseCode = "403", description = "Sin `commission-batches:read-own`"),
    @ApiResponse(responseCode = "404", description = "No existe, o no es mío")
  })
  @GetMapping("/mine/{id}")
  @PreAuthorize("hasAuthority('commission-batches:read-own')")
  public CommissionBatchDetailResponse miLote(@PathVariable UUID id) {
    return consultas.get(id, actor.id());
  }
}
