package com.factech.nexus.modules.academy.interfaces;

import com.factech.nexus.modules.academy.application.ClassroomCatalogRequest;
import com.factech.nexus.modules.academy.application.ClassroomCatalogResponse;
import com.factech.nexus.modules.academy.application.ClassroomCourseResponse;
import com.factech.nexus.modules.academy.application.ClassroomLessonResponse;
import com.factech.nexus.modules.academy.domain.service.GetClassroomCatalogService;
import com.factech.nexus.modules.academy.domain.service.GetClassroomCourseService;
import com.factech.nexus.modules.academy.domain.service.GetClassroomLessonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El aula (`AC`, `RF-AC-033` a `RF-AC-035`): lo que el alumno ve y estudia, bajo {@code
 * /courses/available}.
 *
 * <p><b>Controlador propio</b> y no un método más de {@link CourseController}: otro actor, otros
 * permisos, otra forma. {@code /available} es un segmento literal y Spring lo prefiere a {@code
 * /{id}} por especificidad, sin depender de en qué clase esté (`CA-AC-193`). <b>El actor sale del
 * token</b> en las tres: no hay por dónde preguntar por otra persona. <b>Un permiso por vista</b>
 * desde el 26-09-2026 (`requirements/ac.md` §5.2.13).
 */
@RestController
@RequestMapping("/api/v1/courses/available")
@Tag(
    name = "Aula",
    description = "Lo que el alumno ve y estudia: el catálogo, el curso y el contenido.")
public class ClassroomController {

  private final GetClassroomCatalogService catalogo;
  private final GetClassroomCourseService curso;
  private final GetClassroomLessonService leccion;

  public ClassroomController(
      GetClassroomCatalogService catalogo,
      GetClassroomCourseService curso,
      GetClassroomLessonService leccion) {
    this.catalogo = catalogo;
    this.curso = curso;
    this.leccion = leccion;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('courses:learn')")
  @Operation(
      summary = "Consultar el catálogo de cursos como alumno",
      description =
          """
          **Los cursos que se ofrecen**, en su orden (`displayOrder` y después
          identificador), **sin paginar**, para quien pregunta —el actor sale del token—.
          Un curso se ofrece si está vivo, `ACTIVO`, con sus dos descripciones y al
          menos un módulo activo con una lección activa con contenido; **lo demás no
          aparece**.

          **Por omisión es la vitrina**: todo lo ofrecido, abierto o no. Cada curso
          dice **`accessible`** —se le abre entero a quien pregunta porque **no declara
          ninguna membresía ni servicio** (es de todos), porque **su membresía vigente
          está en la lista** del curso o porque **tiene vigente uno de sus servicios**;
          pertenencia y no nivel: `PLATINO` no abre un curso de `ORO`— y
          **`openLessonCount`**, cuántas lecciones abiertas (demostraciones) tiene.
          **`onlyAccessible=true` deja solo los cursos que abren algo**: `accessible` o
          con `openLessonCount` mayor que cero — «los cursos que puedo ver».

          `totalDurationSeconds`, `lessonCount` y `openLessonCount` cuentan **solo lo
          que el alumno verá**: las lecciones ofrecibles de los módulos ofrecibles.
          `categoryId` acota a una categoría **viva** (una retirada o inexistente da la
          lista vacía, no `404`) y `difficulty` a una dificultad. **`categories` trae
          todas las categorías vivas** —los cajones, incluidas las vacías— **sin aplicar
          ningún filtro**. `currentMembership` es la vigente de quien pregunta, **presente
          y nula** si no tiene.

          Exige `courses:learn`; `courses:read` **no habilita** (esa es la lista de
          administración, en `GET /courses`).
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El catálogo.",
        content = @Content(schema = @Schema(implementation = ClassroomCatalogResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Identificador de categoría mal formado u `onlyAccessible` no booleano (`VAL-001`),"
                + " o dificultad fuera del dominio (`VAL-002`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:learn` (`AUTH-002`)")
  })
  public ClassroomCatalogResponse catalogo(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute
          ClassroomCatalogRequest filtros) {
    return catalogo.catalog(filtros);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('courses:read-available')")
  @Operation(
      summary = "Consultar el detalle de un curso como alumno",
      description =
          """
          **El curso entero antes de entrar**, si se ofrece: lo suyo, el instructor, las
          categorías vivas, los cursos recomendados **que se ofrecen**, **las membresías
          (`memberships`) y los servicios (`products`) que lo abren** —la invitación; las
          dos vacías si el curso es de todos— y **el árbol ofrecido**: los módulos
          ofrecibles en su orden y, dentro, sus lecciones ofrecibles en su orden. **Sin
          estados, sin `offerable` y sin el contenido** de las lecciones, que se pide una
          a una.

          `accessible` del curso es el del catálogo; **el de cada lección es el del curso
          o `open`**: una demostración se abre a cualquiera. `durationSeconds` del módulo
          y `totalDurationSeconds`, `lessonCount` y `openLessonCount` del curso cuentan
          solo lo que aparece. **Un curso que no existe, está retirado, inactivo o le
          falta algo responde `404` con el mismo mensaje**: para el alumno no existe.

          Exige `courses:read-available`; ni `courses:read` ni `courses:learn` habilitan.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El curso ofrecido, con su árbol ofrecido.",
        content = @Content(schema = @Schema(implementation = ClassroomCourseResponse.class))),
    @ApiResponse(responseCode = "400", description = "Identificador inválido (`VAL-001`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:read-available` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description = "El curso no existe o no se ofrece, con el mismo mensaje (`EX-001`)")
  })
  public ClassroomCourseResponse curso(@PathVariable UUID id) {
    return curso.course(id);
  }

  @GetMapping("/{courseId}/lessons/{lessonId}")
  @PreAuthorize("hasAuthority('lessons:learn')")
  @Operation(
      summary = "Consultar el contenido de una lección",
      description =
          """
          **La lección con su contenido** —la URL de un `VIDEO`, el Markdown de un
          `TEXTO`— **tal como se guardó**: sin convertir ni sanear; pintarlo sin ejecutar
          lo que traiga es cosa del frontend.

          **Primero si se ofrece, después si se abre.** La lección tiene que ser de un
          módulo de ese curso, y los tres —curso, módulo y lección— tienen que ofrecerse;
          si no, **`404` con el mismo mensaje**, también para una lección abierta. Sobre lo
          ofrecido, se abre si **la lección está abierta**, si **el curso no declara
          llaves**, si **la membresía vigente** de quien pregunta está en su lista o si
          **tiene vigente uno de sus servicios**. Si no, **`403` con `EX-002`** en
          `errors` y dos miembros de extensión, **`memberships`** `[{id, code, name,
          color}]` y **`products`** `[{id, code, name}]`: lo que abre el curso. El `403`
          de permiso (`AUTH-002`) sale sin ellos y con `errors` vacío.

          Exige `lessons:learn`. No registra nada: no hay progreso.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "La lección con su contenido.",
        content = @Content(schema = @Schema(implementation = ClassroomLessonResponse.class))),
    @ApiResponse(responseCode = "400", description = "Identificador inválido (`VAL-001`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description =
            "Sin el permiso `lessons:learn` (`AUTH-002`, sin extensiones); o ninguna llave abre"
                + " el curso (`EX-002`, con `memberships` y `products`)"),
    @ApiResponse(
        responseCode = "404",
        description = "La lección no existe en ese curso o no se ofrece (`EX-001`)")
  })
  public ClassroomLessonResponse leccion(@PathVariable UUID courseId, @PathVariable UUID lessonId) {
    return leccion.lesson(courseId, lessonId);
  }
}
