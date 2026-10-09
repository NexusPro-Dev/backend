package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.ProgressFigures;
import com.factech.nexus.modules.academy.application.StudentCourseProgressResponse;
import com.factech.nexus.modules.academy.application.StudentCourseProgressResponse.CourseProgressSummary;
import com.factech.nexus.modules.academy.application.StudentCourseProgressResponse.ProgressCourseRef;
import com.factech.nexus.modules.academy.application.StudentCourseProgressResponse.ProgressLessonItem;
import com.factech.nexus.modules.academy.application.StudentCourseProgressResponse.ProgressModuleItem;
import com.factech.nexus.modules.academy.application.StudentCourseProgressResponse.ProgressStudentRef;
import com.factech.nexus.modules.academy.domain.models.LessonProgress;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.CourseRow;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.LessonRow;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ModuleRow;
import com.factech.nexus.modules.academy.domain.repository.LessonProgressRepository;
import com.factech.nexus.modules.academy.domain.repository.LessonProgressRepository.CourseFigures;
import com.factech.nexus.modules.academy.domain.repository.LessonProgressRepository.CourseLessonProgressRow;
import com.factech.nexus.modules.academy.domain.repository.LessonProgressRepository.ProgressRow;
import com.factech.nexus.modules.system.users.application.UserCatalog;
import com.factech.nexus.modules.system.users.application.UserCatalog.UserView;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnauthorizedException;
import com.factech.nexus.shared.security.CurrentActor;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-041`: lo que un alumno lleva de un curso, lección a lección.
 *
 * <p><b>Fuera del alcance es el mismo `404`</b> que un curso o un alumno que no existen
 * (`RN-AC-024`): un `403` diría que el alumno existe. <b>El árbol lo deciden los objetos de
 * ofrecibilidad de siempre</b>, sobre las lecturas del detalle de administración; el progreso se
 * pone encima, y lo que el alumno vio de lo que hoy no se ofrece va a {@code notOffered}.
 */
@Service
public class GetStudentCourseProgressService {

  static final String NO_EXISTE = "No existe progreso de ese alumno en ese curso para consultar.";

  private final CourseQueryRepository cursos;
  private final LessonProgressRepository progreso;
  private final ProgressAudience audiencia;
  private final UserCatalog usuarios;
  private final CurrentActor actor;

  public GetStudentCourseProgressService(
      CourseQueryRepository cursos,
      LessonProgressRepository progreso,
      ProgressAudience audiencia,
      UserCatalog usuarios,
      CurrentActor actor) {
    this.cursos = cursos;
    this.progreso = progreso;
    this.audiencia = audiencia;
    this.usuarios = usuarios;
    this.actor = actor;
  }

  @Transactional(readOnly = true)
  public StudentCourseProgressResponse progress(UUID courseId, UUID userId) {
    UUID quien =
        actor
            .currentActorId()
            .orElseThrow(() -> new UnauthorizedException("AUTH-001", "Se requiere autenticación."));
    CourseRow curso = cursos.findDetail(courseId).orElseThrow(GetStudentCourseProgressService::no);
    if (!audiencia.of(quien).reaches(userId, curso.instructorId())) {
      throw no();
    }
    UserView alumno =
        usuarios
            .find(userId)
            .filter(persona -> !persona.deleted())
            .orElseThrow(GetStudentCourseProgressService::no);

    List<CourseLessonProgressRow> filas = progreso.findOfUserInCourse(userId, courseId);
    Map<UUID, ProgressRow> porLeccion =
        filas.stream()
            .collect(
                Collectors.toMap(f -> f.progress().lessonId(), CourseLessonProgressRow::progress));

    List<ModuleRow> modulos =
        cursos.findModulesOf(courseId).stream()
            .filter(modulo -> modulo.ofrecibilidad().offerable())
            .toList();
    Map<UUID, List<LessonRow>> lecciones =
        modulos.isEmpty()
            ? Map.of()
            : cursos.findLessonsOfModules(modulos.stream().map(ModuleRow::id).toList()).stream()
                .filter(LessonRow::ofrecida)
                .collect(Collectors.groupingBy(LessonRow::moduleId));

    List<ProgressModuleItem> arbol =
        modulos.stream()
            .map(
                modulo ->
                    new ProgressModuleItem(
                        modulo.id(),
                        modulo.title(),
                        modulo.displayOrder(),
                        lecciones.getOrDefault(modulo.id(), List.of()).stream()
                            .map(
                                l ->
                                    ProgressLessonItem.of(
                                        l.id(),
                                        l.moduleId(),
                                        l.title(),
                                        l.type(),
                                        l.durationSeconds(),
                                        l.displayOrder(),
                                        porLeccion.get(l.id())))
                            .toList()))
            .toList();

    List<ProgressLessonItem> fueraDeOferta =
        filas.stream()
            .filter(fila -> !fila.offerable())
            .map(
                fila ->
                    ProgressLessonItem.of(
                        fila.progress().lessonId(),
                        fila.moduleId(),
                        fila.title(),
                        fila.type(),
                        fila.durationSeconds(),
                        fila.displayOrder(),
                        fila.progress()))
            .toList();

    return new StudentCourseProgressResponse(
        ProgressStudentRef.from(alumno),
        ProgressCourseRef.from(curso),
        CourseProgressSummary.from(
            avance(arbol),
            extremo(filas, ProgressRow::firstOpenedAt, Comparator.naturalOrder()),
            extremo(filas, ProgressRow::lastOpenedAt, Comparator.reverseOrder())),
        arbol,
        fueraDeOferta);
  }

  /** `RN-AC-023` sobre las lecciones del árbol: lo mismo que suma el SQL del listado. */
  private static ProgressFigures avance(List<ProgressModuleItem> arbol) {
    long vistos = 0;
    long total = 0;
    long completadas = 0;
    long cuantas = 0;
    for (ProgressModuleItem modulo : arbol) {
      for (ProgressLessonItem leccion : modulo.lessons()) {
        vistos +=
            LessonProgress.credito(
                leccion.watchedSeconds(), leccion.durationSeconds(), leccion.completed());
        total += leccion.durationSeconds();
        completadas += leccion.completed() ? 1 : 0;
        cuantas++;
      }
    }
    return ProgressFigures.from(new CourseFigures(cuantas, completadas, vistos, total));
  }

  private static OffsetDateTime extremo(
      List<CourseLessonProgressRow> filas,
      Function<ProgressRow, OffsetDateTime> fecha,
      Comparator<OffsetDateTime> orden) {
    return filas.stream()
        .map(fila -> fecha.apply(fila.progress()))
        .filter(Objects::nonNull)
        .min(orden)
        .orElse(null);
  }

  private static ResourceNotFoundException no() {
    return new ResourceNotFoundException("EX-001", NO_EXISTE);
  }
}
