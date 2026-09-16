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
 * Las tasas de rol de cada producto (`CM`).
 *
 * <p><b>Desde el 15-09-2026 una tasa de rol nace con su producto</b> (`RN-CM-021`): la ruta es la
 * de siempre y el cuerpo lleva {@code productId}. Las asociaciones de rol —{@code /{id}/products}—
 * dejaron de existir con `RF-CM-007` y `RF-CM-008`; la lectura «qué paga este producto» sigue en
 * {@link ProductCommissionRateController}.
 *
 * <p>Las <b>tasas personalizadas</b> viven en {@link UserCommissionRateController} y la
 * <b>resolución</b> en {@link CommissionResolutionController}: son recursos distintos, no vistas
 * del mismo.
 */
@Tag(name = "Comisiones", description = "Tasas de comisión por rol: qué paga cada producto.")
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
      summary = "Registrar la tasa de comisión de un rol sobre un producto",
      description =
          """
          Declara cuánto gana un rol de tipo **vendedor** por vender **este
          producto**. **`productId` es obligatorio** (`RN-CM-021`, 15-09-2026): la
          tasa nace con su producto, **rige sobre él desde este instante** y no
          cambia de producto — cambiar es retirarla y registrar otra.

          **Registrar es poner en vigor.** No hay paso de asociación ni borrador:
          una tasa registrada «para probar» sobre un producto activo es una
          comisión real. Hasta el 15-09-2026 la tasa se registraba sin producto y
          no pagaba nada hasta asociarse; las tasas de aquel catálogo se
          borraron (`V94`) y hay que registrarlas de nuevo, producto a producto.

          **Un solo rol por producto** entre las vivas (`RN-CM-013`): la segunda
          responde `409`. Y todo lo que antes se comprobaba al asociar se
          comprueba aquí: producto **vivo y no retirado**, el **tope** —la suma de
          lo que el producto paga a sus roles no pasa de cien, `RN-CM-019`—, la
          regla del **gratuito** —solo importe fijo, `RN-CM-020`— y, para un
          importe fijo, **los decimales de la moneda del producto**
          (`RN-CM-017`).

          **El porcentaje cero es válido** y significa «esto no comisiona», que **no
          es lo mismo** que no declarar la tasa.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Tasa registrada, y rigiendo desde ya"),
    @ApiResponse(
        responseCode = "400",
        description =
            "Datos inválidos, el rol no es vendedor, o el importe fijo no cabe en los decimales de"
                + " la moneda del producto (`VAL-014`)"),
    @ApiResponse(responseCode = "403", description = "Sin permiso"),
    @ApiResponse(
        responseCode = "409",
        description =
            "Ya hay una tasa viva de ese rol sobre ese producto (`EX-007`), el producto pagaría"
                + " más de cien (`EX-005`), o es gratuito y la tasa es de porcentaje (`EX-006`)"),
    @ApiResponse(
        responseCode = "422",
        description = "El rol no existe (`EX-002`), o el producto no existe o está retirado")
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
      summary = "Consultar las tasas de comisión por rol",
      description =
          """
          Devuelve **todas las tasas de rol que se han configurado**, cada una
          **con su producto** (`id`, `code`, `name`, su **`price`** y su
          **`currency`**: `id`, `code`, `decimalPlaces`) y su rol, paginadas. Es
          lo que el responsable del proyecto pidió leer: qué paga cada producto,
          en una sola lista —y sobre qué precio y en qué moneda, porque un
          porcentaje es una parte del precio y un importe fijo es dinero en la
          moneda del producto—.
          Se filtra por `productId`, `roleId` y `rateType`, y con
          `includeDeleted` entran las retiradas.

          **Toda tasa viva rige** sobre su producto (`RN-CM-021`): ya no hay un
          contador de asociaciones que mirar.

          Las tasas **personalizadas** no salen en este listado: están en
          `GET /api/v1/user-commission-rates`. **No hay filtro por fecha**, porque
          las tasas de rol no tienen vigencia.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de tasas"),
    @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
    @ApiResponse(responseCode = "403", description = "Sin permiso")
  })
  @GetMapping
  @PreAuthorize("hasAuthority('commissions:read')")
  public CommissionRatePageResponse listar(@ModelAttribute ListCommissionRatesRequest filtros) {
    return listado.list(filtros);
  }

  @Operation(
      summary = "Corregir el valor de una tasa de rol",
      description =
          """
          Corrige el **valor** —la forma y la cifra—, que es lo único corregible.

          **Esta operación borra el pasado.** Las tasas de rol no tienen vigencia:
          pasar un `AGENTE` de 10 a 12 **borra el 10**. No hay dos filas contando
          cada una su parte; hay una que ahora dice otra cosa.

          Lo único que preserva lo ya pagado es que la liquidación haya copiado el
          porcentaje que aplicó — y esa liquidación **todavía no existe**.

          `rateType: null` se **rechaza**. **Ni el rol ni el producto se corrigen**:
          enviar `roleId` devuelve `400` con `VAL-009`, y `productId` es un campo
          desconocido que también responde `400`. Se rechazan y no se ignoran,
          porque ignorarlos haría creer que el cambio se aplicó.

          **El tope se revisa contra su producto** (`RN-CM-019`): si el nuevo valor
          dejaría al producto pagando más del 100 % de sí mismo entre sus tasas de
          rol, la corrección se rechaza con `409`. **Un producto GRATUITO solo
          admite importe fijo** (`RN-CM-020`): corregir a porcentaje una tasa de un
          producto de precio cero se rechaza con `409` (`EX-008`). Y un importe
          fijo tiene que **caber en los decimales de la moneda del producto**
          (`VAL-013`).
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Tasa corregida"),
    @ApiResponse(responseCode = "400", description = "Datos inválidos o campos no corregibles"),
    @ApiResponse(responseCode = "403", description = "Sin permiso"),
    @ApiResponse(responseCode = "404", description = "No existe, o está retirada"),
    @ApiResponse(
        responseCode = "409",
        description =
            "El nuevo valor dejaría al producto pagando más de cien (`EX-006`), o dejaría de"
                + " porcentaje la tasa de un producto gratuito (`EX-008`)")
  })
  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('commissions:update')")
  public CommissionRateResponse corregir(
      @PathVariable UUID id, @RequestBody UpdateCommissionRateRequest peticion) {
    return correccion.update(id, peticion);
  }

  @Operation(
      summary = "Retirar una tasa de comisión de rol",
      description =
          """
          Retira una tasa con **motivo obligatorio** (`RN-CM-005`), y desde ese
          instante **el producto deja de pagar a ese rol**: es la única forma de
          dejar de pagar, a la vista y con instantánea.

          Hasta el 15-09-2026 una tasa asociada no se retiraba (`RN-CM-015`);
          desde que la tasa nace con su producto no hay asociación que la
          sostenga, y esa condición queda solo para la personalizada.

          **No es idempotente**: retirar dos veces devuelve `409`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Tasa retirada"),
    @ApiResponse(responseCode = "400", description = "Motivo ausente, en blanco o demasiado largo"),
    @ApiResponse(responseCode = "403", description = "Sin permiso"),
    @ApiResponse(responseCode = "404", description = "La tasa no existe"),
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
