package com.factech.nexus.modules.academy.interfaces;

import com.factech.nexus.modules.academy.application.ListStudentProgressRequest;
import com.factech.nexus.modules.academy.application.StudentCourseProgressResponse;
import com.factech.nexus.modules.academy.application.StudentProgressItem;
import com.factech.nexus.modules.academy.domain.service.GetStudentCourseProgressService;
import com.factech.nexus.modules.academy.domain.service.ListStudentProgressService;
import com.factech.nexus.shared.pagination.PageResponse;
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
 * El progreso de los alumnos (`AC`, `RF-AC-040` y `RF-AC-041`), para quien lo sigue:
 * administración, el instructor y la red comercial.
 *
 * <p><b>Controlador propio</b>: otro actor y otro alcance que la administración del curso y que el
 * aula. {@code /courses/progress} es un segmento literal y Spring lo prefiere a {@code
 * /courses/{id}} por especificidad, como {@code /available}. <b>El alcance va dentro</b>
 * (`RN-AC-024`): el permiso habilita, y quien porta el permiso ve lo que le toca.
 */
@RestController
@RequestMapping("/api/v1/courses")
@Tag(
    name = "Progreso de los alumnos",
    description =
        "Cuánto ha visto cada alumno de cada curso, dentro del alcance de quien consulta.")
public class CourseProgressController {

  private final ListStudentProgressService listado;
  private final GetStudentCourseProgressService detalle;

  public CourseProgressController(
      ListStudentProgressService listado, GetStudentCourseProgressService detalle) {
    this.listado = listado;
    this.detalle = detalle;
  }

  @GetMapping("/progress")
  @PreAuthorize("hasAuthority('courses:list-progress')")
  @Operation(
      summary = "Consultar el progreso de los alumnos",
      description =
          """
          **Una fila por alumno y curso** en el que el alumno abrió al menos una lección,
          por **última actividad descendente**, paginada. Cada fila con el alumno, el curso
          —vivo o retirado— y su avance: `percent` (lo visto, acotado a cada duración y
          entero si la lección está completada, entre la duración total, hacia abajo),
          `completedLessons` de `lessonCount`, `watchedSeconds` de `totalSeconds`,
          `completed` (todas las lecciones completadas y al menos una), la primera apertura
          y la última actividad. **El avance se calcula sobre las lecciones que el curso
          ofrece hoy**: lo visto de una lección retirada no cuenta, y no se pierde.

          **Cada uno ve lo que le toca**: un rol `FUNCIONARIO`, todo; un `VENDEDOR`, a su
          red en profundidad —con él dentro— y a los clientes cuyo vendedor principal es de
          la red; y **cualquiera, a los alumnos de los cursos que dicta**. Las condiciones
          se suman. **Fuera del alcance, página vacía**, nunca `403`.

          Filtros: `userId`, `courseId` (uno inexistente da página vacía) y `completed`.

          Exige `courses:list-progress`.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "La página."),
    @ApiResponse(
        responseCode = "400",
        description = "Paginación fuera de rango o un filtro mal formado (`VAL-001`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:list-progress` (`AUTH-002`)")
  })
  public PageResponse<StudentProgressItem> listado(
      @org.springdoc.core.annotations.ParameterObject @ModelAttribute
          ListStudentProgressRequest filtros) {
    return listado.list(filtros);
  }

  @GetMapping("/{courseId}/progress/{userId}")
  @PreAuthorize("hasAuthority('courses:read-progress')")
  @Operation(
      summary = "Consultar el progreso de un alumno en un curso",
      description =
          """
          **Lección a lección**, lo que el alumno lleva del curso: el **árbol que el curso
          ofrece hoy** —módulos y lecciones en su orden— con, por lección, `watchedSeconds`
          (la mayor posición reportada de un video), `percent`, `completed` y
          `completedAt`, y la primera y la última apertura; **las no abiertas, en cero y con
          fechas nulas**. Lo que vio de lecciones que hoy no se ofrecen va aparte, en
          **`notOffered`**, y no cuenta en `progress`. Un curso retirado se lee igual.

          El alcance es el del listado. **Fuera del alcance, curso inexistente y alumno
          inexistente responden el mismo `404`**: un `403` diría que el alumno existe. Un
          alumno alcanzado que no ha empezado responde `200` en cero.

          Exige `courses:read-progress`.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "El progreso del alumno en el curso.",
        content = @Content(schema = @Schema(implementation = StudentCourseProgressResponse.class))),
    @ApiResponse(responseCode = "400", description = "Identificador inválido (`VAL-001`)"),
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido (`AUTH-001`)"),
    @ApiResponse(
        responseCode = "403",
        description = "Autenticado sin el permiso `courses:read-progress` (`AUTH-002`)"),
    @ApiResponse(
        responseCode = "404",
        description =
            "El curso o el alumno no existen, o el alumno está fuera del alcance (`EX-001`)")
  })
  public StudentCourseProgressResponse detalle(
      @PathVariable UUID courseId, @PathVariable UUID userId) {
    return detalle.progress(courseId, userId);
  }
}
