package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.LessonResponse;
import com.factech.nexus.modules.academy.application.UpdateLessonRequest;
import com.factech.nexus.modules.academy.domain.models.Lesson;
import com.factech.nexus.modules.academy.domain.repository.JpaLessonRepository;
import com.factech.nexus.modules.academy.domain.repository.LessonRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-029`: corregir una lección, <b>incluido el tipo y el contenido</b>.
 *
 * <p>La forma de los campos se comprueba junta y antes de consultar (`VAL-002`, `VAL-003`,
 * `VAL-005`); <b>la pareja {@code (tipo, contenido)} resultante</b> (`VAL-004`) solo puede
 * comprobarse después de leer la fila, y la comprueba el agregado antes de aplicar nada. El
 * contenido se audita como longitud y no como texto.
 */
@Service
public class UpdateLessonService {

  static final String MENSAJE_LECCION =
      "No existe una lección viva con ese identificador en este módulo.";

  private final LessonRepository lecciones;
  private final AuditWriter auditoria;
  private final LessonDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public UpdateLessonService(
      LessonRepository lecciones, AuditWriter auditoria, LessonDetailReader detalle) {
    this(lecciones, auditoria, detalle, Clock.systemUTC());
  }

  UpdateLessonService(
      LessonRepository lecciones, AuditWriter auditoria, LessonDetailReader detalle, Clock reloj) {
    this.lecciones = lecciones;
    this.auditoria = auditoria;
    this.detalle = detalle;
    this.reloj = reloj;
  }

  @Transactional
  public LessonResponse update(
      UUID courseId, UUID moduleId, UUID lessonId, UpdateLessonRequest peticion) {
    verificarFormato(peticion);

    Lesson leccion =
        lecciones
            .findAliveByIdInModuleForUpdate(courseId, moduleId, lessonId)
            .orElseThrow(() -> new ResourceNotFoundException("EX-002", MENSAJE_LECCION));

    if (peticion.title().presente()) {
      String titulo = peticion.title().valor().trim();
      if (lecciones.existsAliveTitleInModuleForOther(moduleId, titulo, leccion.getId())) {
        throw new BusinessRuleException(
            "EX-001",
            JpaLessonRepository.MENSAJE_TITULO,
            List.of(new FieldError("title", "EX-001", JpaLessonRepository.MENSAJE_TITULO)));
      }
    }

    Map<String, Object> cambios =
        leccion.update(
            peticion.type(),
            peticion.title(),
            peticion.description(),
            peticion.content(),
            peticion.durationSeconds(),
            peticion.displayOrder(),
            peticion.open(),
            OffsetDateTime.now(reloj));

    if (!cambios.isEmpty()) {
      lecciones.flush();
      auditoria.recordChange(
          new ChangeEvent(
              CourseDetailReader.MODULO,
              LessonDetailReader.ENTIDAD,
              leccion.getId(),
              ChangeAction.UPDATE,
              cambios));
    }
    return detalle.leer(courseId, moduleId, leccion.getId());
  }

  private static void verificarFormato(UpdateLessonRequest peticion) {
    List<FieldError> problemas = new ArrayList<>();
    if (!peticion.informaAlgo()) {
      problemas.add(
          new FieldError(
              "body", "VAL-005", "Debe informar al menos uno de los campos corregibles."));
    }
    if (peticion.type().presente() && peticion.type().valor() == null) {
      problemas.add(
          new FieldError(
              "type",
              "VAL-002",
              "El tipo de la lección no puede quedar vacío y debe ser VIDEO o TEXTO."));
    }
    if (peticion.title().presente()) {
      String titulo = peticion.title().valor();
      if (titulo == null || titulo.isBlank() || titulo.trim().length() > 150) {
        problemas.add(
            new FieldError(
                "title",
                "VAL-002",
                "El título de la lección no puede quedar vacío ni superar los 150 caracteres."));
      }
    }
    if (peticion.durationSeconds().presente()) {
      Integer duracion = peticion.durationSeconds().valor();
      if (duracion == null || duracion <= 0) {
        problemas.add(
            new FieldError(
                "durationSeconds",
                "VAL-002",
                "La duración de la lección no puede quedar vacía y debe ser un entero de segundos"
                    + " mayor que cero."));
      }
    }
    if (peticion.displayOrder().presente()) {
      Integer orden = peticion.displayOrder().valor();
      if (orden == null || orden < 0) {
        problemas.add(
            new FieldError(
                "displayOrder",
                "VAL-002",
                "El orden de la lección no puede quedar vacío y debe ser un entero mayor o igual"
                    + " que cero."));
      }
    }
    if (peticion.open().presente() && peticion.open().valor() == null) {
      problemas.add(
          new FieldError("open", "VAL-002", "La bandera de demostración no puede quedar vacía."));
    }
    Patchable<String> descripcion = peticion.description();
    if (descripcion.presente()
        && descripcion.valor() != null
        && descripcion.valor().trim().length() > 1000) {
      problemas.add(
          new FieldError(
              "description", "VAL-003", "La descripción no puede exceder 1000 caracteres."));
    }
    if (!problemas.isEmpty()) {
      throw new ValidationException(
          problemas.get(0).code(),
          problemas.size() == 1
              ? problemas.get(0).message()
              : "La petición trae " + problemas.size() + " campos inválidos.",
          problemas);
    }
  }
}
