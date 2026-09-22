package com.factech.nexus.modules.academy.interfaces;

import com.factech.nexus.modules.academy.application.ChangeCourseStatusRequest;
import com.factech.nexus.modules.academy.application.CourseDetailResponse;
import com.factech.nexus.modules.academy.application.CoursePageResponse;
import com.factech.nexus.modules.academy.application.DeleteCourseRequest;
import com.factech.nexus.modules.academy.application.ListCoursesRequest;
import com.factech.nexus.modules.academy.application.RegisterCourseRequest;
import com.factech.nexus.modules.academy.application.UpdateCourseRequest;
import com.factech.nexus.modules.academy.domain.service.ChangeCourseStatusService;
import com.factech.nexus.modules.academy.domain.service.DeleteCourseService;
import com.factech.nexus.modules.academy.domain.service.GetCourseService;
import com.factech.nexus.modules.academy.domain.service.ListCoursesService;
import com.factech.nexus.modules.academy.domain.service.RegisterCourseService;
import com.factech.nexus.modules.academy.domain.service.UpdateCourseService;
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
 * Los cursos (`AC`, `RF-AC-008` a `RF-AC-013`): las seis operaciones de administración del curso en
 * sí. Sus relaciones, sus módulos y lecciones y su portada llegan con los bloques 3 a 5 de `ac.md`
 * §6.1, y el aula con el 6, bajo {@code /courses/available}.
 *
 * <p><b>El alta, el detalle y las escrituras devuelven la misma forma</b> —el curso con sus
 * relaciones, su árbol y por qué no se ofrece— para que el frontend tenga una sola pantalla de
 * curso. <b>Los {@code course-categories:} no habilitan aquí ni una operación.</b>
 */
@RestController
@RequestMapping("/api/v1/courses")
@Tag(name = "Cursos", description = "Lo que la membresía abre: los cursos de la academia.")
public class CourseController {

  private final RegisterCourseService alta;
  private final ListCoursesService listado;
  private final GetCourseService detalle;
  private final UpdateCourseService correccion;
  private final ChangeCourseStatusService estado;
  private final DeleteCourseService retiro;

