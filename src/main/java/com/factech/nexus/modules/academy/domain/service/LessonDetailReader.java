package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.LessonResponse;
import com.factech.nexus.modules.academy.domain.repository.LessonQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.LessonQueryRepository.LessonDetailRow;
import com.factech.nexus.shared.audit.DeletionReasonReader;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Arma la respuesta de una lección, <b>con su contenido</b>, para las operaciones que la devuelven
 * (`RF-AC-028` a `RF-AC-030`) y para el detalle de administración (`RF-AC-036`), que la lee en
 * cualquier estado y añade el motivo de retiro cuando lo hay. <b>Una sentencia</b>, y una más con
 * motivo.
 */
@Component
public class LessonDetailReader {

  static final String ENTIDAD = "lessons";

  private final LessonQueryRepository consultas;
  private final DeletionReasonReader motivos;

  public LessonDetailReader(LessonQueryRepository consultas, DeletionReasonReader motivos) {
    this.consultas = consultas;
    this.motivos = motivos;
  }

  public LessonResponse leer(UUID courseId, UUID moduleId, UUID lessonId) {
    LessonDetailRow fila =
        consultas
            .findDetail(courseId, moduleId, lessonId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe una lección con ese identificador en ese módulo."));
    String motivo =
        fila.retirada()
            ? motivos.reasonFor(CourseDetailReader.MODULO, ENTIDAD, fila.id()).orElse(null)
            : null;
    return LessonResponse.from(fila, motivo);
  }
}
