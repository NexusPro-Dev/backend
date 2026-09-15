package com.factech.nexus.modules.products.interfaces;

import com.factech.nexus.modules.products.application.PackageDetailResponse;
import com.factech.nexus.modules.products.application.RegisterPackageRequest;
import com.factech.nexus.modules.products.domain.service.RegisterPackageService;
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
 * Los paquetes de productos (`PM`, `RF-PM-017` a `RF-PM-025`).
 *
 * <p><b>Un recurso propio con sus cuatro permisos</b> (`packages:`), por decisión del responsable
 * del proyecto: armar combos y tocar el catálogo son dos capacidades, y los {@code products:} no
 * habilitan aquí ni una operación.
 *
 * <p><b>Todas las operaciones devuelven el paquete entero con su cuenta hecha</b>, porque lo que
 * cambia al asociar, corregir o quitar un producto es su precio — y el precio no está en ninguna
 * columna (`RN-PM-036`).
 */
@RestController
@RequestMapping("/api/v1/packages")
@Tag(name = "Paquetes", description = "Paquetes de productos: varios productos con su descuento.")
public class PackageController {

  private final RegisterPackageService alta;

  public PackageController(RegisterPackageService alta) {
    this.alta = alta;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('packages:create')")
  @Operation(
      summary = "Registrar un paquete",
      description =
          """
          Registra un paquete de productos, **vacío e inactivo**: los productos entran
          uno a uno después (`POST /packages/{id}/products`) y publicarlo es otra
          operación (`PATCH /packages/{id}/status`), que exige descripción y al menos
          dos productos.

          **El paquete no declara precio: se calcula.** Su `price` es la suma de sus
          productos con su descuento, `listPrice` la suma sin descuentos y `savings` la
          diferencia, todo con el precio de catálogo **de hoy** y redondeado por
          producto a los decimales de la moneda. No se guarda en ninguna columna: si un
          producto cambia de precio, el paquete cambia solo. Enviar `price` —o `products`,
          o `status`— responde `400`.

          **La moneda es obligatoria e inmutable**, y solo se le asocian productos en
          esa moneda: un paquete recién creado no tiene productos y aun así sabe en qué
          se expresa. `scope` dice en qué vistas se publica —`TIENDA`, `HOTLINK`, `AMBOS`
          o `NINGUNO`, que existe y se activa pero no se ofrece en ninguna— y es del
          paquete: el alcance de sus productos no filtra dentro de él.

          La respuesta es la misma forma que el detalle: `items` vacío, los tres
          importes en cero, `exchange` nulo, y `offerable: false` con su motivo — «menos
          de dos productos». `offerable` y `offerableReason` viajan **siempre**.

          Exige `packages:create`. **Los `products:` no habilitan**: un actor con los
          cuatro permisos del catálogo y ninguno de paquetes recibe `403`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Paquete registrado, vacío y en estado `INACTIVO`.",
        content = @Content(schema = @Schema(implementation = PackageDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Datos inválidos, juntos (`VAL-001` a `VAL-004`), o un cuerpo con `price`, `products`"
                + " o `status` (`VAL-005`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `packages:create` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "409",
        description =
            "Código ya en uso —también por un paquete retirado— o nombre ya en uso por un paquete"
                + " vivo (`EX-001`, `EX-002`)"),
    @ApiResponse(responseCode = "422", description = "Moneda inexistente o inactiva (`EX-003`)")
  })
  public ResponseEntity<PackageDetailResponse> register(
      @Valid @RequestBody RegisterPackageRequest peticion) {
    PackageDetailResponse creado = alta.register(peticion);
    return ResponseEntity.created(URI.create("/api/v1/packages/" + creado.id())).body(creado);
  }
}
