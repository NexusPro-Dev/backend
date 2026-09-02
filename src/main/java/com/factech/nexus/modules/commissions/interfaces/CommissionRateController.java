package com.factech.nexus.modules.commissions.interfaces;

import com.factech.nexus.modules.commissions.application.AssociateProductRequest;
import com.factech.nexus.modules.commissions.application.CommissionRatePageResponse;
import com.factech.nexus.modules.commissions.application.CommissionRateResponse;
import com.factech.nexus.modules.commissions.application.DeleteCommissionRateRequest;
import com.factech.nexus.modules.commissions.application.DissociateProductRequest;
import com.factech.nexus.modules.commissions.application.ListCommissionRatesRequest;
import com.factech.nexus.modules.commissions.application.ProductAssociationResponse;
import com.factech.nexus.modules.commissions.application.RegisterCommissionRateRequest;
import com.factech.nexus.modules.commissions.application.UpdateCommissionRateRequest;
import com.factech.nexus.modules.commissions.domain.service.AssociateProductService;
import com.factech.nexus.modules.commissions.domain.service.DeleteCommissionRateService;
import com.factech.nexus.modules.commissions.domain.service.DissociateProductService;
import com.factech.nexus.modules.commissions.domain.service.ListCommissionRatesService;
import com.factech.nexus.modules.commissions.domain.service.ListProductAssociationsService;
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
 * El catálogo de tasas por rol y sus asociaciones (`CM`).
 *
 * <p><b>Las asociaciones cuelgan de la tasa y no son un recurso raíz</b>, porque no existen sin
 * ella: una asociación es «esta tasa, sobre este producto». La lectura desde el otro lado —«qué
 * paga este producto»— sí tiene recurso propio, en {@link ProductCommissionRateController}.
 *
 * <p>Las <b>tasas personalizadas</b> viven en {@link UserCommissionRateController} y la
 * <b>resolución</b> en {@link CommissionResolutionController}: son recursos distintos, no vistas
 * del mismo.
 */
@Tag(
    name = "Comisiones",
    description = "Tasas de comisión por rol y su asociación con los productos.")
@RestController
@RequestMapping("/api/v1/commission-rates")
public class CommissionRateController {

  private final RegisterCommissionRateService alta;
  private final ListCommissionRatesService listado;
  private final UpdateCommissionRateService correccion;
  private final DeleteCommissionRateService retiro;
  private final AssociateProductService asociacion;
  private final DissociateProductService desasociacion;
  private final ListProductAssociationsService asociaciones;

  public CommissionRateController(
      RegisterCommissionRateService alta,
      ListCommissionRatesService listado,
      UpdateCommissionRateService correccion,
      DeleteCommissionRateService retiro,
      AssociateProductService asociacion,
      DissociateProductService desasociacion,
      ListProductAssociationsService asociaciones) {
    this.alta = alta;
    this.listado = listado;
    this.correccion = correccion;
    this.retiro = retiro;
    this.asociacion = asociacion;
    this.desasociacion = desasociacion;
    this.asociaciones = asociaciones;
  }

