package com.factech.nexus.modules.academy.interfaces;

import com.factech.nexus.modules.academy.domain.models.AcademyImage;
import com.factech.nexus.modules.academy.domain.service.GetAcademyImageService;
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
 * Las portadas de academia, públicas (`RF-AC-032`): {@code ProductImageController} sobre {@code
 * academy_images}. Una ruta propia porque la tabla es otra (`ac.md` §5.2.3); pública <b>solo en
 * {@code GET}</b> —subir vive bajo cada entidad—, con su propia familia de cota en {@code
 * RateLimitFilter}.
 */
@RestController
@RequestMapping("/api/v1/academy-images")
@Tag(name = "Cursos")
public class AcademyImageController {

  /** Un año, e inmutable: cada subida estrena dirección. */
  private static final CacheControl UN_ANO_INMUTABLE =
      CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable();

  private final GetAcademyImageService imagenes;

  public AcademyImageController(GetAcademyImageService imagenes) {
    this.imagenes = imagenes;
  }

  // Sin `produces`, como la de `PM`: un solo tipo de respuesta, el de la imagen,
  // y un `Accept: application/json` no debe recibir `406`.
  @GetMapping("/{imageId}")
  @io.swagger.v3.oas.annotations.security.SecurityRequirements
  @Operation(
      summary = "Obtener la imagen de una portada de academia, sin autenticación",
      description =
          """
          Devuelve **los bytes** de la portada de una categoría, un curso o un
          módulo, con el `Content-Type` real —el detectado al subirla— y **sin
          token**: las lecturas de academia la publican en `coverImageUrl`, y un
          `<img>` no lleva credencial. Con token responde lo mismo.

          **La dirección señala una imagen concreta**: cada subida estrena
          identificador y la reemplazada se borra, de modo que una dirección nunca
          cambia de contenido. Se sirve con `Cache-Control: public,
          max-age=31536000, immutable`.

          **No mira a quién pertenece**: sirve la portada de una categoría retirada
          o de un curso inactivo igual. `404` con el mismo cuerpo para un
          identificador inexistente, reemplazado o quitado. Solo `JPEG`, `PNG` y
          `WebP`, con `X-Content-Type-Options: nosniff`.
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
    AcademyImage imagen = imagenes.get(imageId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(imagen.getContentType()))
        .cacheControl(UN_ANO_INMUTABLE)
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
        .body(imagen.getContent());
  }
}
