package com.factech.nexus.modules.commissions.interfaces;

import com.factech.nexus.modules.commissions.application.CommissionClosingPageResponse;
import com.factech.nexus.modules.commissions.application.ListCommissionClosingsRequest;
import com.factech.nexus.modules.commissions.application.NextClosingResponse;
import com.factech.nexus.modules.commissions.application.PaymentModeRequest;
import com.factech.nexus.modules.commissions.domain.service.ChoosePaymentModeService;
import com.factech.nexus.modules.commissions.domain.service.GetNextClosingService;
import com.factech.nexus.modules.commissions.domain.service.ListCommissionClosingsService;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los cierres del periodo de comisiones (`RF-CM-009`), y el próximo con su forma de pago
 * (`RF-CM-028`, `RF-CM-029`).
 */
@Tag(
    name = "Cierres de comisiones",
    description = "Cada cierre del periodo, programado o a mano, con lo que hizo.")
@RestController
@RequestMapping("/api/v1/commission-closings")
public class CommissionClosingController {

  private final ListCommissionClosingsService cierres;
  private final GetNextClosingService proximo;
  private final ChoosePaymentModeService eleccion;
  private final AuthenticatedActor actor;

  public CommissionClosingController(
      ListCommissionClosingsService cierres,
      GetNextClosingService proximo,
      ChoosePaymentModeService eleccion,
      AuthenticatedActor actor) {
    this.cierres = cierres;
    this.proximo = proximo;
    this.eleccion = eleccion;
    this.actor = actor;
  }

  @Operation(
      summary = "Consultar los cierres",
      description =
          """
          Los cierres del periodo, **del más reciente al más antiguo** (`RF-CM-009`,
          `CA-CM-180`): cuándo empezó y cuándo cerró cada uno, si fue programado o a mano —y
          quién—, cuántos lotes cerró, cuántas líneas recogió el barrido y cuántas rechazadas
          reintentó y recuperó.

          **Existe porque un proceso programado no tiene a quién contestar**: es la forma de saber
          si el cierre de anoche corrió. **`closedAt` nulo** es un cierre en curso o uno que falló
          a medias; sus lotes siguen abiertos. **`paymentMode`** dice cómo se pagó lo que cerró un
          cierre programado —`AUTOMATICO` o `MANUAL`, nulo en los cierres a mano, que no pagan—,
          con `batchesPaid` y `batchesNotPaid` (`RN-CM-053`). **`linesSwept` distinto de cero** quiere decir que
          el aviso de las ventas se está perdiendo y el barrido lo está tapando.

          Filtros: `origin` (`PROGRAMADO`, `MANUAL`) y `from`/`to` sobre el inicio. Los errores
          de los filtros salen **todos juntos**.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de cierres"),
    @ApiResponse(responseCode = "400", description = "Filtros inválidos"),
    @ApiResponse(responseCode = "401", description = "Sin token"),
    @ApiResponse(responseCode = "403", description = "Sin `commission-closings:read`")
  })
  @GetMapping
  @PreAuthorize("hasAuthority('commission-closings:read')")
  public CommissionClosingPageResponse listar(
      @ModelAttribute ListCommissionClosingsRequest filtros) {
    return cierres.list(filtros);
  }

  @Operation(
      summary = "Consultar el próximo cierre y cómo se pagará",
      description =
          """
          **Cuándo es el próximo cierre programado** y cómo se pagará lo que cierre (`RF-CM-028`,
          `RN-CM-054`). El cierre paga **en el mismo momento**, salvo que se elija pagarlo a mano
          en las **48 horas anteriores**: `windowOpensAt` es cuándo se abre esa ventana y
          `windowOpen` si está abierta ahora. **Es lo que el frontend lee para mostrar el botón**
          de `PUT /commission-closings/next/payment-mode`.

          **Sin elección, `paymentMode` es `AUTOMATICO`** y `chosen` es falso, sin `chosenBy` ni
          `chosenAt`. La elección vale **solo para ese cierre**. Con el cierre programado
          **apagado** no hay próximo cierre: `409`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "El próximo cierre"),
    @ApiResponse(responseCode = "401", description = "Sin token"),
    @ApiResponse(responseCode = "403", description = "Sin `commission-closings:read-next`"),
    @ApiResponse(responseCode = "409", description = "`EX-001`: el cierre programado está apagado")
  })
  @GetMapping("/next")
  @PreAuthorize("hasAuthority('commission-closings:read-next')")
  public NextClosingResponse proximo() {
    return proximo.get();
  }

  @Operation(
      summary = "Elegir si el pago del próximo cierre es automático o manual",
      description =
          """
          **El botón** (`RF-CM-029`, `RN-CM-054`): `AUTOMATICO` hace que el cierre pague, en
          cuanto termina, **cada lote que cerró**, cada uno por su cuenta; `MANUAL` los deja
          `PENDIENTE` para Finanzas. **Solo dentro de la ventana**: las 48 horas anteriores al
          cierre, hasta que el cierre empieza. Dentro se puede cambiar cuantas veces se quiera, y
          **gana la última**; cada elección queda auditada.

          Vale **solo para ese cierre**: el siguiente vuelve a `AUTOMATICO`. Responde lo mismo que
          `GET /commission-closings/next`, con la elección hecha.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Elegido"),
    @ApiResponse(responseCode = "400", description = "`VAL-001`: el modo falta o no es válido"),
    @ApiResponse(responseCode = "401", description = "Sin token"),
    @ApiResponse(responseCode = "403", description = "Sin `commission-closings:set-payment-mode`"),
    @ApiResponse(
        responseCode = "409",
        description =
            "`EX-001`: el cierre programado está apagado. `EX-002`: la ventana está cerrada")
  })
  @PutMapping("/next/payment-mode")
  @PreAuthorize("hasAuthority('commission-closings:set-payment-mode')")
  public NextClosingResponse elegir(@Valid @RequestBody PaymentModeRequest peticion) {
    return eleccion.choose(peticion.modo(), actor.id());
  }
}
