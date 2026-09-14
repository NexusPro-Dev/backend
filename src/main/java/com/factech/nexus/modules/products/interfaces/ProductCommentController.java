package com.factech.nexus.modules.products.interfaces;

import com.factech.nexus.modules.products.application.CreateProductCommentRequest;
import com.factech.nexus.modules.products.application.ProductCommentPublicItem;
import com.factech.nexus.modules.products.application.ProductCommentResponse;
import com.factech.nexus.modules.products.application.UpdateProductCommentRequest;
import com.factech.nexus.modules.products.domain.service.CreateProductCommentService;
import com.factech.nexus.modules.products.domain.service.DeleteProductCommentService;
import com.factech.nexus.modules.products.domain.service.GetOwnProductCommentService;
import com.factech.nexus.modules.products.domain.service.GetProductCommentsService;
import com.factech.nexus.modules.products.domain.service.UpdateProductCommentService;
import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las reseñas de producto (`RF-PM-009` a `RF-PM-013`).
 *
 * <p>Cinco rutas bajo {@code /api/v1/products/{id}/comments}: el alta y la lista comparten la ruta
 * y se separan por verbo —el {@code POST} exige {@code products:comment} y el {@code GET} es
 * <b>público</b>—; la propia va en {@code /mine}, y la corrección y el retiro en {@code
 * /{commentId}}.
 *
 * <p><b>{@code /mine} va declarada antes que {@code /{commentId}} a propósito</b>, como {@code
 * /available} antes que {@code /{id}} en {@code ProductController}: Spring resuelve por
 * especificidad y no por orden, de modo que esto no cambia el comportamiento — cambia quién lo
 * entiende al leerlo. Que funcione lo fija `CA-PM-217`.
 */
@RestController
@RequestMapping("/api/v1/products/{id}/comments")
@Tag(name = "Reseñas", description = "Lo que quien compra dice de un producto.")
public class ProductCommentController {

  private final CreateProductCommentService alta;
  private final GetProductCommentsService lista;
  private final GetOwnProductCommentService propia;
  private final UpdateProductCommentService correccion;
  private final DeleteProductCommentService retiro;

