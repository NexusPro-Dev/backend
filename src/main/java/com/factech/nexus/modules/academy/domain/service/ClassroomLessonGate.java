package com.factech.nexus.modules.academy.domain.service;

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
import org.springframework.stereotype.Component;

/**
 * Las puertas de una lección del aula (`RF-AC-035`, `RF-AC-039`): <b>primero si se ofrece, después
 * si se abre</b> (`CA-AC-206`). Cualquier «no» de los tres niveles es el mismo `404`, y solo sobre
 * lo ofrecido se mira el acceso; el «no» de las llaves es un `403` con las dos listas del curso
 * como invitación, <b>sin auditar</b>.
 *
 * <p>Sale de {@code GetClassroomLessonService} el 09-10-2026 para que leer el contenido y reportar
 * el avance <b>no tengan dos definiciones de «se le abre»</b> (`RF-AC-039` · plan §1). <b>La
 * abierta y la del curso sin llaves no preguntan a `SP`</b>: no necesitan saber quién mira.
 */
@Component
public class ClassroomLessonGate {

  static final String NO_EXISTE = "No existe una lección con ese identificador en ese curso.";
  static final String NO_SE_ABRE = "Ni tu membresía ni tus servicios abren este curso.";

  private final LessonQueryRepository lecciones;
  private final CourseQueryRepository cursos;
  private final StudentKeys llaves;

  public ClassroomLessonGate(
      LessonQueryRepository lecciones, CourseQueryRepository cursos, StudentKeys llaves) {
    this.lecciones = lecciones;
    this.cursos = cursos;
    this.llaves = llaves;
  }

  /** La lección, si se ofrece en ese curso y se le abre a quien mira; si no, `404` o `403`. */
  public ClassroomLessonRow pass(UUID courseId, UUID lessonId) {
    ClassroomLessonRow fila =
        lecciones
            .findClassroomLesson(courseId, lessonId)
            .filter(ClassroomLessonRow::ofrecida)
            .orElseThrow(() -> new ResourceNotFoundException("EX-001", NO_EXISTE));

    if (fila.lesson().open() || fila.cursoSinLlaves()) {
      return fila;
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
    return fila;
  }
}
