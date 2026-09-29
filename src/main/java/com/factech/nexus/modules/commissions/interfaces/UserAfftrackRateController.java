package com.factech.nexus.modules.commissions.interfaces;

import com.factech.nexus.modules.commissions.application.DeleteAfftrackRateRequest;
import com.factech.nexus.modules.commissions.application.ListUserAfftrackRatesRequest;
import com.factech.nexus.modules.commissions.application.RegisterUserAfftrackRateRequest;
import com.factech.nexus.modules.commissions.application.UpdateUserAfftrackRateRequest;
import com.factech.nexus.modules.commissions.application.UserAfftrackRatePageResponse;
import com.factech.nexus.modules.commissions.application.UserAfftrackRateResponse;
import com.factech.nexus.modules.commissions.domain.service.DeleteUserAfftrackRateService;
import com.factech.nexus.modules.commissions.domain.service.ListUserAfftrackRatesService;
import com.factech.nexus.modules.commissions.domain.service.RegisterUserAfftrackRateService;
import com.factech.nexus.modules.commissions.domain.service.UpdateUserAfftrackRateService;
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

/** Los escalones afftrack de persona (`RF-CM-019`), con sus cuatro operaciones. */
@Tag(
    name = "Comisiones afftrack",
    description =
        "Escalones por FTD —membresía BECA → BECA activada— que se liquidan en cada cierre.")
@RestController
@RequestMapping("/api/v1/user-afftrack-rates")
public class UserAfftrackRateController {

  private final RegisterUserAfftrackRateService alta;
  private final ListUserAfftrackRatesService listado;
  private final UpdateUserAfftrackRateService correccion;
  private final DeleteUserAfftrackRateService retiro;

  public UserAfftrackRateController(
      RegisterUserAfftrackRateService alta,
      ListUserAfftrackRatesService listado,
      UpdateUserAfftrackRateService correccion,
      DeleteUserAfftrackRateService retiro) {
    this.alta = alta;
    this.listado = listado;
    this.correccion = correccion;
    this.retiro = retiro;
  }

  @Operation(
      summary = "Registrar la comisión afftrack de una persona",
      description =
          """
          Registra un escalón de la escala afftrack **propia** de una persona sobre
          un producto FTD, **desde `validFrom` y hasta `validTo`** —nulo:
          indefinidamente—.

          **La escala de la persona sustituye entera la de su rol** (`RN-CM-039`):
          basta **un** escalón vigente el día del cierre para que cobre **solo** por
          los suyos. Un único escalón personal de 40 la deja sin el de 60 de su
          rol. Un escalón de valor **cero** es la forma de dejar a alguien sin
          comisión afftrack sobre un producto.

          Varios límites vigentes a la vez son la escala; **el mismo límite con un
          día en común** responde `409`. No se exige que la persona venda.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Escalón registrado"),
    @ApiResponse(
        responseCode = "400",
        description = "Datos inválidos, o el fin anterior al inicio (`VAL-004`)"),
    @ApiResponse(responseCode = "403", description = "Sin `user-afftrack-rates:create`"),
    @ApiResponse(
        responseCode = "409",
        description =
            "Otro escalón vivo de esa persona, producto y límite cubre algún día (`EX-002`)"),
    @ApiResponse(
        responseCode = "422",
        description =
            "La persona no existe (`EX-001`); el producto no existe, está retirado o no es FTD")
  })
  @PostMapping
  @PreAuthorize("hasAuthority('user-afftrack-rates:create')")
  public ResponseEntity<UserAfftrackRateResponse> registrar(
      @Valid @RequestBody RegisterUserAfftrackRateRequest peticion) {
    UserAfftrackRateResponse creado = alta.register(peticion);
    return ResponseEntity.created(URI.create("/api/v1/user-afftrack-rates/" + creado.id()))
        .body(creado);
  }

  @Operation(
      summary = "Consultar las comisiones afftrack de persona",
      description =
          """
          Los escalones de persona, paginados por persona, producto, límite
          ascendente y vigencia más reciente. Filtros `userId` y `productId`.

          **Con `onDate`** se devuelven exactamente los escalones que el cierre de
          ese día aplicaría: los vivos cuya vigencia cubre la fecha. Si para esa
          persona y producto sale alguno, **esa es su escala**; si no sale ninguno,
          cobra por la de su rol. Con `onDate`, `includeDeleted` no cuenta.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de escalones"),
    @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
    @ApiResponse(responseCode = "403", description = "Sin `user-afftrack-rates:read`")
  })
  @GetMapping
  @PreAuthorize("hasAuthority('user-afftrack-rates:read')")
  public UserAfftrackRatePageResponse listar(@ModelAttribute ListUserAfftrackRatesRequest filtros) {
    return listado.list(filtros);
  }

  @Operation(
      summary = "Corregir la comisión afftrack de una persona",
      description =
          """
          Corrige `threshold`, `amountPerFtd` o `validTo`. **Enviar `validTo` vacío
          quita el fin**: el escalón vuelve a regir indefinidamente. Vaciar el
          límite o el valor se rechaza. **La persona, el producto y el inicio no se
          corrigen** (`EX-008`).

          El choque de vigencias se comprueba con el límite y el fin resultantes:
          `409` si otro escalón vivo de la misma persona, producto y límite cubre
          algún día.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Escalón corregido, o sin cambios"),
    @ApiResponse(
        responseCode = "400",
        description = "Datos inválidos, campos no corregibles o petición vacía"),
    @ApiResponse(responseCode = "403", description = "Sin `user-afftrack-rates:update`"),
    @ApiResponse(responseCode = "404", description = "No existe, o está retirado"),
    @ApiResponse(
        responseCode = "409",
        description = "La vigencia resultante choca con otro escalón")
  })
  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('user-afftrack-rates:update')")
  public UserAfftrackRateResponse corregir(
      @PathVariable UUID id, @RequestBody UpdateUserAfftrackRateRequest peticion) {
    return correccion.update(id, peticion);
  }

  @Operation(
      summary = "Retirar la comisión afftrack de una persona",
      description =
          """
          Retira el escalón con motivo obligatorio y **libera sus días**. Si era el
          último vigente de la persona sobre ese producto, **vuelve a aplicarse la
          escala de su rol**. Los FTD reunidos no se pierden.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Escalón retirado"),
    @ApiResponse(responseCode = "400", description = "Motivo ausente, en blanco o demasiado largo"),
    @ApiResponse(responseCode = "403", description = "Sin `user-afftrack-rates:delete`"),
    @ApiResponse(responseCode = "404", description = "No existe"),
    @ApiResponse(responseCode = "409", description = "Ya estaba retirado")
  })
  @PostMapping("/{id}/deletion")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('user-afftrack-rates:delete')")
  public void retirar(
      @PathVariable UUID id, @RequestBody(required = false) DeleteAfftrackRateRequest peticion) {
    retiro.delete(id, peticion);
  }
}