  public ProductCommentController(
      CreateProductCommentService alta,
      GetProductCommentsService lista,
      GetOwnProductCommentService propia,
      UpdateProductCommentService correccion,
      DeleteProductCommentService retiro) {
    this.alta = alta;
    this.lista = lista;
    this.propia = propia;
    this.correccion = correccion;
    this.retiro = retiro;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('products:comment')")
  @Operation(
      summary = "Reseñar un producto",
      description =
          """
          Registra **la** reseña de quien llama sobre un producto: una puntuación entera de
          1 a 5 y un texto de 1 a 1000 caracteres, **las dos obligatorias** (`RN-PM-025`).

          **El autor sale del token.** El cuerpo no admite campo de persona: un `userId`
          es un campo desconocido y se rechaza con `400`. No existe forma de reseñar en
          nombre de otro.

          **Una por persona y producto, entre las vivas** (`RN-PM-026`). La segunda sobre el
          mismo producto responde `409` mientras la primera siga viva; quien cambie de
          opinión la corrige (`PATCH .../comments/{commentId}`). Retirada la suya, puede
          escribir otra.

          **Solo se reseña lo que se puede comprar** (`RN-PM-028`): un producto activo y no
          retirado. **El `404` no distingue** entre inexistente, inactivo y retirado — quien
          reseña es un cliente y ve la oferta, que tampoco distingue.

          **Un decimal en la puntuación se rechaza, no se redondea**, y una cadena tampoco es
          una puntuación: los dos responden `400`.

          Desde la primera reseña escrita, las cuatro lecturas del producto publican
          `rating` con el promedio (dos decimales, **nulo** sin reseñas) y la cantidad de
          reseñas **vivas** (`RN-PM-031`).

          **El texto se guarda tal cual y NO se sanea.** La lista es pública: el front debe
          escaparlo al pintarlo.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Reseña registrada. Sin autor: quien la recibe es quien la escribió.",
        content = @Content(schema = @Schema(implementation = ProductCommentResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador mal formado (`VAL-001`), puntuación ausente (`VAL-002`) o fuera de"
                + " 1..5 (`VAL-003`), texto vacío (`VAL-004`) o de más de 1000 caracteres"
                + " (`VAL-005`), o campos no admitidos en el cuerpo (`VAL-006`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `products:comment` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "El producto no existe, está inactivo o está retirado (`EX-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Quien llama ya tiene una reseña viva sobre este producto (`EX-002`)",
        content = @Content)
  })
  public ResponseEntity<ProductCommentResponse> create(
      @PathVariable UUID id, @RequestBody CreateProductCommentRequest peticion) {
    ProductCommentResponse creada = alta.create(id, peticion);
    return ResponseEntity.created(URI.create("/api/v1/products/" + id + "/comments/" + creada.id()))
        .body(creada);
  }

  @GetMapping
  @Operation(
      summary = "Consultar las reseñas de un producto",
      description =
          """
          Devuelve, **sin token**, las reseñas vivas de un producto, paginadas y de la más
          reciente a la más antigua. **Es público por decisión** (`RF-PM-012`): la pantalla
          del hotlink las necesita y no tiene con qué autenticarse.

          **Del autor solo viaja su nombre y apellido** (`RN-PM-030`): ni identificador, ni
          nombre de usuario, ni correo, ni estado, ni roles. Es la segunda ruta pública del
          sistema que publica el nombre de una persona, después del hotlink, y hereda sus dos
          decisiones.

          **La misma respuesta con token que sin él.** No marca cuál reseña es la de quien
          llama; para eso está `GET .../comments/mine`.

          **El `404` es uniforme**: producto inexistente, inactivo, retirado o identificador
          mal formado responden **el mismo cuerpo** (`RN-PM-028`). Un anónimo no debe poder
          saber si un identificador corresponde a un producto en preparación.

          `updatedAt` distinto de `createdAt` significa «corregida»; no hay campo aparte.

          **El texto NO está saneado**: escápelo al pintarlo.

          Acotada por origen: 120 por minuto sobre la familia `/api/v1/products/*/comments`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Página de reseñas vivas del producto."),
    @ApiResponse(
        responseCode = "400",
        description = "Paginación inválida (`VAL-003` de `shared/pagination`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description =
            "El producto no existe, está inactivo, está retirado o el identificador está mal"
                + " formado (`EX-001`) — el mismo cuerpo en los cuatro casos",
        content = @Content),
    @ApiResponse(
        responseCode = "429",
        description = "Límite de tasa por origen superado",
        content = @Content)
  })
  public PageResponse<ProductCommentPublicItem> list(
      @Parameter(description = "Identificador del producto (UUID)") @PathVariable("id")
          String productId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return lista.list(productId, page, size);
  }

