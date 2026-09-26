package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.ClassroomLessonResponse;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.MembershipRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ProductRef;
import com.factech.nexus.modules.academy.domain.repository.LessonQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.LessonQueryRepository.ClassroomLessonRow;
import com.factech.nexus.shared.error.NotEntitledException;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-035`: el contenido de una lección, el único sitio donde sale hacia un alumno.
 *
 * <p><b>Primero si se ofrece, después si se abre</b> (`CA-AC-206`): cualquier «no» de los tres
 * niveles es el mismo `404`, y solo sobre lo ofrecido se mira el acceso. <b>La abierta y la del
 * curso sin llaves no preguntan a `SP`</b>: no necesitan saber quién mira (`spec.md` §14.3). El
 * «no» es un `403` con las dos listas del curso como invitación, <b>sin auditar</b>.
 */
@Service
public class GetClassroomLessonService {

  static final String NO_SE_ABRE = "Ni tu membresía ni tus servicios abren este curso.";

  private final LessonQueryRepository lecciones;
  private final CourseQueryRepository cursos;
  private final StudentKeys llaves;

  public GetClassroomLessonService(
      LessonQueryRepository lecciones, CourseQueryRepository cursos, StudentKeys llaves) {
    this.lecciones = lecciones;
    this.cursos = cursos;
    this.llaves = llaves;
  }

  @Transactional(readOnly = true)
  public ClassroomLessonResponse lesson(UUID courseId, UUID lessonId) {
    ClassroomLessonRow fila =
        lecciones
            .findClassroomLesson(courseId, lessonId)
            .filter(ClassroomLessonRow::ofrecida)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe una lección con ese identificador en ese curso."));

    if (fila.lesson().open() || fila.cursoSinLlaves()) {
      return ClassroomLessonResponse.from(fila.lesson());
    }

    List<MembershipRef> membresias = cursos.findMembershipsOf(courseId);
    List<ProductRef> servicios = cursos.findProductsOf(courseId);
    boolean abre =
        llaves
            .ofCurrentActor()
            .opens(
                membresias.stream().map(MembershipRef::id).toList(),
                servicios.stream().map(ProductRef::id).toList());
    if (!abre) {
      throw new NotEntitledException(
          "EX-002",
          NO_SE_ABRE,
          Map.of(
              "memberships",
              membresias.stream()
                  .map(
                      m ->
                          Map.of(
                              "id", m.id(), "code", m.code(), "name", m.name(), "color", m.color()))
                  .toList(),
              "products",
              servicios.stream()
                  .map(s -> Map.of("id", s.id(), "code", s.code(), "name", s.name()))
                  .toList()));
    }
    return ClassroomLessonResponse.from(fila.lesson());
  }
}
