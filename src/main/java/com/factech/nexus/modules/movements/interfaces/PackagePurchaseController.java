package com.factech.nexus.modules.movements.interfaces;

import com.factech.nexus.modules.movements.application.BuyPackageRequest;
import com.factech.nexus.modules.movements.application.PurchaseResponse;
import com.factech.nexus.modules.movements.domain.service.BuyPackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La compra de un paquete (`RF-MV-012`).
 *
 * <p><b>El recurso es el paquete y no el movimiento</b>, al revés que `RF-MV-001` —que entra por
 * {@code POST /api/v1/movements}—: <b>lo que se compra manda sobre lo que se produce</b>, porque es
 * lo que el cliente tiene delante cuando pulsa (`plan.md` §4). Lo que se produce —una venta— viaja
 * en la respuesta y en el {@code Location}, que apunta al detalle propio de `RF-MV-008`.
 *
 * <p><b>Vive en `MV` aunque cuelgue de {@code /packages}</b>: la venta es de este módulo, y `PM` no
 * sabe registrarla. Que dos controladores compartan prefijo es un detalle de enrutado; que uno
 * escriba en las tablas del otro sería una frontera rota.
 *
 * <p><b>Sin {@code @PreAuthorize}, y a propósito.</b> Las demás rutas de {@code /api/v1/packages}
 * son de administración y exigen permiso; esta es una compra propia, como `RF-MV-002` y
 * `RF-PM-007`, y el alcance lo da la credencial: <b>el sujeto no viaja en la petición</b>, de modo
 * que no hay forma de comprar a nombre de otro. Compartir prefijo con rutas protegidas es
 * exactamente donde se cuela un permiso que sobra o que falta, y por eso la ausencia se declara en
 * la lista blanca de {@code EndpointPermissionsIT} y no se deja a la interpretación de quien lea
 * este archivo.
 */
@Tag(name = "Movimientos")
@RestController
@RequestMapping("/api/v1/packages")
public class PackagePurchaseController {

  private final BuyPackageService compra;

  public PackagePurchaseController(BuyPackageService compra) {
    this.compra = compra;
  }

  @Operation(
      summary = "Comprar un paquete para uno mismo",
      description =
          """
          **Usted se compra el paquete entero** que su oferta le muestra, pagando **lo que ese
          paquete vale** —la suma de sus productos rebajados, el mismo `price` que publica la
          oferta— y recibiendo **cada producto** con el descuento que el paquete le declara,
          congelado.

          **El cuerpo lleva el método de pago y nada más.** El paquete va en la ruta **por su
          código** —el que publica la oferta—, sin distinguir mayúsculas. **No se
          admiten** productos, cantidades, precios ni descuentos: el paquete se compra
          **entero** —no se elige qué llevarse—, **uno** —no hay cantidad— y **solo** —sin
          productos sueltos ni un segundo paquete; quien quiera dos cosas hace dos compras—.
          El descuento es el que el paquete declara, y un descuento enviado no tiene dónde
          caer. Tampoco viajan el cliente —es usted— ni la fecha —es ahora—.

          **`paymentMethodId` es condicional en los dos sentidos.** Obligatorio si el paquete
          tiene importe; **prohibido** si vale cero, que se registra como gratuita: envíe `{}`.

          **La venta nace `PENDIENTE`, y eso significa que no concede nada.** No sube de
          nivel, no habilita nada y no comisiona hasta que se confirme el pago. Se registra
          **una línea por producto del paquete**, cantidad uno, cada una con lo copiado del
          catálogo —nombre, descripción, precio unitario, vigencia— y **su rebaja explicada**:
          `discounts[]` dice cómo se pactó (`type`, `value`) y cuánto valió en dinero
          (`discountValue`), y `lineDiscount` es lo que se restó. `payableAmount` es la suma de
          las líneas rebajadas; `totalAmount` lo que valdrían sin rebaja; `discountAmount` la
          diferencia. El paquete queda anotado en `packageId`.

          **Lo copiado queda congelado.** Corregir después el descuento del paquete, el precio
          de un producto o quitar un producto del paquete no cambia esta venta.

          **Se compra tal como está hoy.** Al registrar se vuelve a comprobar todo lo que la
          oferta ya miró: que el paquete esté activo, publicado en la tienda, con sus
          productos, **dentro de su vigencia**, y que le corresponda a usted por su membresía.
          **Y se compra entero**: si un producto del paquete no procede —inactivo, retirado,
          fuera de su oferta, o un upgrade que baja de nivel— se rechaza la compra completa
          nombrando el producto, y **no se registra nada**.

          **La respuesta no lleva el vendedor**: se atribuye a su superior comercial vigente
          —o a usted mismo si no cuelga de nadie— y se congela en cada línea, pero a quien
          compra no se le enseña. Es la misma forma que devuelve `GET /api/v1/movements/mine/{id}`
          sin `seller`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Venta registrada, pendiente de pago."),
    @ApiResponse(
        responseCode = "400",
        description = "Un campo que el cuerpo no admite: productos, cantidad, precio o descuento.",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`). Comprar exige sesión.",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description =
            "Lo que solo se sabe después de resolver: el paquete no se ofrece hoy —inactivo,"
                + " vencido, sin descripción, con menos de dos productos, fuera de la tienda o"
                + " con un producto inactivo o retirado— (`EX-002`, con el mismo motivo que publica"
                + " el catálogo); no le corresponde a usted por su membresía (`EX-003`); un"
                + " producto no está en su oferta (`EX-004`); el upgrade BAJA de nivel —renovar el"
                + " mismo sí se admite— (`EX-005`); su cuenta no puede operar todavía (`EX-006`);"
                + " el método de pago no cuadra con el importe (`RN-MV-022`) o está desactivado"
                + " (`EX-010`).",
        content = @Content),
    @ApiResponse(
        responseCode = "422",
        description =
            "Una referencia que no resuelve: ningún paquete tiene ese código, o está retirado"
                + " (`EX-001`); o el método de pago no existe (`EX-010`).",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  @PostMapping("/{code}/purchases")
  public ResponseEntity<PurchaseResponse> comprar(
      @PathVariable String code, @Valid @RequestBody(required = false) BuyPackageRequest peticion) {
    PurchaseResponse venta = compra.buy(code, peticion);
    return ResponseEntity.created(URI.create("/api/v1/movements/mine/" + venta.id())).body(venta);
  }
}
