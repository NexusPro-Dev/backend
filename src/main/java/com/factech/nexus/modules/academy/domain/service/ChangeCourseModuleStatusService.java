package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.ChangeCourseModuleStatusRequest;
import com.factech.nexus.modules.academy.application.CourseModuleDetailResponse;
import com.factech.nexus.modules.academy.domain.models.CourseModule;
import com.factech.nexus.modules.academy.domain.models.CourseStatus;
import com.factech.nexus.modules.academy.domain.repository.CourseModuleQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseModuleRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-024`: publicar o despublicar un módulo. Una sola condición para activar
 * —<b>una lección {@code ACTIVA} viva</b> (`RN-AC-009`), sin exigirle contenido: lo tuvo al
 * activarse—; y desactivar nunca se rechaza, aunque sea el último módulo activo de un curso activo:
 * el curso queda activo y no ofrecible, y el detalle lo dice (`RN-AC-015`).
 */
@Service
public class ChangeCourseModuleStatusService {

  static final String SIN_LECCION =
      "El módulo no tiene ninguna lección activa: no se publica lo que está vacío.";

  private final CourseModuleRepository modulos;
  private final CourseModuleQueryRepository consultas;
  private final AuditWriter auditoria;
  private final CourseModuleDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public ChangeCourseModuleStatusService(
      CourseModuleRepository modulos,
      CourseModuleQueryRepository consultas,
      AuditWriter auditoria,
      CourseModuleDetailReader detalle) {
    this(modulos, consultas, auditoria, detalle, Clock.systemUTC());
  }

  ChangeCourseModuleStatusService(
      CourseModuleRepository modulos,
      CourseModuleQueryRepository consultas,
      AuditWriter auditoria,
      CourseModuleDetailReader detalle,
      Clock reloj) {
    this.modulos = modulos;
    this.consultas = consultas;
    this.auditoria = auditoria;
    this.detalle = detalle;
    this.reloj = reloj;
  }

  @Transactional
  public CourseModuleDetailResponse change(
      UUID courseId, UUID moduleId, ChangeCourseModuleStatusRequest peticion) {
    CourseModule modulo =
        modulos
            .findAliveByIdInCourseForUpdate(courseId, moduleId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", UpdateCourseModuleService.MENSAJE_MODULO));

    CourseStatus antes = modulo.getStatus();
    boolean cambio;
    if (peticion.status() == CourseStatus.ACTIVO) {
      if (antes != CourseStatus.ACTIVO && !tieneLeccionActiva(modulo.getId())) {
        throw new BusinessRuleException(
            "EX-002", SIN_LECCION, List.of(new FieldError("lessons", "EX-002", SIN_LECCION)));
      }
      cambio = modulo.activate(OffsetDateTime.now(reloj));
    } else {
      cambio = modulo.deactivate(OffsetDateTime.now(reloj));
    }

    if (cambio) {
      auditoria.recordChange(
          new ChangeEvent(
              CourseDetailReader.MODULO,
              CourseModuleDetailReader.ENTIDAD,
              modulo.getId(),
              ChangeAction.UPDATE,
              Map.of(
                  "status", Map.of("before", antes.name(), "after", modulo.getStatus().name()))));
    }
    return detalle.leer(courseId, modulo.getId());
  }

  /**
   * La condición de `RN-AC-009` para el módulo: una lección viva y {@code ACTIVA}, con o sin
   * contenido.
   */
  private boolean tieneLeccionActiva(UUID moduleId) {
    return consultas.findLessonsOf(moduleId).stream()
        .anyMatch(
            leccion -> !leccion.retirada() && CourseStatus.ACTIVO.name().equals(leccion.status()));
  }
}
