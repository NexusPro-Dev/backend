package com.factech.nexus.modules.products.interfaces;

import com.factech.nexus.modules.products.application.ProductResponse;
import com.factech.nexus.modules.products.application.RegisterProductRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El catálogo de productos (`PM`).
 *
 * <p>Por ahora solo el alta: el resto de operaciones llegan con `RF-PM-002` a `RF-PM-007`.
 */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Productos", description = "Catálogo de venta: upgrades de membresía y servicios.")
public class ProductController {

  private final RegisterProductService alta;

  public ProductController(RegisterProductService alta) {
    this.alta = alta;
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
}
