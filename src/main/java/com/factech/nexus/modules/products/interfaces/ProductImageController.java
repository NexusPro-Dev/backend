package com.factech.nexus.modules.products.interfaces;

import com.factech.nexus.modules.products.domain.models.ProductImage;
import com.factech.nexus.modules.products.domain.service.GetProductImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * `RF-PM-016` — la imagen de una portada, sin autenticación.
 *
 * <p><b>Es la única ruta del sistema que sirve bytes y no JSON</b>, y por eso vive sola y no dentro
 * de {@code ProductController}: mezclada entre veinte operaciones que devuelven JSON se perdería lo
 * que la distingue. <b>Sin {@code @PreAuthorize}, y es deliberado</b>: la ruta es pública por
 * decisión (`security.md` §6) — una de las cuatro lecturas que devuelven su dirección es el
 * hotlink, que no tiene con qué autenticarse, y un {@code <img>} no lleva cabecera {@code
 * Authorization}. La declaración está en {@code SecurityConfig}, solo en {@code GET}.
 *
 * <p><b>Cinco cabeceras, y las cinco a mano</b> (`spec.md` §6.2): el {@code Content-Type} real —el
 * detectado al subir—, {@code Cache-Control: public, max-age=31536000, immutable} porque una
 * dirección señala una imagen concreta que no cambia nunca —reemplazar la portada es otra
 * dirección—, {@code X-Content-Type-Options: nosniff} para que el navegador no adivine otro tipo
 * —la segunda mitad de dejar {@code SVG} fuera—, {@code Content-Disposition: inline} porque se
 * pinta y no se descarga, y {@code Content-Length}, que pone Spring con el {@code byte[]}.
 */
@RestController
@RequestMapping("/api/v1/product-images")
@Tag(name = "Productos")
public class ProductImageController {

  /** Un año, e inmutable: el navegador no vuelve a preguntar (`spec.md` §2). */
  private static final CacheControl UN_ANO_INMUTABLE =
      CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable();

  private final GetProductImageService imagenes;

  public ProductImageController(GetProductImageService imagenes) {
    this.imagenes = imagenes;
  }

  // Sin `produces`, a propósito: la ruta tiene un solo tipo de respuesta —el de
  // la imagen— y NO negocia. Con `produces`, un `Accept: application/json`
  // recibiría `406` antes de llegar aquí (`spec.md` §13). Los tres tipos los
  // declara el contrato, abajo.
  @GetMapping("/{imageId}")
  // `security: []` en el contrato, como las reseñas: le dice al cliente generado
  // que no envíe token aquí.
  @io.swagger.v3.oas.annotations.security.SecurityRequirements
  @Operation(
      summary = "Obtener la imagen de una portada, sin autenticación",
      description =
          """
          Devuelve **los bytes** de la imagen de portada de un producto, con el
          `Content-Type` real —el detectado al subirla— y **sin token**: la
          dirección la dan las cuatro lecturas del producto en `coverImageUrl`,
          y una de ellas —el hotlink— es pública, de modo que un `<img>` tiene
          que poder pintarla sin credencial. Con token responde lo mismo.

          **La dirección señala una imagen concreta y no «la portada de un
          producto»**: cada subida estrena identificador y la reemplazada se
          borra, de modo que una dirección **nunca cambia de contenido** — o
          sirve siempre la misma imagen, o responde `404`. Por eso se sirve con
          `Cache-Control: public, max-age=31536000, immutable`: el navegador la
          guarda un año y no vuelve a preguntar, y una portada nueva es otra
          dirección.

          **No mira el producto**: sirve la imagen aunque el producto esté
          inactivo, retirado o sea de alcance `TIENDA` (`requirements/pm.md`
          §5.2.9). El identificador no se lista en ningún sitio sin token para
          lo que no se publica. `404` con el mismo cuerpo para un identificador
          inexistente, reemplazado o quitado. Solo `JPEG`, `PNG` y `WebP`, con
          `X-Content-Type-Options: nosniff`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Los bytes de la imagen, con su tipo real y caché inmutable de un año.",
        content = {
          @Content(
              mediaType = MediaType.IMAGE_JPEG_VALUE,
              schema = @Schema(type = "string", format = "binary")),
          @Content(
              mediaType = MediaType.IMAGE_PNG_VALUE,
              schema = @Schema(type = "string", format = "binary")),
          @Content(mediaType = "image/webp", schema = @Schema(type = "string", format = "binary"))
        }),
    @ApiResponse(
        responseCode = "400",
        description = "Identificador sin forma canónica (`VAL-001`)",
        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
    @ApiResponse(
        responseCode = "404",
        description = "La imagen no existe — nunca existió, se reemplazó o se quitó (`EX-001`)",
        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
    @ApiResponse(
        responseCode = "429",
        description = "Demasiadas peticiones desde ese origen",
        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
    @ApiResponse(
        responseCode = "500",
        description = "Fallo no controlado (`ERR-500`)",
        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
  })
  public ResponseEntity<byte[]> imagen(@PathVariable UUID imageId) {
    ProductImage imagen = imagenes.get(imageId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(imagen.getContentType()))
        .cacheControl(UN_ANO_INMUTABLE)
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
        .body(imagen.getContent());
  }
}
