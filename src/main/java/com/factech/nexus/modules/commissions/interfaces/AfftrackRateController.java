package com.factech.nexus.modules.commissions.interfaces;

import com.factech.nexus.modules.commissions.application.AfftrackRatePageResponse;
import com.factech.nexus.modules.commissions.application.AfftrackRateResponse;
import com.factech.nexus.modules.commissions.application.DeleteAfftrackRateRequest;
import com.factech.nexus.modules.commissions.application.ListAfftrackRatesRequest;
import com.factech.nexus.modules.commissions.application.RegisterAfftrackRateRequest;
import com.factech.nexus.modules.commissions.application.UpdateAfftrackRateRequest;
import com.factech.nexus.modules.commissions.domain.service.DeleteAfftrackRateService;
import com.factech.nexus.modules.commissions.domain.service.ListAfftrackRatesService;
import com.factech.nexus.modules.commissions.domain.service.RegisterAfftrackRateService;
import com.factech.nexus.modules.commissions.domain.service.UpdateAfftrackRateService;
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
 * Los escalones afftrack de rol (`RF-CM-015` a `RF-CM-018`).
 *
 * <p>Los de persona viven en {@link UserAfftrackRateController}: tienen vigencia y se comportan
 * distinto, como las tasas personalizadas respecto de las de rol.
 */
@Tag(
    name = "Comisiones afftrack",
    description =
        "Escalones por FTD —membresía BECA → BECA activada— que se liquidan en cada cierre.")
@RestController
@RequestMapping("/api/v1/afftrack-rates")
public class AfftrackRateController {

  private final RegisterAfftrackRateService alta;
  private final ListAfftrackRatesService listado;
  private final UpdateAfftrackRateService correccion;
  private final DeleteAfftrackRateService retiro;

  public AfftrackRateController(
      RegisterAfftrackRateService alta,
      ListAfftrackRatesService listado,
      UpdateAfftrackRateService correccion,
      DeleteAfftrackRateService retiro) {
    this.alta = alta;
    this.listado = listado;
    this.correccion = correccion;
    this.retiro = retiro;
  }

  @Operation(
      summary = "Registrar una comisión afftrack de rol",
      description =
          """
          Registra un **escalón** de la escala afftrack de un rol **vendedor** sobre
          un **producto FTD** —la membresía `BECA → BECA`, `RN-CM-036`—: al reunir
          `threshold` FTD en un cierre, se pagan `threshold × amountPerFtd`
          (`amountAtThreshold` en la respuesta).

          Una escala son **varios escalones** del mismo rol y producto con límites
          distintos. Cada cierre paga **el mayor límite alcanzado, una sola vez**, y
          lo que sobra pasa al cierre siguiente (`RN-CM-041`). **Registrar es poner
          en vigor**: rige desde el siguiente cierre.

          El valor está en **la moneda del producto** y no puede tener más decimales
          que ella (`VAL-005`). **El cero es válido**: alcanzar ese límite no paga,
          pero consume los FTD. Un producto que no es FTD se rechaza (`EX-005`), y
          el mismo límite vivo para el mismo rol y producto responde `409`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Escalón registrado"),
    @ApiResponse(
        responseCode = "400",
        description =
            "Datos inválidos (`VAL-001` a `VAL-005`, todos juntos los de forma), o el rol no es"
                + " vendedor (`EX-001`)"),
    @ApiResponse(responseCode = "403", description = "Sin `afftrack-rates:create`"),
    @ApiResponse(
        responseCode = "409",
        description = "Ya hay un escalón vivo de ese rol y producto con ese límite (`EX-006`)"),
    @ApiResponse(
        responseCode = "422",
        description =
            "El rol no existe (`EX-002`); el producto no existe (`EX-003`), está retirado"
                + " (`EX-004`) o no es FTD (`EX-005`)")
  })
  @PostMapping
  @PreAuthorize("hasAuthority('afftrack-rates:create')")
  public ResponseEntity<AfftrackRateResponse> registrar(
      @Valid @RequestBody RegisterAfftrackRateRequest peticion) {
    AfftrackRateResponse creado = alta.register(peticion);
    return ResponseEntity.created(URI.create("/api/v1/afftrack-rates/" + creado.id())).body(creado);
  }

  @Operation(
      summary = "Consultar las comisiones afftrack de rol",
      description =
          """
          La escala de cada rol en cada producto FTD, paginada y ordenada por
          producto, rol y **límite ascendente**: los escalones de una escala salen
          juntos y en el orden en que se alcanzan. Filtros `productId` y `roleId`;
          con `includeDeleted` entran los retirados, con su `deletedAt` y **sin
          motivo**.

          Qué escala rige para una persona concreta lo decide el cierre
          (`RN-CM-039`): la suya, si tiene escalones vigentes
          (`GET /api/v1/user-afftrack-rates`), y si no, la de su rol.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de escalones"),
    @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
    @ApiResponse(responseCode = "403", description = "Sin `afftrack-rates:read`")
  })
  @GetMapping
  @PreAuthorize("hasAuthority('afftrack-rates:read')")
  public AfftrackRatePageResponse listar(@ModelAttribute ListAfftrackRatesRequest filtros) {
    return listado.list(filtros);
  }

  @Operation(
      summary = "Corregir el límite o el valor de una comisión afftrack de rol",
      description =
          """
          Corrige `threshold`, `amountPerFtd` o los dos. **El rol y el producto no
          se corrigen** (`EX-002`): se retira el escalón y se registra otro. Vaciar
          un campo se rechaza.

          **Lo ya pagado no cambia**: cada comisión afftrack copió el límite y el
          valor que pagó. El siguiente cierre usa lo nuevo. Un límite que ya tiene
          otro escalón vivo del mismo rol y producto responde `409`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Escalón corregido, o sin cambios"),
    @ApiResponse(
        responseCode = "400",
        description = "Datos inválidos, campos no corregibles o petición vacía"),
    @ApiResponse(responseCode = "403", description = "Sin `afftrack-rates:update`"),
    @ApiResponse(responseCode = "404", description = "No existe, o está retirado"),
    @ApiResponse(responseCode = "409", description = "Ese límite ya lo tiene otro escalón vivo")
  })
  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('afftrack-rates:update')")
  public AfftrackRateResponse corregir(
      @PathVariable UUID id, @RequestBody UpdateAfftrackRateRequest peticion) {
    return correccion.update(id, peticion);
  }

  @Operation(
      summary = "Retirar una comisión afftrack de rol",
      description =
          """
          Retira el escalón con motivo obligatorio: **deja de aplicarse desde el
          siguiente cierre**. No se deshace. **Los FTD que cada persona haya
          reunido no se pierden**: siguen en su remanente, y el siguiente cierre
          los compara con lo que quede de la escala.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Escalón retirado"),
    @ApiResponse(responseCode = "400", description = "Motivo ausente, en blanco o demasiado largo"),
    @ApiResponse(responseCode = "403", description = "Sin `afftrack-rates:delete`"),
    @ApiResponse(responseCode = "404", description = "No existe"),
    @ApiResponse(responseCode = "409", description = "Ya estaba retirado")
  })
  @PostMapping("/{id}/deletion")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('afftrack-rates:delete')")
  public void retirar(
      @PathVariable UUID id, @RequestBody(required = false) DeleteAfftrackRateRequest peticion) {
    retiro.delete(id, peticion);
  }
}
