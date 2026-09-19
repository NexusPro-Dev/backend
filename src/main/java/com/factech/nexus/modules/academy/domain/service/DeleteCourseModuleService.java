package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.DeleteCourseModuleRequest;
import com.factech.nexus.modules.academy.domain.models.CourseModule;
import com.factech.nexus.modules.academy.domain.repository.CourseModuleRepository;
import com.factech.nexus.shared.audit.DeletionReason;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-025`: retirar un módulo con sus lecciones. `RF-AC-013` un nivel abajo, con
 * {@code courses:update}: motivo antes de cualquier consulta, el módulo del curso en cualquier
 * estado y bloqueado, y el arrastre por {@link CourseTreeRetirement}. El curso no cambia: si era su
 * último módulo activo, queda activo y no ofrecible (`RN-AC-015`).
 */
@Service
public class DeleteCourseModuleService {

  private final CourseModuleRepository modulos;
  private final CourseTreeRetirement arrastre;
  private final Clock reloj;

  @Autowired
  public DeleteCourseModuleService(CourseModuleRepository modulos, CourseTreeRetirement arrastre) {
    this(modulos, arrastre, Clock.systemUTC());
  }

  DeleteCourseModuleService(
      CourseModuleRepository modulos, CourseTreeRetirement arrastre, Clock reloj) {
    this.modulos = modulos;
    this.arrastre = arrastre;
    this.reloj = reloj;
  }

  @Transactional
  public void delete(UUID courseId, UUID moduleId, DeleteCourseModuleRequest peticion) {
    DeletionReason motivo = new DeletionReason(peticion == null ? null : peticion.reason());

    CourseModule modulo =
        modulos
            .findByIdInCourseForUpdate(courseId, moduleId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un módulo con ese identificador en este curso."));
    if (modulo.estaRetirado()) {
      String mensaje = "El módulo ya está retirado.";
      throw new BusinessRuleException(
          "EX-002", mensaje, List.of(new FieldError("id", "EX-002", mensaje)));
    }

    arrastre.retirarModulo(modulo, motivo.value(), OffsetDateTime.now(reloj));
  }
}