  public CourseController(
      RegisterCourseService alta,
      ListCoursesService listado,
      GetCourseService detalle,
      UpdateCourseService correccion,
      ChangeCourseStatusService estado,
      DeleteCourseService retiro) {
    this.alta = alta;
    this.listado = listado;
    this.detalle = detalle;
    this.correccion = correccion;
    this.estado = estado;
    this.retiro = retiro;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('courses:create')")
  @Operation(
      summary = "Registrar un curso",
      description =
          """
          Registra un curso con **título, instructor, dificultad y orden**, obligatorios,
          y descripción corta, descripción larga y video de introducción, opcionales.
          **Nace `INACTIVO` y vacío**: sin categorías, sin recomendaciones, sin
          membresías, sin módulos y sin portada — cada cosa entra por su operación—, y
          **sin código**: un curso no se teclea en ninguna venta.

          **El instructor es una persona del sistema que existe, no está retirada y
          porta `courses:teach`**, comprobado **al asignar y solo al asignar**: que lo
          pierda después no toca el curso. Un instructor inexistente o retirado y uno
          sin el permiso son dos `422` distintos, porque se arreglan en sitios
          distintos. **`instructor.fullName` es el nombre actual** de la persona, no
          una copia.

          El título es único entre los cursos **vivos**, sin distinguir mayúsculas ni
          acentos; un retirado libera el suyo. Las descripciones de solo espacios se
          guardan nulas; el video se comprueba en su forma y **no se sigue**. Enviar
          `status`, `categories`, `memberships`, `modules`, `coverImageUrl` o `code`
          responde `400`.

          La respuesta es la forma del detalle: listas vacías, `coverImageUrl`
          presente y nulo, cero minutos y cero lecciones, y `offerable: false` con
          `offerableReason` «inactivo». Exige `courses:create`; **los
          `course-categories:` no habilitan**.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Curso registrado, inactivo y vacío.",
        content = @Content(schema = @Schema(implementation = CourseDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Datos inválidos, juntos (`VAL-001` a `VAL-006`), o un campo no admitido (`VAL-007`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:create` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "409",
        description = "Título ya en uso por un curso vivo (`EX-001`)"),
    @ApiResponse(
        responseCode = "422",
        description =
            "El instructor no existe o está retirado (`EX-002`), o no porta `courses:teach`"
                + " (`EX-003`)")
  })
  public ResponseEntity<CourseDetailResponse> register(
      @Valid @RequestBody RegisterCourseRequest peticion) {
    CourseDetailResponse creado = alta.register(peticion);
    return ResponseEntity.created(URI.create("/api/v1/courses/" + creado.id())).body(creado);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('courses:read')")
  @Operation(
      summary = "Consultar los cursos",
      description =
          """
          La lista de administración de los cursos, paginada, **en el orden en que se
          enseñan**: por `displayOrder` ascendente, con el más antiguo primero entre
          dos que compartan número. Cada fila trae el instructor resuelto —con su
          nombre **actual**—, `coverImageUrl`, sus categorías vivas, **`offerable`** y
          cuántos módulos y lecciones **vivos** tiene — vivos y no ofrecidos—. Sin
          descripción larga, video ni árbol: para eso está el detalle.

          Filtra por `q` —título, sin distinguir mayúsculas ni acentos—, `categoryId`,
          `instructorId`, `difficulty` y `status`, combinables. **Excluye los
          retirados** salvo `includeDeleted=true`, y entonces los trae con `deletedAt`
          y `offerable: false`. «Ofrecible» no es filtro: se publica por fila.

          Orden: `displayOrder` (omisión, ascendente), `title` y `createdAt`
          (descendente por omisión), con `,asc`/`,desc`. Los parámetros inválidos se
          devuelven **juntos** con `400`. Exige `courses:read`;
          `course-categories:read` no habilita.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La página de cursos.",
        content = @Content(schema = @Schema(implementation = CoursePageResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Orden, paginación o filtros inválidos, juntos (`VAL-001` a `VAL-003`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:read` (`AUTH-002`)")
  })
  public CoursePageResponse listar(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute ListCoursesRequest filtros) {
    return listado.list(filtros);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('courses:read')")
  @Operation(
      summary = "Consultar el detalle de un curso",
      description =
          """
          El curso entero: lo suyo, el instructor resuelto, `coverImageUrl`, sus
          **categorías** vivas, los **cursos que recomienda** —con su estado y si se
          ofrecen—, las **membresías que lo abren**, y **sus módulos en orden con sus
          lecciones en orden**, vivos y retirados marcados, cada módulo con `offerable`
          y cada lección con tipo, duración, estado y `open` — **sin el contenido**, que
          se pide por lección. La duración total y la cuenta de lecciones se suman sobre
          lo vivo.

          **`offerable` y `offerableReason` viajan siempre**, con el primer motivo que
          falla en su orden: retirado, inactivo, sin descripción, sin membresías, sin
          módulo activo con lección activa con contenido. Es la vista con la que se
          arma el curso y se ve qué le falta para publicarse.

          **Se devuelve también un retirado**, con `deletedAt` y `deletionReason`
          —leído de la auditoría— y su árbol marcado: es la lectura de administración
          y devuelve todo.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El curso con sus relaciones y su árbol.",
        content = @Content(schema = @Schema(implementation = CourseDetailResponse.class))),
    @ApiResponse(responseCode = "400", description = "Identificador inválido (`VAL-001`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:read` (`AUTH-002`)"),
    @ApiResponse(responseCode = "404", description = "El curso no existe (`EX-001`)")
  })
  public CourseDetailResponse detalle(@PathVariable UUID id) {
    return detalle.detail(id);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('courses:update')")
  @Operation(
      summary = "Editar un curso",
      description =
          """
          Corrección **parcial**: solo lo que viene cambia. Admite `title`,
          `instructorId`, `difficulty`, `shortDescription`, `longDescription`,
          `introVideoUrl` y `displayOrder`, por separado o juntos, y exige al menos
          uno. **Ausente y nulo no significan lo mismo**: ausente es «no lo toques»,
          nulo es «vacíalo» — y **solo las descripciones y el video admiten
          vaciarse**; el nulo en los otros cuatro responde `400`, junto con los demás
          errores de forma.

          **Sin inmutables**. **Reasignar el instructor repite la comprobación del
          alta** sobre el nuevo: existe, no está retirado y porta `courses:teach`; al
          mismo no se comprueba nada y nada cambia. **Vaciar una descripción no
          desactiva**: un curso activo sigue activo y **deja de ofrecerse** —el detalle
          dice «sin descripción»— hasta que alguien la reponga. El estado, la portada,
          las relaciones y los módulos tienen sus propios endpoints, y en el cuerpo
          responden `400`.

          El título nuevo no puede ser el de **otro** curso vivo. Un retirado no se
          corrige (`404`). Un cuerpo sin cambios de valor responde `200` sin avanzar
          `updatedAt` ni auditar. Exige `courses:update`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El curso corregido, en la forma del detalle.",
        content = @Content(schema = @Schema(implementation = CourseDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido, campos que no admiten vaciarse, cuerpo vacío o campo no"
                + " admitido (`VAL-001` a `VAL-008`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El curso no existe o está retirado (`EX-002`)"),
    @ApiResponse(
        responseCode = "409",
        description = "Título ya en uso por otro curso vivo (`EX-001`)"),
    @ApiResponse(
        responseCode = "422",
        description =
            "El instructor nuevo no existe o está retirado (`EX-003`), o no porta"
                + " `courses:teach` (`EX-004`)")
  })
  public CourseDetailResponse corregir(
      @PathVariable UUID id, @Valid @RequestBody UpdateCourseRequest peticion) {
    return correccion.update(id, peticion);
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAuthority('courses:update')")
  @Operation(
      summary = "Cambiar el estado de un curso",
      description =
          """
          `ACTIVO` publica; `INACTIVO` despublica. **Activar exige las dos
          descripciones y al menos un módulo activo**, comprobado **junto**: si faltan
          varias cosas, la respuesta trae todos los motivos. **No exige una
          membresía**: un curso activo sin lista existe y no se ofrece, y el detalle
          lo dice. **Desactivar no exige nada** y no toca módulos ni relaciones.

          El estado es lo que alguien decidió: **vaciar una descripción o retirar el
          último módulo activo después no lo cambia** — el curso deja de ofrecerse, y
          `offerableReason` dice por qué. Pedir el estado que ya tiene responde `200`
          sin escribir ni auditar. Un retirado responde `404`. Exige `courses:update`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El curso con su estado, en la forma del detalle.",
        content = @Content(schema = @Schema(implementation = CourseDetailResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido o estado ausente o fuera de dominio (`VAL-001`, `VAL-002`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:update` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El curso no existe o está retirado (`EX-001`)"),
    @ApiResponse(
        responseCode = "409",
        description =
            "No puede activarse: sin descripción corta (`EX-002`), sin descripción larga"
                + " (`EX-003`) o sin módulo activo (`EX-004`), juntos")
  })
  public CourseDetailResponse cambiarEstado(
      @PathVariable UUID id, @Valid @RequestBody ChangeCourseStatusRequest peticion) {
    return estado.change(id, peticion);
  }

  @PostMapping("/{id}/deletion")
  @PreAuthorize("hasAuthority('courses:delete')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Retirar un curso",
      description =
          """
          Baja **lógica y con motivo**: la fila se queda con `deletedAt` puesto y nada
          más cambiado —ni el estado—, y el motivo y la instantánea —con los
          identificadores de sus categorías, membresías, recomendados y módulos— van a
          la auditoría de eliminación. **`POST` y no `DELETE`** porque el cuerpo lleva
          el motivo, que es obligatorio.

          **Retirar un curso arrastra sus módulos y lecciones vivos**, con el mismo
          instante y el mismo motivo, y **una fila de auditoría por cada uno**. Sus
          clasificaciones, recomendaciones y visibilidades **se conservan** y dejan de
          verse; **deja de aparecer como recomendado** en el aula. Un curso que se
          ofrece se retira igual y sale del aula en el acto.

          El título del retirado **queda libre**. Sale del listado salvo
          `includeDeleted=true`, y su detalle trae el motivo y el árbol marcado.
          Retirar uno ya retirado responde `409`, distinto del `404` del inexistente.
          Exige `courses:delete`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Curso retirado, con su árbol."),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador inválido o motivo ausente, vacío o largo (`VAL-001` a `VAL-003`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:delete` (`AUTH-002`)"),
    @ApiResponse(responseCode = "404", description = "El curso no existe (`EX-001`)"),
    @ApiResponse(responseCode = "409", description = "El curso ya está retirado (`EX-002`)")
  })
  public void retirar(
      @PathVariable UUID id, @RequestBody(required = false) DeleteCourseRequest peticion) {
    retiro.delete(id, peticion);
  }
}
