package com.factech.nexus.modules.products.interfaces;

import com.factech.nexus.modules.products.application.HotlinkResponse;
import com.factech.nexus.modules.products.domain.service.GetHotlinkService;
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

  public HotlinkController(GetHotlinkService hotlinks) {
    this.hotlinks = hotlinks;
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

          **Solo publica productos activos y de alcance `HOTLINKS`**, y solo el
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

          **La conversión es INFORMATIVA**: lo que se cobra no es ese número. Una
          venta va en una sola moneda y congela su importe al registrarse; esta
          conversión se calcula al vuelo, cambia el día que cambie la tasa y **no
          reserva nada**. Cuando no hay tasa vigente —o el producto ya está en la
          moneda de casa— `exchange` llega **presente y nulo**, y el producto se
          devuelve igual.

          **Todo lo que no procede responde el MISMO `404`**: usuario
          inexistente, persona que no es vendedora, código inexistente, producto
          inactivo, retirado o de alcance `TIENDA`. **No dice cuál falló**, y esa
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
}
