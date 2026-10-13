package com.factech.nexus.modules.academy.interfaces;

import com.factech.nexus.modules.academy.application.TaughtCourseItem;
import com.factech.nexus.modules.academy.domain.service.ListTaughtCoursesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los cursos que dicta quien pregunta (`RF-AC-057`), aparte de {@link CourseController}: aquella es
 * la administración del catálogo, y esta, la vista del instructor.
 */
@RestController
@RequestMapping("/api/v1/courses")
@Tag(name = "Cursos")
public class TaughtCourseController {

  private final ListTaughtCoursesService mios;

  public TaughtCourseController(ListTaughtCoursesService mios) {
    this.mios = mios;
  }

  @GetMapping("/mine")
  @PreAuthorize("hasAuthority('courses:list-own')")
  @Operation(
      summary = "Consultar los cursos que dicto",
      description =
          """
          **Los cursos no retirados cuyo instructor es quien pregunta**, activos e inactivos,
          con `id`, `title` y `status`, por orden de presentación y título, sin paginar: los
          mismos en los que puede programar sus clases en vivo
          (`POST /api/v1/live-sessions/mine`). Sin cursos, una lista vacía. **No es el
          catálogo**: ni categorías, ni portada, ni los de otros.

          Exige `courses:list-own`; `courses:teach` no habilita.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Los cursos que dicta."),
    @ApiResponse(responseCode = "401", description = "Sin sesión (`AUTH-001`)"),
    @ApiResponse(responseCode = "403", description = "Sin el permiso (`AUTH-002`)")
  })
  public List<TaughtCourseItem> listarMios() {
    return mios.listMine();
  }
}
