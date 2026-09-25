package com.factech.nexus.modules.academy.interfaces;

import com.factech.nexus.modules.academy.application.CourseCategoryDetailResponse;
import com.factech.nexus.modules.academy.application.CourseCategoryPageResponse;
import com.factech.nexus.modules.academy.application.DeleteCourseCategoryRequest;
import com.factech.nexus.modules.academy.application.ListCourseCategoriesRequest;
import com.factech.nexus.modules.academy.application.RegisterCourseCategoryRequest;
import com.factech.nexus.modules.academy.application.UpdateCourseCategoryRequest;
import com.factech.nexus.modules.academy.domain.service.DeleteCourseCategoryService;
import com.factech.nexus.modules.academy.domain.service.GetCourseCategoryService;
import com.factech.nexus.modules.academy.domain.service.ListCourseCategoriesService;
import com.factech.nexus.modules.academy.domain.service.RegisterCourseCategoryService;
import com.factech.nexus.modules.academy.domain.service.UpdateCourseCategoryService;
import com.factech.nexus.modules.academy.domain.service.UploadCourseCategoryCoverService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Las categorías del catálogo de cursos (`AC`, `RF-AC-001` a `RF-AC-005`): las cinco operaciones de
 * administración. La portada de la categoría llegará con `RF-AC-006` y `RF-AC-007`, y la sirve sin
 * token `RF-AC-032`.
 *
 * <p><b>Un recurso propio con sus cuatro permisos</b> (`course-categories:`): una categoría existe
 * sin cursos y la administra quien organiza el catálogo, que puede no ser quien arma un curso. Los
 * {@code courses:} no habilitan aquí ni una operación.
 *
 * <p><b>El alta, el detalle y la corrección devuelven la misma forma</b>, con los cursos vivos
 * dentro: el frontend tiene una sola pantalla de categoría.
 */
@RestController
@RequestMapping("/api/v1/course-categories")
@Tag(name = "Categorías de cursos", description = "Los cajones del catálogo de cursos.")
public class CourseCategoryController {

  private final RegisterCourseCategoryService alta;
  private final ListCourseCategoriesService listado;
  private final GetCourseCategoryService detalle;
  private final UpdateCourseCategoryService correccion;
  private final DeleteCourseCategoryService retiro;
  private final UploadCourseCategoryCoverService portada;

