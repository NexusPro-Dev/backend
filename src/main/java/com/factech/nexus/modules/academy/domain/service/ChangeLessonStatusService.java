package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.ChangeLessonStatusRequest;
import com.factech.nexus.modules.academy.application.LessonResponse;
import com.factech.nexus.modules.academy.domain.models.CourseStatus;
import com.factech.nexus.modules.academy.domain.models.Lesson;
import com.factech.nexus.modules.academy.domain.repository.LessonRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-030`: publicar o despublicar una lección. Activar exige contenido, y lo
 * comprueba el agregado sin leer nada más; desactivar nunca se rechaza, y si era la última activa
 * del módulo, el módulo queda activo y no ofrecible, y su duración baja en la siguiente lectura.
 * <b>Se bloquea la lección y no el módulo</b> (`RF-AC-024` §13): la carrera con la activación del
 * módulo tiene un resultado legítimo.
 */
@Service
public class ChangeLessonStatusService {

  private final LessonRepository lecciones;
  private final AuditWriter auditoria;
  private final LessonDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public ChangeLessonStatusService(
      LessonRepository lecciones, AuditWriter auditoria, LessonDetailReader detalle) {
    this(lecciones, auditoria, detalle, Clock.systemUTC());
  }

  ChangeLessonStatusService(
      LessonRepository lecciones, AuditWriter auditoria, LessonDetailReader detalle, Clock reloj) {
    this.lecciones = lecciones;
    this.auditoria = auditoria;
    this.detalle = detalle;
    this.reloj = reloj;
  }

  @Transactional
  public LessonResponse change(
      UUID courseId, UUID moduleId, UUID lessonId, ChangeLessonStatusRequest peticion) {
    Lesson leccion =
        lecciones
            .findAliveByIdInModuleForUpdate(courseId, moduleId, lessonId)
            .orElseThrow(
                () -> new ResourceNotFoundException("EX-001", UpdateLessonService.MENSAJE_LECCION));

    CourseStatus antes = leccion.getStatus();
    boolean cambio =
        peticion.status() == CourseStatus.ACTIVO
            ? leccion.activate(OffsetDateTime.now(reloj))
            : leccion.deactivate(OffsetDateTime.now(reloj));

    if (cambio) {
      auditoria.recordChange(
          new ChangeEvent(
              CourseDetailReader.MODULO,
              LessonDetailReader.ENTIDAD,
              leccion.getId(),
              ChangeAction.UPDATE,
              Map.of(
                  "status", Map.of("before", antes.name(), "after", leccion.getStatus().name()))));
    }
    return detalle.leer(courseId, moduleId, leccion.getId());
  }
}
