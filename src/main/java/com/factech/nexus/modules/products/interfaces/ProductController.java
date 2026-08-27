package com.factech.nexus.modules.products.interfaces;

import com.factech.nexus.modules.products.application.ChangeProductStatusRequest;
import com.factech.nexus.modules.products.application.DeleteProductRequest;
import com.factech.nexus.modules.products.application.ListProductsRequest;
import com.factech.nexus.modules.products.application.ProductDetailResponse;
import com.factech.nexus.modules.products.application.ProductPageResponse;
import com.factech.nexus.modules.products.application.ProductResponse;
import com.factech.nexus.modules.products.application.RegisterProductRequest;
import com.factech.nexus.modules.products.domain.service.ChangeProductStatusService;
import com.factech.nexus.modules.products.domain.service.DeleteProductService;
import com.factech.nexus.modules.products.domain.service.GetProductService;
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
 * El catálogo de productos (`PM`).
 *
 * <p>El alta, el listado, el detalle, el cambio de estado y el retiro; falta la edición
 * (`RF-PM-004`) y la oferta del cliente (`RF-PM-007`).
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
  private final GetProductService detalle;
  private final ChangeProductStatusService estado;
  private final DeleteProductService retiro;

  public ProductController(
      RegisterProductService alta,
      ListProductsService listado,
      GetProductService detalle,
      ChangeProductStatusService estado,
      DeleteProductService retiro) {
    this.alta = alta;
    this.listado = listado;
    this.detalle = detalle;
    this.estado = estado;
    this.retiro = retiro;
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

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('products:read')")
  @Operation(
      summary = "Consultar el detalle de un producto",
      description =
          """
          Devuelve el producto con su membresía destino y su moneda **resueltas**,
          sin exigir una segunda consulta.

          **Un producto retirado se devuelve marcado como tal**, no como
          inexistente: `deletedAt` dice desde cuándo y `deletionReason` **por
          qué**. Los dos campos **solo aparecen si el producto está retirado** —
          su ausencia significa que sigue vivo—. El motivo llega con
          `products:read` y **sin exigir permiso de auditoría**; es la
          contrapartida asumida de que el detalle lo devuelva.

          **No devuelve autoría en ninguna forma**: ni quién lo creó, ni quién lo
          corrigió, ni quién lo retiró. Eso vive en la auditoría y tiene su
          propio permiso.

          `targetMembership` y `validityDays` viajan **presentes en nulo** cuando
          no aplican: un servicio no tiene destino y un producto puede no
          caducar, y un campo ausente es indistinguible de uno que el cliente no
          conoce.

          El **nivel** del destino es el **actual**, no el que tenía cuando se
          creó el producto: la cadena de membresías se reordena al insertar un
          eslabón.

          Un identificador con forma laxa se rechaza como **dato inválido**
          (`400`), no como recurso no encontrado.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El producto, con su destino y su moneda resueltos.",
        content = @Content(schema = @Schema(implementation = ProductDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Identificador sin forma canónica (`VAL-001`)",
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
        responseCode = "404",
        description = "No existe un producto con ese identificador (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public ProductDetailResponse detalle(@PathVariable UUID id) {
    return detalle.detail(id);
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAuthority('products:update')")
  @Operation(
      summary = "Publicar o retirar de la oferta un producto",
      description =
          """
          Cambia el estado del producto entre `ACTIVO` e `INACTIVO`. **Es un
          recurso propio y no un campo de la edición**: publicar y corregir son
          decisiones distintas, y mezclarlas haría que una corrección de texto
          pudiera poner algo a la venta.

          **Pedir el estado que el producto ya tiene devuelve `200` sin cambiar
          nada y sin registrar evento.** No es un error: quien pulsa dos veces el
          mismo botón no ha hecho nada malo.

          **No se publica un producto sin descripción** (`RN-PM-014`). Sí se
          permite **desactivarlo** sin ella: la regla acota lo que se ofrece, no
          lo que se retira.

          **Solo puede haber un upgrade activo hacia cada membresía destino**
          (`RN-PM-004`). Al activar uno cuyo destino ya está ocupado, el rechazo
          **nombra el producto que lo ocupa**, para que se sepa cuál desactivar.
          Desactivar no comprueba nada: liberar un destino nunca produce
          conflicto.

          **Un producto retirado no vuelve a la venta por aquí**: responde que no
          existe.

          No se exige motivo para activar ni para desactivar.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El producto, con su estado ya aplicado.",
        content = @Content(schema = @Schema(implementation = ProductDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador sin forma canónica (`VAL-001`), estado ausente o fuera de su dominio"
                + " (`VAL-002`), o activación de un producto sin descripción (`VAL-003`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `products:update` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe un producto vivo con ese identificador (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Ya hay otro upgrade activo hacia esa membresía destino (`EX-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public ProductDetailResponse cambiarEstado(
      @PathVariable UUID id, @Valid @RequestBody ChangeProductStatusRequest peticion) {
    return estado.change(id, peticion);
  }

  @PostMapping("/{id}/deletion")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('products:delete')")
  @Operation(
      summary = "Retirar un producto del catálogo",
      description =
          """
          Retira el producto: **eliminación lógica y con motivo** (Art. V.13).
          La fila se conserva —lo vendido tiene que seguir resolviendo a lo que
          se vendió— y el producto deja de ofrecerse.

          **`POST` sobre un subrecurso y no `DELETE` con cuerpo**, igual que al
          eliminar un rol o una persona: RFC 9110 no define semántica para el
          cuerpo de un `DELETE` y un intermediario puede descartarlo, con lo que
          la petición se convertiría en un rechazo por motivo ausente que quien
          la envió no puede entender ni corregir. Y tampoco en la URL, donde el
          motivo quedaría escrito en los registros de acceso de cualquier proxy.

          **El motivo es obligatorio** y se comprueba lo primero de todo, antes
          de tocar la base.

          **El estado no se modifica al retirar.** El registro de eliminación
          conserva si el producto **estaba a la venta**: desactivarlo «de paso»
          haría que todos los registros dijeran «inactivo» y ese dato dejaría de
          significar nada.

          **Qué libera el retiro y qué no**: el **destino** del upgrade queda
          libre para que otro se active, y el **nombre** queda libre para otro
          producto. El **código no se libera nunca** — el día que una factura
          diga `UPGRADE_ORO` tiene que resolver a un solo producto para siempre.

          **No es idempotente a propósito**: retirar uno ya retirado devuelve
          `409`. Dos motivos sobre un solo hecho es evidencia contradictoria.

          No devuelve el producto: lo que se acaba de retirar no es algo que el
          sistema deba seguir ofreciendo a quien lo pidió.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Producto retirado.", content = @Content),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador sin forma canónica (`VAL-001`), motivo ausente o en blanco (`VAL-002`)"
                + " o demasiado largo (`VAL-003`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `products:delete` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "No existe un producto con ese identificador (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "El producto ya estaba retirado (`EX-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content)
  })
  public void retirar(
      @PathVariable UUID id, @RequestBody(required = false) DeleteProductRequest peticion) {
    retiro.delete(id, peticion);
  }
}