  @Operation(
      summary = "Registrar una tasa de comisión por rol",
      description =
          """
          Declara cuánto gana un rol de tipo **vendedor** por vender.

          **Registrar una tasa NO la pone en vigor.** Hasta que se asocie a un
          producto con `POST /{id}/products`, **no paga nada a nadie**. La respuesta
          lleva `associatedProducts`, que aquí vale siempre cero.

          Esto es lo contrario de lo que ocurría antes del 01-09-2026, cuando una
          tarifa sin producto regía sobre **todo el catálogo**.

          **Varias tasas por rol son legítimas**: el catálogo puede ofrecer
          `AGENTE 10 %` y `AGENTE 15 %` para asociarlas a productos distintos.

          **El porcentaje cero es válido** y significa «esto no comisiona», que **no
          es lo mismo** que no declarar la tasa.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Tasa registrada, y todavía sin regir"),
    @ApiResponse(responseCode = "400", description = "Datos inválidos, o el rol no es vendedor"),
    @ApiResponse(responseCode = "403", description = "Sin permiso"),
    @ApiResponse(responseCode = "422", description = "El rol no existe")
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
      summary = "Consultar el catálogo de tasas por rol",
      description =
          """
          Devuelve el **catálogo**, no lo que rige.

          **`associatedProducts` es el campo que hay que mirar**: una tasa con cero
          asociaciones aparece aquí con su porcentaje y **no paga nada a nadie**.

          Las tasas **personalizadas** no salen en este listado: están en
          `GET /api/v1/user-commission-rates`.

          **No hay filtro por fecha**, porque las tasas de rol no tienen vigencia:
          el catálogo solo sabe lo que dice hoy.
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
      summary = "Corregir el porcentaje de una tasa de rol",
      description =
          """
          Corrige el **porcentaje**, que es lo único corregible.

          **Esta operación borra el pasado.** Las tasas de rol no tienen vigencia:
          pasar un `AGENTE` de 10 a 12 **borra el 10**. No hay dos filas contando
          cada una su parte; hay una que ahora dice otra cosa.

          Lo único que preserva lo ya pagado es que la liquidación haya copiado el
          porcentaje que aplicó — y esa liquidación **todavía no existe**.

          `percentage: null` se **rechaza**. **El rol no se corrige**, y enviarlo
          devuelve `400`: se rechaza y no se ignora, porque ignorarlo haría creer
          que el cambio se aplicó.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Tasa corregida"),
    @ApiResponse(responseCode = "400", description = "Datos inválidos o campos no corregibles"),
    @ApiResponse(responseCode = "403", description = "Sin permiso"),
    @ApiResponse(responseCode = "404", description = "No existe, o está retirada")
  })
  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('commissions:update')")
  public CommissionRateResponse corregir(
      @PathVariable UUID id, @RequestBody UpdateCommissionRateRequest peticion) {
    return correccion.update(id, peticion);
  }

  @Operation(
      summary = "Retirar una tasa de comisión por rol",
      description =
          """
          Retira una tasa que **no debió existir**, con **motivo obligatorio**.

          **Una tasa asociada a algún producto no se retira** y devuelve `409`.
          Retire primero esas asociaciones: si no, el producto dejaría de comisionar
          **sin que nada lo indicara**, porque la asociación sobreviviría apuntando a
          una fila muerta.

          **Para dejar de pagar sin destruir la tasa**, use la desasociación
          (`POST /{id}/products/{productId}/deletion`): la tasa sigue en el catálogo
          y disponible para otros productos.

          **No es idempotente**: retirar dos veces devuelve `409`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Tasa retirada"),
    @ApiResponse(responseCode = "400", description = "Motivo ausente, en blanco o demasiado largo"),
    @ApiResponse(responseCode = "403", description = "Sin permiso"),
    @ApiResponse(responseCode = "404", description = "La tasa no existe"),
    @ApiResponse(
        responseCode = "409",
        description = "Ya estaba retirada, o sigue asociada a algún producto")
  })
  @PostMapping("/{id}/deletion")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('commissions:delete')")
  public void retirar(
      @PathVariable UUID id, @RequestBody(required = false) DeleteCommissionRateRequest peticion) {
    retiro.delete(id, peticion);
  }

  @Operation(
      summary = "Asociar la tasa a un producto",
      description =
          """
          **Es lo único que pone una tasa en vigor** (`RN-CM-012`). Sin esto, la tasa
          existe en el catálogo y no paga nada a nadie.

          **El rol no se envía**: se toma de la tasa que nombra la ruta, y el esquema
          hace imposible que diverja del que ella declara.

          **Un solo porcentaje por rol y producto** (`RN-CM-013`): si ese rol ya
          tiene otra tasa asociada a ese producto, devuelve `409`. Retire la
          asociación existente antes de declarar otra.

          **No se asocia a un producto retirado** ni desde una tasa retirada.

          Devuelve **todas** las asociaciones de la tasa, no solo la nueva.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Asociada. Devuelve todas sus asociaciones"),
    @ApiResponse(responseCode = "400", description = "Datos inválidos"),
    @ApiResponse(responseCode = "403", description = "Sin permiso"),
    @ApiResponse(responseCode = "404", description = "La tasa no existe o está retirada"),
    @ApiResponse(
        responseCode = "409",
        description = "Ese rol ya paga por ese producto, o el producto está retirado"),
    @ApiResponse(responseCode = "422", description = "El producto no existe")
  })
  @PostMapping("/{id}/products")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('commissions:update')")
  public ProductAssociationResponse asociar(
      @PathVariable UUID id, @Valid @RequestBody AssociateProductRequest peticion) {
    return asociacion.associate(id, peticion);
  }

  @Operation(
      summary = "Consultar sobre qué productos rige la tasa",
      description =
          """
          **Una lista vacía significa que la tasa no paga nada a nadie**, por mucho
          que tenga porcentaje.

          No se pagina: una tasa se asocia a un puñado de productos.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Las asociaciones de la tasa"),
    @ApiResponse(responseCode = "403", description = "Sin permiso")
  })
  @GetMapping("/{id}/products")
  @PreAuthorize("hasAuthority('commissions:read')")
  public ProductAssociationResponse productosDeLaTasa(@PathVariable UUID id) {
    return asociaciones.byRate(id);
  }

  @Operation(
      summary = "Retirar la asociación de la tasa con un producto",
      description =
          """
          **Es la única forma de dejar de pagar sin retirar la tasa**: sigue en el
          catálogo y disponible para otros productos.

          **El borrado es físico** y la tabla no tiene retiro lógico, de modo que
          **el registro de eliminación es lo único que queda** de que esa tasa rigió
          alguna vez sobre ese producto. De ahí el **motivo obligatorio**.

          Si esa tasa no está asociada a ese producto devuelve `404` — y no `409`,
          porque al no quedar rastro no puede distinguirse «nunca existió» de «ya se
          borró».
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Asociación retirada"),
    @ApiResponse(responseCode = "400", description = "Motivo ausente, en blanco o demasiado largo"),
    @ApiResponse(responseCode = "403", description = "Sin permiso"),
    @ApiResponse(responseCode = "404", description = "Esa tasa no está asociada a ese producto")
  })
  @PostMapping("/{id}/products/{productId}/deletion")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('commissions:update')")
  public void desasociar(
      @PathVariable UUID id,
      @PathVariable UUID productId,
      @RequestBody(required = false) DissociateProductRequest peticion) {
    desasociacion.dissociate(id, productId, peticion);
  }
}
