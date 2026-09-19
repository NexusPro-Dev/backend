package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseModuleDetailResponse;
import com.factech.nexus.modules.academy.domain.repository.CourseModuleQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ModuleRow;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Arma la respuesta de un módulo para las operaciones que lo devuelven (`RF-AC-022` a `RF-AC-027`):
 * la fila con sus cuentas → sus lecciones → ofrecibilidad. <b>Dos sentencias</b>; la segunda se
 * cortocircuita cuando el módulo no tiene lecciones vivas ni retiradas que enseñar.
 */
@Component
public class CourseModuleDetailReader {

  static final String ENTIDAD = "course_modules";

  private final CourseModuleQueryRepository consultas;

  public CourseModuleDetailReader(CourseModuleQueryRepository consultas) {
    this.consultas = consultas;
  }

  public CourseModuleDetailResponse leer(UUID courseId, UUID moduleId) {
    ModuleRow fila =
        consultas
            .findDetail(courseId, moduleId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un módulo con ese identificador en este curso."));
    return CourseModuleDetailResponse.from(fila, consultas.findLessonsOf(fila.id()));
  }
}
