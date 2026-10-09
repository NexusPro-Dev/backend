package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.ListStudentProgressRequest;
import com.factech.nexus.modules.academy.application.StudentCourseProgressResponse.ProgressStudentRef;
import com.factech.nexus.modules.academy.application.StudentProgressItem;
import com.factech.nexus.modules.academy.domain.repository.LessonProgressRepository;
import com.factech.nexus.modules.academy.domain.repository.LessonProgressRepository.StudentCourseRow;
import com.factech.nexus.modules.academy.domain.repository.LessonProgressRepository.StudentProgressFilter;
import com.factech.nexus.modules.academy.domain.service.ProgressAudience.Audience;
import com.factech.nexus.modules.system.users.application.UserCatalog;
import com.factech.nexus.modules.system.users.application.UserCatalog.UserView;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import com.factech.nexus.shared.security.CurrentActor;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-040`: el avance por alumno y curso, dentro del alcance.
 *
 * <p><b>El alcance entra en el SQL</b>: las personas que {@link ProgressAudience} resuelve, o el
 * actor como instructor del curso de cada fila. <b>Un {@code userId} que no cabe en las personas no
 * se descarta</b>: puede ser alumno de un curso que el actor dicta, y eso solo lo sabe la
 * sentencia. Fuera de todo, página vacía (`RN-AC-024`). <b>Dos sentencias y una llamada a `SP`</b>
 * —la página, el total y las identidades de la página—, fijas.
 */
@Service
public class ListStudentProgressService {

  private final LessonProgressRepository progreso;
  private final ProgressAudience audiencia;
  private final UserCatalog usuarios;
  private final Pagination paginacion;
  private final CurrentActor actor;

  public ListStudentProgressService(
      LessonProgressRepository progreso,
      ProgressAudience audiencia,
      UserCatalog usuarios,
      Pagination paginacion,
      CurrentActor actor) {
    this.progreso = progreso;
    this.audiencia = audiencia;
    this.usuarios = usuarios;
    this.paginacion = paginacion;
    this.actor = actor;
  }

  @Transactional(readOnly = true)
  public PageResponse<StudentProgressItem> list(ListStudentProgressRequest filtros) {
    Pagination.Slice trozo = paginacion.resolver(filtros.page(), filtros.size());
    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));
    Audience alcance = audiencia.of(quien);

    StudentProgressFilter filtro =
        new StudentProgressFilter(
            alcance.everyone(),
            alcance.people(),
            alcance.everyone() ? null : quien,
            filtros.userId(),
            filtros.courseId(),
            filtros.completed());

    long total = progreso.count(filtro);
    List<StudentCourseRow> filas =
        total == 0 ? List.of() : progreso.search(filtro, trozo.offset(), trozo.size());
    Set<UUID> personas =
        filas.stream()
            .map(StudentCourseRow::userId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Map<UUID, UserView> identidades =
        personas.isEmpty()
            ? Map.of()
            : usuarios.findAll(personas).stream()
                .collect(Collectors.toMap(UserView::id, Function.identity()));

    List<StudentProgressItem> pagina =
        filas.stream()
            .map(
                fila -> {
                  UserView persona = identidades.get(fila.userId());
                  ProgressStudentRef alumno =
                      persona == null
                          ? new ProgressStudentRef(fila.userId(), null, null)
                          : ProgressStudentRef.from(persona);
                  return StudentProgressItem.from(fila, alumno);
                })
            .toList();
    return PageResponse.de(pagina, total, trozo.page(), trozo.size());
  }
}
