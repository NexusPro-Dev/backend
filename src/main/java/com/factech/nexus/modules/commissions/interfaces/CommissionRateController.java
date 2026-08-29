package com.factech.nexus.modules.commissions.interfaces;

import com.factech.nexus.modules.commissions.application.CommissionRatePageResponse;
import com.factech.nexus.modules.commissions.application.CommissionRateResponse;
import com.factech.nexus.modules.commissions.application.DeleteCommissionRateRequest;
import com.factech.nexus.modules.commissions.application.ListCommissionRatesRequest;
import com.factech.nexus.modules.commissions.application.RegisterCommissionRateRequest;
import com.factech.nexus.modules.commissions.application.UpdateCommissionRateRequest;
import com.factech.nexus.modules.commissions.domain.service.DeleteCommissionRateService;
import com.factech.nexus.modules.commissions.domain.service.ListCommissionRatesService;
import com.factech.nexus.modules.commissions.domain.service.RegisterCommissionRateService;
import com.factech.nexus.modules.commissions.domain.service.UpdateCommissionRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las tarifas de comisión (`CM`).
 *
 * <p>Cuatro operaciones: alta, listado, corrección y retiro. La <b>resolución</b> —qué porcentaje
 * le corresponde a una persona por un producto— vive en otro controlador, porque es otro recurso:
 * no devuelve una tarifa del catálogo sino una respuesta calculada.
 */
@Tag(
    name = "Comisiones",
    description = "Tarifas de comisión: cuánto gana un rol vendedor, por producto y por persona.")
@RestController
@RequestMapping("/api/v1/commission-rates")
public class CommissionRateController {

  private final RegisterCommissionRateService alta;
  private final ListCommissionRatesService listado;
  private final UpdateCommissionRateService correccion;
  private final DeleteCommissionRateService retiro;

  public CommissionRateController(
      RegisterCommissionRateService alta,
      ListCommissionRatesService listado,
      UpdateCommissionRateService correccion,
      DeleteCommissionRateService retiro) {
    this.alta = alta;
    this.listado = listado;
    this.correccion = correccion;
    this.retiro = retiro;
  }

  @Operation(
      summary = "Registrar una tarifa de comisión",
      description =
          """
          Declara cuánto gana un rol de tipo **vendedor** por vender.

          **La ausencia es la que da el alcance**: sin `userId` la tarifa rige para
          todos los del rol, y sin `productId` para todo el catálogo. No hay ningún
          campo que diga «para todos».

          **Toda tarifa rige durante un periodo**: `validFrom` es obligatorio y
          `validTo` opcional — sin él, rige indefinidamente.

          **El porcentaje cero es válido** y significa «esto no comisiona», que **no
          es lo mismo** que no declarar la tarifa.

          Dos tarifas del mismo rol, producto y persona **no pueden solaparse** en el
          tiempo. Si una termina el 31, la siguiente empieza el 1.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Tarifa registrada"),
    @ApiResponse(responseCode = "400", description = "Datos inválidos, o el rol no es vendedor"),
    @ApiResponse(responseCode = "403", description = "Sin permiso"),
    @ApiResponse(responseCode = "409", description = "Se solapa con otra tarifa viva"),
    @ApiResponse(
        responseCode = "422",
        description = "El rol, el producto o la persona no resuelven")
  })
  @PostMapping
  @PreAuthorize("hasAuthority('commissions:create')")
  public ResponseEntity<CommissionRateResponse> registrar(
      @Valid @RequestBody RegisterCommissionRateRequest peticion) {
    CommissionRateResponse creada = alta.register(peticion);
    return ResponseEntity.created(URI.create("/api/v1/commission-rates/" + creada.id()))
        .body(creada);
  }

  @Operation(
      summary = "Consultar las tarifas de comisión",
      description =
          """
          Devuelve las tarifas **tal como se declararon**, y no resuelve cuál se
          aplica: para eso está `GET /api/v1/commissions/effective`.

          **Incluye el historial**: las vencidas viajan junto a las vigentes salvo
          que se filtre por fecha con `onDate`.

          **Filtrar por persona devuelve las declaradas PARA esa persona**, no las
          que le aplican.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de tarifas"),
    @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
    @ApiResponse(responseCode = "403", description = "Sin permiso")
  })
  @GetMapping
  @PreAuthorize("hasAuthority('commissions:read')")
  public CommissionRatePageResponse listar(@ModelAttribute ListCommissionRatesRequest filtros) {
    return listado.list(filtros);
  }

  @Operation(
      summary = "Corregir una tarifa de comisión",
      description =
          """
          Corrige el **porcentaje** y el **fin de vigencia**. Se aplica lo que llega
          y se deja intacto lo que no.

          **Corregir no es cambiar**: corregir arregla un error y reescribe lo que
          esa tarifa dice que rigió. Cambiar la comisión a partir de una fecha es
          **cerrar la vigente y registrar otra**, que son dos operaciones.

          `validTo: null` **vacía** el fin de vigencia y la tarifa vuelve a regir
          indefinidamente; `percentage: null` se **rechaza**.

          **El rol, el producto, la persona y el inicio de vigencia no se corrigen**,
          y enviarlos devuelve `400`. Se rechazan y no se ignoran: ignorarlos haría
          creer que el cambio se aplicó.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Tarifa corregida"),
    @ApiResponse(responseCode = "400", description = "Datos inválidos o campos no corregibles"),
    @ApiResponse(responseCode = "403", description = "Sin permiso"),
    @ApiResponse(responseCode = "404", description = "No existe, o está retirada"),
    @ApiResponse(responseCode = "409", description = "La vigencia resultante se solapa")
  })
  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('commissions:update')")
  public CommissionRateResponse corregir(
      @PathVariable UUID id, @RequestBody UpdateCommissionRateRequest peticion) {
    return correccion.update(id, peticion);
  }

  @Operation(
      summary = "Retirar una tarifa de comisión",
      description =
          """
          Retira una tarifa que **no debió existir**, con **motivo obligatorio**.

          **Retirar no es cerrar la vigencia**: se cierra lo que dejó de regir, se
          retira lo que fue un error. Por eso el retiro **no toca la vigencia** — el
          registro de eliminación debe poder decir qué periodo cubría lo retirado.

          **No es idempotente**: retirar dos veces devuelve `409`.

          Los días que ocupaba **quedan libres**, de modo que puede declararse otra
          tarifa que los cubra.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Tarifa retirada"),
    @ApiResponse(responseCode = "400", description = "Motivo ausente, en blanco o demasiado largo"),
    @ApiResponse(responseCode = "403", description = "Sin permiso"),
    @ApiResponse(responseCode = "404", description = "La tarifa no existe"),
    @ApiResponse(responseCode = "409", description = "Ya estaba retirada")
  })
  @PostMapping("/{id}/deletion")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('commissions:delete')")
  public void retirar(
      @PathVariable UUID id, @RequestBody(required = false) DeleteCommissionRateRequest peticion) {
    retiro.delete(id, peticion);
  }
}
