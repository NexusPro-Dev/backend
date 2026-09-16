package com.factech.nexus.modules.products.interfaces;

import com.factech.nexus.modules.products.application.HotlinkResponse;
import com.factech.nexus.modules.products.application.PackageHotlinkResponse;
import com.factech.nexus.modules.products.domain.service.GetHotlinkService;
import com.factech.nexus.modules.products.domain.service.GetPackageHotlinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El enlace público que un vendedor reparte (`RF-PM-008`).
 *
 * <p><b>Sin `@PreAuthorize`, y a propósito</b>: la ruta está declarada pública en {@code
 * SecurityConfig} y en la lista de rutas sin permiso de {@code EndpointPermissionsIT}. Los dos
 * sitios hacen falta — el primero para que responda, el segundo para que una ruta pública no se
 * cuele por descuido.
 */
@RestController
@RequestMapping("/api/v1/hotlinks")
@Tag(name = "Hotlinks", description = "El enlace público que un vendedor reparte")
public class HotlinkController {

  private final GetHotlinkService hotlinks;
  private final GetPackageHotlinkService paquetes;

  public HotlinkController(GetHotlinkService hotlinks, GetPackageHotlinkService paquetes) {
    this.hotlinks = hotlinks;
    this.paquetes = paquetes;
  }

  @GetMapping("/{username}/{code}")
  @Operation(
      summary = "Consultar un hotlink",
      description =
          """
          Devuelve, **sin token**, el producto que el enlace señala y el **nombre
          y apellido** de quien lo reparte.

          **Es público por decisión, no por definición.** Las otras cinco rutas
          públicas del sistema lo son porque quien las llama no puede portar
          todavía un token; esta lo es porque un enlace se abre antes de
          registrarse.

          **Solo publica productos activos y de alcance `HOTLINK` o `AMBOS`**
          (`HOTLINKS` hasta el 15-09-2026), y solo el
          nombre de quien porta un rol de tipo `VENDEDOR`. Ni correo, ni
          identificador, ni estado, ni roles.

          **De la membresía se publican tres campos** —código, nombre y color—,
          y no los cinco que devuelven los otros endpoints: el identificador no
          sirve a quien no puede llamar a nada más, y el nivel publicaría la
          forma de la cadena comercial sin token.

          **El precio llega en la moneda del producto y, si hay tasa vigente,
          convertido a la moneda por omisión.** `rate` viaja como **cadena**
          porque tiene ocho decimales y un número JSON pasa por coma flotante en
          cualquier cliente JavaScript.

          **Viene UN importe** (`RN-PM-024`, reescrita el 12-09-2026): `price`,
          el que se cobra. **El precio de compra no viaja por aquí ni se
          consulta**: es lo que NEXUS paga por el producto, y este endpoint es
          público. Entre el 08-09-2026 y el 12-09-2026 viajó también
          `publicPrice`, cuando ese importe era lo que se anunciaba; ese campo
          **ya no existe**. **La conversión se calcula sobre `price`.**

          **Viene `videoUrl`, y sin token** (`RN-PM-032`): la dirección del video
          que presenta el producto, **tal cual la escribió administración** y
          **presente y nula** cuando no tiene. El sistema no la sigue ni la
          valida más allá de su forma: un enlace roto se publica igual. Es la
          única columna opcional del producto que este endpoint trae y el
          precio de compra no — el costo enseñaría el margen; el video existe
          para que lo vean.

          **La conversión es INFORMATIVA**: lo que se cobra no es ese número. Una
          venta va en una sola moneda y congela su importe al registrarse; esta
          conversión se calcula al vuelo, cambia el día que cambie la tasa y **no
          reserva nada**. Cuando no hay tasa vigente —o el producto ya está en la
          moneda de casa— `exchange` llega **presente y nulo**, y el producto se
          devuelve igual.

          **Todo lo que no procede responde el MISMO `404`**: usuario
          inexistente, persona que no es vendedora, código inexistente, producto
          inactivo, retirado o de alcance `TIENDA` o `NINGUNO`. **No dice cuál falló**, y esa
          uniformidad es deliberada — distinguirlos convertiría el enlace en un
          oráculo que dice quién existe.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El enlace, con su producto y su vendedor.",
        content = @Content(schema = @Schema(implementation = HotlinkResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description =
            "El enlace no lleva a ninguna parte. **El mismo cuerpo en los seis casos** (`EX-001`)"),
    @ApiResponse(responseCode = "429", description = "Demasiadas peticiones desde ese origen")
  })
  public HotlinkResponse hotlink(@PathVariable String username, @PathVariable("code") String code) {
    return hotlinks.hotlink(username, code);
  }

  @GetMapping("/{username}/packages/{code}")
  @Operation(
      summary = "Consultar el hotlink de un paquete",
      description =
          """
          Devuelve, **sin token**, el paquete que el enlace señala —con sus productos,
          el descuento de cada uno y **la cuenta hecha**— y el nombre y apellido de
          quien lo reparte.

          **Cada producto va en la misma forma que el hotlink del producto**, tal
          cual: código, nombre, descripción, icono, video, portada, membresía en tres
          campos, precio, conversión y `rating`. A su lado, `discount` y
          `priceInPackage` — lo que vale ese producto **dentro** del paquete, con el
          precio de catálogo de hoy. `listPrice` es la suma sin descuentos, `price` lo
          que vale el paquete y `savings` la diferencia; `exchange` convierte `price`
          a la moneda de casa si hay tasa. **`priceInPackage` es la cuenta de hoy y
          no una reserva**: si un producto cambia de precio, el paquete cambia solo,
          y un fijo que hoy supera su precio cuenta cero.

          **Solo publica paquetes activos y de alcance `HOTLINK` o `AMBOS`**, y **el
          alcance de los productos no filtra dentro del paquete**: el canal lo decide
          el paquete. Sin `purchasePrice` ni `status` en ningún nivel.

          **Todo lo que no procede responde el MISMO `404`, y el mismo que el hotlink
          del producto**: usuario inexistente, persona que no es vendedora, código
          inexistente, paquete inactivo, retirado o de alcance `TIENDA` o `NINGUNO`,
          y **también el paquete que hoy no se puede ofrecer** —un producto suyo
          inactivo o retirado, menos de dos productos, sin descripción, o **fuera de
          su vigencia** (`RN-PM-047`): todavía no empieza o ya terminó—. Dentro de
          ella, `validFrom` y `validTo` viajan para que quien abre el enlace sepa
          hasta cuándo vale. **No dice cuál
          falló**: distinguirlos publicaría, sin token, que el paquete existe y qué le
          pasa. Quien tiene que saberlo es administración, y lo sabe por el detalle.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El enlace, con su paquete y su vendedor.",
        content = @Content(schema = @Schema(implementation = PackageHotlinkResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description =
            "El enlace no lleva a ninguna parte. **El mismo cuerpo en todos los casos**, y el"
                + " mismo que el hotlink del producto (`EX-001`)"),
    @ApiResponse(responseCode = "429", description = "Demasiadas peticiones desde ese origen")
  })
  public PackageHotlinkResponse hotlinkDePaquete(
      @PathVariable String username, @PathVariable("code") String code) {
    return paquetes.hotlink(username, code);
  }
}