  @GetMapping("/mine")
  @PreAuthorize("hasAuthority('products:comment')")
  @Operation(
      summary = "Consultar la reseña propia sobre un producto",
      description =
          """
          Devuelve **la** reseña viva de quien llama sobre el producto, con su identificador —
          que es lo que la corrección y el retiro reciben en la ruta—. **No admite parámetro
          de persona**: responde sobre quien llama y sobre nadie más.

          **El `404` no dice nada del producto**: responde igual si quien llama no ha
          reseñado, si retiró la suya o si el producto no existe. Y **sí responde** sobre un
          producto inactivo o retirado cuando la reseña existe: el autor tiene que poder
          llegar a la suya para corregirla o retirarla (`RN-PM-028`).
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La reseña de quien llama.",
        content = @Content(schema = @Schema(implementation = ProductCommentResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Identificador mal formado (`VAL-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `products:comment` (`AUTH-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "Quien llama no tiene reseña viva sobre este producto (`EX-001`)",
        content = @Content)
  })
  public ProductCommentResponse mine(@PathVariable UUID id) {
    return propia.mine(id);
  }

  @PatchMapping("/{commentId}")
  @PreAuthorize("hasAuthority('products:comment')")
  @Operation(
      summary = "Corregir la reseña propia",
      description =
          """
          Corrige la puntuación, el texto o los dos. **Lo que no viene no cambia**, y
          **ningún campo admite el nulo explícito**: los dos son obligatorios. Un cuerpo sin
          ningún campo responde `400`, como en la edición del producto.

          **Solo el autor** (`RN-PM-027`). El permiso `products:comment` habilita la operación;
          ser el autor la autoriza, y son dos cosas distintas: un administrador con el
          permiso corrige las suyas y recibe `403` en las ajenas. **No existe moderación.**

          Primero «existe» (`404`) y después «es tuya» (`403`). Una reseña **retirada**
          responde `404` también a su autor: no se revive corrigiendo. La reseña debe ser
          **del producto de la ruta**; si no, `404`.

          **Se corrige aunque el producto ya no se venda.** Un cuerpo cuyos valores
          coinciden con los guardados responde `200` sin escribir nada y sin avanzar
          `updatedAt`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La reseña corregida, con `updatedAt` avanzado si algo cambió.",
        content = @Content(schema = @Schema(implementation = ProductCommentResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador mal formado (`VAL-001`), puntuación nula o fuera de 1..5"
                + " (`VAL-002`, `VAL-003`), texto nulo, vacío o largo (`VAL-004`), campos no"
                + " admitidos (`VAL-006`) o cuerpo sin ningún campo (`VAL-005`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description =
            "Autenticado sin el permiso `products:comment` (`AUTH-002`), o **con él pero sin"
                + " ser el autor** (`EX-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description =
            "La reseña no existe, está retirada o no es del producto de la ruta (`EX-001`)",
        content = @Content)
  })
  public ProductCommentResponse update(
      @PathVariable UUID id,
      @PathVariable UUID commentId,
      @RequestBody(required = false) UpdateProductCommentRequest peticion) {
    return correccion.update(id, commentId, peticion);
  }

  @DeleteMapping("/{commentId}")
  @PreAuthorize("hasAuthority('products:comment')")
  @Operation(
      summary = "Retirar la reseña propia",
      description =
          """
          Retira lógicamente la reseña de quien llama, **sin motivo y sin cuerpo**. Es la
          primera baja de una entidad de negocio del sistema que no pide `reason`: el
          Art. V.13 admite desde el 14-09-2026 el **contenido propio** como excepción, porque
          el único «por qué» posible —su autor ya no la quiere ahí— está en el propio evento.
          El registro de eliminación se escribe igual, con la instantánea y un motivo fijo.

          Por eso es un `DELETE` y no un `POST /deletion`: el retiro del producto usa `POST`
          porque su cuerpo lleva el motivo, y aquí no hay cuerpo que proteger. Un cuerpo, si
          llega, se ignora.

          **Solo el autor** (`RN-PM-027`), sin excepción: el superadministrador recibe `403`
          sobre una ajena. **No existe moderación.**

          **Retirar dos veces responde `404`**, no `409`: la reseña retirada no la devuelve
          nadie, de modo que «retirada» y «no existe» son lo mismo. Retirada la suya, la
          persona puede escribir otra, y el promedio del producto la deja de contar en el
          acto.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Reseña retirada.", content = @Content),
    @ApiResponse(
        responseCode = "400",
        description = "Identificador mal formado (`VAL-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "401",
        description = "Token ausente o inválido (`AUTH-001`)",
        content = @Content),
    @ApiResponse(
        responseCode = "403",
        description =
            "Autenticado sin el permiso `products:comment` (`AUTH-002`), o **con él pero sin"
                + " ser el autor** (`EX-002`)",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description =
            "La reseña no existe, ya está retirada o no es del producto de la ruta (`EX-001`)",
        content = @Content)
  })
  public ResponseEntity<Void> delete(@PathVariable UUID id, @PathVariable UUID commentId) {
    retiro.delete(id, commentId);
    return ResponseEntity.noContent().build();
  }
}
