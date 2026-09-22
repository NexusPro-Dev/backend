package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.DeleteLessonRequest;
import com.factech.nexus.modules.academy.domain.models.Lesson;
import com.factech.nexus.modules.academy.domain.repository.LessonRepository;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.audit.DeletionReason;
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
 * Caso de uso `RF-AC-031`: retirar una lección, <b>sin arrastre</b> —no hay nada debajo— y con
 * <b>la única instantánea que lleva el contenido entero</b>: el registro de eliminación es el sitio
 * donde el texto queda para quien lo quiera recuperar (`RF-AC-028` §14.1). Tres sentencias.
 */
@Service
public class DeleteLessonService {

  private final LessonRepository lecciones;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public DeleteLessonService(LessonRepository lecciones, AuditWriter auditoria) {
    this(lecciones, auditoria, Clock.systemUTC());
  }

  DeleteLessonService(LessonRepository lecciones, AuditWriter auditoria, Clock reloj) {
    this.lecciones = lecciones;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public void delete(UUID courseId, UUID moduleId, UUID lessonId, DeleteLessonRequest peticion) {
    DeletionReason motivo = new DeletionReason(peticion == null ? null : peticion.reason());

    Lesson leccion =
        lecciones
            .findByIdInModuleForUpdate(courseId, moduleId, lessonId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe una lección con ese identificador en este módulo."));
    if (leccion.estaRetirada()) {
      String mensaje = "La lección ya está retirada.";
      throw new BusinessRuleException(
          "EX-002", mensaje, List.of(new FieldError("id", "EX-002", mensaje)));
    }

    Map<String, Object> instantanea = leccion.instantaneaCompleta();
    leccion.delete(OffsetDateTime.now(reloj));

    auditoria.recordDeletion(
        new DeletionEvent(
            CourseDetailReader.MODULO,
            LessonDetailReader.ENTIDAD,
            leccion.getId(),
            DeletionType.LOGICAL,
            motivo.value(),
            instantanea));
  }
}
