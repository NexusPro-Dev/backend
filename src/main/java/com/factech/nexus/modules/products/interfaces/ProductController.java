package com.factech.nexus.modules.products.interfaces;

import com.factech.nexus.modules.products.application.ListProductsRequest;
import com.factech.nexus.modules.products.application.ProductPageResponse;
import com.factech.nexus.modules.products.application.ProductResponse;
import com.factech.nexus.modules.products.application.RegisterProductRequest;
import com.factech.nexus.modules.products.domain.service.ListProductsService;
import com.factech.nexus.modules.products.domain.service.RegisterProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El catálogo de productos (`PM`).
 *
 * <p>El alta y el listado; el resto de operaciones llegan con `RF-PM-003` a `RF-PM-007`.
 *
 * <p><b>Este listado no es la oferta.</b> Devuelve el catálogo completo —lo activo, lo inactivo y,
 * si se pide, lo retirado— y lo lee quien administra o vende. Lo que un cliente puede comprar es
 * `RF-PM-007`, que responde otra pregunta, a otro actor y en otro orden.
 */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Productos", description = "Catálogo de venta: upgrades de membresía y servicios.")
public class ProductController {

  private final RegisterProductService alta;
  private final ListProductsService listado;

  public ProductController(RegisterProductService alta, ListProductsService listado) {
    this.alta = alta;
    this.listado = listado;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('products:create')")
  @Operation(
      summary = "Registrar un producto",
      description =
          """
          Registra un producto del catálogo, **siempre inactivo**: publicarlo es otra
          operación (`RF-PM-005`).

          El tipo decide qué campos son obligatorios: un `UPGRADE_MEMBRESIA` debe
          declarar su membresía destino y un `SERVICIO` no puede declararla.

          La vigencia es opcional en los dos tipos: sin ella, lo adquirido no caduca.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Producto registrado, en estado `INACTIVO`.",
        content = @Content(schema = @Schema(implementation = ProductResponse.class))),
    @ApiResponse(responseCode = "400", description = "Datos inválidos (serie `VAL-nnn`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `products:create` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "409",
        description = "Código o nombre ya en uso (`EX-005`, `EX-001`)"),
    @ApiResponse(
        responseCode = "422",
        description = "Membresía destino o moneda inexistente o inactiva (`EX-002`, `EX-003`)")
  })
  public ResponseEntity<ProductResponse> register(
      @Valid @RequestBody RegisterProductRequest peticion) {

    ProductResponse creado = alta.register(peticion.toCommand());
    return ResponseEntity.created(URI.create("/api/v1/products/" + creado.id())).body(creado);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('products:read')")
  @Operation(
      summary = "Consultar el catálogo de productos",
      description =
          """
          Devuelve el catálogo **paginado**, con el destino de cada upgrade y la
          moneda de cada precio ya resueltos: no hace falta una segunda consulta.

          **Los retirados quedan fuera salvo que se pidan** con `includeDeleted`.
          Al pedirlos se indica **desde cuándo** lo están; el **motivo** no viaja
          en el listado —uno a uno lo devuelve el detalle (`RF-PM-003`), en
          bloque sería una exportación de decisiones comerciales—. Verlos **no
          exige un permiso propio**: basta `products:read`.

          Solo se puede ordenar por la lista blanca —`name`, `price`,
          `createdAt`—, con `,asc` o `,desc`. **Un campo fuera de ella se
          rechaza y no se ignora**: ignorarlo devolvería un orden distinto del
          pedido sin decirlo. Por omisión se ordena por **fecha de alta
          descendente**, y el orden aplicado viaja en la respuesta.

          **`targetMembershipId` no se valida contra el catálogo de membresías.**
          Filtrar por un destino inexistente devuelve la colección vacía y no es
          un error; combinarlo con `type=SERVICIO` también, porque ningún
          servicio tiene destino.

          La búsqueda va sobre el nombre, **sin distinguir acentos ni
          mayúsculas** y por fragmento. En blanco equivale a no filtrar.

          Un filtro sin coincidencias devuelve `200` con la colección vacía, y
          una página más allá de la última hace lo mismo **con el total real**.
          No hay `404` ni `422`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Página del catálogo, con el orden aplicado.",
        content = @Content(schema = @Schema(implementation = ProductPageResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Paginación fuera de rango (`VAL-001`), tipo (`VAL-002`) o estado (`VAL-003`) fuera de"
                + " su dominio, identificador mal formado (`VAL-004`) o campo de ordenamiento no"
                + " admitido (`VAL-005`). Los cuatro primeros se devuelven **juntos**",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `products:read` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public ProductPageResponse listar(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute ListProductsRequest filtros) {
    return listado.list(filtros);
  }
}
