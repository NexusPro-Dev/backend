package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.ClassroomLessonResponse;
import com.factech.nexus.modules.academy.domain.models.LessonType;
import com.factech.nexus.modules.academy.domain.repository.LessonProgressRepository;
import com.factech.nexus.modules.academy.domain.repository.LessonProgressRepository.ProgressRow;
import com.factech.nexus.modules.academy.domain.repository.LessonQueryRepository.ClassroomLessonRow;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-035`: el contenido de una lección, el único sitio donde sale hacia un alumno.
 *
 * <p>Las puertas son de {@link ClassroomLessonGate}. <b>Desde el 09-10-2026 entregar deja
 * rastro</b> (`RN-AC-022`): la primera y la última apertura, y una lección {@code TEXTO} completada
 * en el acto. Va en la misma transacción que la lectura —si no se puede anotar, no se entrega— y
 * <b>solo después de pasar las puertas</b>: un `403` o un `404` no dejan nada. No se audita
 * (`requirements/ac.md` §5.2.14).
 */
@Service
public class GetClassroomLessonService {

  private final ClassroomLessonGate puertas;
  private final LessonProgressRepository progreso;
  private final StudentKeys llaves;

  public GetClassroomLessonService(
      ClassroomLessonGate puertas, LessonProgressRepository progreso, StudentKeys llaves) {
    this.puertas = puertas;
    this.progreso = progreso;
    this.llaves = llaves;
  }

  @Transactional
  public ClassroomLessonResponse lesson(UUID courseId, UUID lessonId) {
    ClassroomLessonRow fila = puertas.pass(courseId, lessonId);
    boolean esTexto = LessonType.TEXTO.name().equals(fila.lesson().type());
    ProgressRow rastro = progreso.open(llaves.actorId(), lessonId, esTexto);
    return ClassroomLessonResponse.from(fila.lesson(), rastro);
  }
}
