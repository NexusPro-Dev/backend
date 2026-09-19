package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.LessonResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso RF-AC-036: el detalle de una leccion para administracion, con su contenido, viva o
 * retirada. Es LessonDetailReader bajo una transaccion de solo lectura, y nada mas: la lectura de
 * administracion devuelve todo, incluida la arrastrada por el retiro de su modulo o su curso.
 */
@Service
public class GetLessonService {

  private final LessonDetailReader detalle;

  public GetLessonService(LessonDetailReader detalle) {
    this.detalle = detalle;
  }

  @Transactional(readOnly = true)
  public LessonResponse detail(UUID courseId, UUID moduleId, UUID lessonId) {
    return detalle.leer(courseId, moduleId, lessonId);
  }
}