  public CourseCategoryController(
      RegisterCourseCategoryService alta,
      ListCourseCategoriesService listado,
      GetCourseCategoryService detalle,
      UpdateCourseCategoryService correccion,
      DeleteCourseCategoryService retiro,
      UploadCourseCategoryCoverService portada) {
    this.portada = portada;
    this.alta = alta;
    this.listado = listado;
    this.detalle = detalle;
    this.correccion = correccion;
    this.retiro = retiro;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('course-categories:create')")
  @Operation(
      summary = "Registrar una categoría",
      description =
          """
          Registra un cajón del catálogo de cursos con **nombre, color, icono y orden**,
          obligatorios, y descripción opcional. **Nace viva, sin portada y sin cursos**:
          la categoría no tiene estado —está o está retirada—, la imagen se sube después
          con su propia petición, y los cursos se clasifican desde el curso.

          **El color va sin `#` y sale en mayúsculas**: seis dígitos hexadecimales, que
          se admiten en minúsculas y se normalizan. **No es único**: dos categorías
          pueden compartir color. **El icono es un nombre**, no una imagen; el backend
          lo guarda y no sabe pintarlo. **El orden es una posición**: entero mayor o
          igual que cero, no único, y dos con el mismo número se desempatan por
          antigüedad.

          El nombre es único entre las categorías **vivas**, sin distinguir mayúsculas
          ni acentos; una retirada libera el suyo. Enviar `status`, `courses` o
          `coverImageUrl` responde `400`.

          La respuesta es la misma forma que el detalle: `courseCount` en cero,
          `courses` vacío y `coverImageUrl` presente y nulo. Exige
          `course-categories:create`; **los `courses:` no habilitan**.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Categoría registrada, viva y sin cursos.",
        content = @Content(schema = @Schema(implementation = CourseCategoryDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Datos inválidos, juntos (`VAL-001` a `VAL-004`), o un cuerpo con `status`, `courses`"
                + " o `coverImageUrl` (`VAL-005`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `course-categories:create` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "409",
        description = "Nombre ya en uso por una categoría viva (`EX-001`)")
  })
  public ResponseEntity<CourseCategoryDetailResponse> register(
      @Valid @RequestBody RegisterCourseCategoryRequest peticion) {
    CourseCategoryDetailResponse creada = alta.register(peticion);
    return ResponseEntity.created(URI.create("/api/v1/course-categories/" + creada.id()))
        .body(creada);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('course-categories:read')")
  @Operation(
      summary = "Consultar las categorías",
      description =
          """
          La lista de administración de las categorías, paginada, **en el orden en que
          se enseñan**: por `displayOrder` ascendente, con la más antigua primero
          entre dos que compartan número. Cada fila trae `coverImageUrl` —presente y
          nulo si no hay— y **`courseCount`, cuántos cursos vivos tiene**: vivos y no
          ofrecidos; un curso inactivo cuenta, uno retirado no. Sin descripción ni
          cursos: para eso está el detalle.

          Busca por `q` —nombre, sin distinguir mayúsculas ni acentos—. **Excluye las
          retiradas** salvo `includeDeleted=true`, y entonces las trae con `deletedAt`.
          «Vacía» no es filtro: se publica por fila.

          Orden: `displayOrder` (omisión, ascendente), `name` y `createdAt`
          (descendente por omisión), con `,asc`/`,desc`. Los parámetros inválidos se
          devuelven **juntos** con `400`. Exige `course-categories:read`;
          `courses:read` no habilita.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La página de categorías.",
        content = @Content(schema = @Schema(implementation = CourseCategoryPageResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Orden o paginación inválidos, juntos (`VAL-001` a `VAL-003`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `course-categories:read` (`AUTH-002`)")
  })
  public CourseCategoryPageResponse listar(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute
          ListCourseCategoriesRequest filtros) {
    return listado.list(filtros);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('course-categories:read')")
  @Operation(
      summary = "Consultar el detalle de una categoría",
      description =
          """
          La categoría entera: lo suyo, `coverImageUrl` —presente y nulo si no hay— y
          **sus cursos vivos en el orden en que se enseñan**, cada uno con su `status`
          y con `offerable`, que es del curso y no de la pareja. Un curso retirado que
          estuvo clasificado no aparece ni cuenta; uno inactivo aparece con su estado
          y cuenta.

          **Se devuelve también una retirada**, con `deletedAt` y `deletionReason`
          —leído de la auditoría de eliminación— y con sus cursos vivos igual: siguen
          clasificados en ella aunque el aula ya no los enseñe bajo ese cajón. Es la
          lectura de administración, y quien tiene `course-categories:read` ve el
          catálogo completo.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La categoría con sus cursos.",
        content = @Content(schema = @Schema(implementation = CourseCategoryDetailResponse.class))),
    @ApiResponse(responseCode = "400", description = "Identificador inválido (`VAL-001`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `course-categories:read` (`AUTH-002`)"),
    @ApiResponse(responseCode = "404", description = "La categoría no existe (`EX-001`)")
  })
  public CourseCategoryDetailResponse detalle(@PathVariable UUID id) {
    return detalle.detail(id);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('course-categories:update')")
  @Operation(
      summary = "Editar una categoría",
      description =
          """
          Corrección **parcial**: solo lo que viene cambia. Admite `name`,
          `description`, `color`, `icon` y `displayOrder`, por separado o juntos, y
          exige al menos uno. **Ausente y nulo no significan lo mismo**: ausente es «no
          lo toques», nulo es «vacíalo» — y **solo la descripción admite vaciarse**; el
          nulo en los otros cuatro responde `400`, junto con los demás errores de
          forma.

          **Sin inmutables**: la categoría no lleva código ni moneda. **La portada no
          se corrige por aquí**: tiene sus propios endpoints, y `coverImageUrl` en el
          cuerpo responde `400` como campo no admitido. **El color se normaliza antes
          de comparar**: enviar `1e88e5` sobre `1E88E5` no es un cambio y no se audita.

          **Reordenar es corregir el número**: cambiar el `displayOrder` de una
          categoría no mueve a ninguna otra; dos con el mismo número se desempatan por
          antigüedad. El nombre nuevo no puede ser el de **otra** categoría viva —el de
          una retirada sí, y cambiar solo la caja del propio también—. Una retirada no
          se corrige (`404`).

          Un cuerpo sin cambios de valor responde `200` sin avanzar `updatedAt` ni
          auditar. La respuesta es la forma del detalle. Exige `course-categories:update`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La categoría corregida, en la forma del detalle.",
        content = @Content(schema = @Schema(implementation = CourseCategoryDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido, campos que no admiten vaciarse, cuerpo vacío o campo no"
                + " admitido (`VAL-001` a `VAL-007`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `course-categories:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "La categoría no existe o está retirada (`EX-002`)"),
    @ApiResponse(
        responseCode = "409",
        description = "Nombre ya en uso por otra categoría viva (`EX-001`)")
  })
  public CourseCategoryDetailResponse corregir(
      @PathVariable UUID id, @Valid @RequestBody UpdateCourseCategoryRequest peticion) {
    return correccion.update(id, peticion);
  }

  @PostMapping("/{id}/deletion")
  @PreAuthorize("hasAuthority('course-categories:delete')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Retirar una categoría",
      description =
          """
          Baja **lógica y con motivo**: la fila se queda, con `deletedAt` puesto y nada
          más cambiado, y el motivo y la instantánea —con los identificadores de los
          cursos que tenía— van a la auditoría de eliminación. **`POST` y no `DELETE`**
          porque el cuerpo lleva el motivo, que es obligatorio, y un `DELETE` con
          cuerpo no tiene garantías.

          **No arrastra nada y nunca se rechaza por tener cursos**: la categoría es un
          filtro del catálogo. Sus cursos siguen vivos y se siguen ofreciendo —un curso
          sin categoría se ofrece igual—, y sus clasificaciones se conservan y dejan de
          enseñarse. La portada se queda: el detalle de una retirada la sigue
          devolviendo.

          El nombre de la retirada **queda libre** para un alta. Sale del listado salvo
          `includeDeleted=true`, y su detalle trae el motivo. Retirar una ya retirada
          responde `409`, distinto del `404` de la inexistente: quien retira dos veces
          merece saber que la primera funcionó. Exige `course-categories:delete`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Categoría retirada."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido o motivo ausente, vacío o largo (`VAL-001` a `VAL-003`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `course-categories:delete` (`AUTH-002`)"),
    @ApiResponse(responseCode = "404", description = "La categoría no existe (`EX-001`)"),
    @ApiResponse(responseCode = "409", description = "La categoría ya está retirada (`EX-002`)")
  })
  public void retirar(
      @PathVariable UUID id, @RequestBody(required = false) DeleteCourseCategoryRequest peticion) {
    retiro.delete(id, peticion);
  }

  @PutMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('course-categories:update')")
  @Operation(
      summary = "Subir o reemplazar la portada de una categoría",
      description =
          """
          Recibe **un archivo** —`multipart/form-data`, una sola parte llamada
          `file`— y lo convierte en la portada de la categoría, **tal cual**: ni
          recorte, ni redimensión, ni conversión.

          **El tipo lo deciden los bytes, no la cabecera**: `JPEG`, `PNG` o `WebP`,
          reconocidos por su firma; el `Content-Type` de la parte y el nombre del
          archivo se ignoran. Un `GIF`, un `SVG` o un texto se rechazan con
          `VAL-003`. **Hasta 5 MB** (`VAL-004`); sin archivo o vacío, `VAL-002`. Los
          tres nombran `file`, y son **los mismos de la portada de un producto**.

          **Si ya tenía portada, la reemplaza**: la nueva estrena identificador, **la
          anterior se borra** y `coverImageUrl` cambia de dirección —
          `/api/v1/academy-images/{imageId}`, pública y con caché inmutable—. **Sin
          condición**: la categoría se pinta con su color y su icono cuando no tiene
          portada, y subirla nunca la deja peor. Una categoría retirada responde
          `404`. Exige `course-categories:update`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La categoría, con `coverImageUrl` señalando la imagen nueva.",
        content = @Content(schema = @Schema(implementation = CourseCategoryDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido (`VAL-001`), sin archivo o vacío (`VAL-002`), ni JPEG ni PNG"
                + " ni WebP por sus bytes (`VAL-003`), más de 5 MB (`VAL-004`) o petición que no"
                + " es `multipart/form-data` (`EX-002`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `course-categories:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "La categoría no existe o está retirada (`EX-001`)")
  })
  public CourseCategoryDetailResponse subirPortada(
      @PathVariable UUID id, @RequestPart(value = "file", required = false) MultipartFile file) {
    return portada.upload(id, CoverPart.bytesDe(file));
  }
}
