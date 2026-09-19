package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseModuleDetailResponse;
import com.factech.nexus.modules.academy.application.UpdateCourseModuleRequest;
import com.factech.nexus.modules.academy.domain.models.CourseModule;
import com.factech.nexus.modules.academy.domain.models.VideoUrl;
import com.factech.nexus.modules.academy.domain.repository.CourseModuleRepository;
import com.factech.nexus.modules.academy.domain.repository.JpaCourseModuleRepository;
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
 * Caso de uso `RF-AC-023`: corregir un módulo. `RF-AC-011` sin instructor: parcial, sin inmutables,
 * unicidad del título dentro del curso, descripciones y video vaciables en cualquier estado, y el
 * módulo de otro curso es {@code 404}.
 */
@Service
public class UpdateCourseModuleService {

  static final String MENSAJE_MODULO =
      "No existe un módulo vivo con ese identificador en este curso.";

  private final CourseModuleRepository modulos;
  private final AuditWriter auditoria;
  private final CourseModuleDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public UpdateCourseModuleService(
      CourseModuleRepository modulos, AuditWriter auditoria, CourseModuleDetailReader detalle) {
    this(modulos, auditoria, detalle, Clock.systemUTC());
  }

  UpdateCourseModuleService(
      CourseModuleRepository modulos,
      AuditWriter auditoria,
      CourseModuleDetailReader detalle,
      Clock reloj) {
    this.modulos = modulos;
    this.auditoria = auditoria;
    this.detalle = detalle;
    this.reloj = reloj;
  }

  @Transactional
  public CourseModuleDetailResponse update(
      UUID courseId, UUID moduleId, UpdateCourseModuleRequest peticion) {
    verificarFormato(peticion);

    CourseModule modulo =
        modulos
            .findAliveByIdInCourseForUpdate(courseId, moduleId)
            .orElseThrow(() -> new ResourceNotFoundException("EX-002", MENSAJE_MODULO));

    if (peticion.title().presente()) {
      String titulo = peticion.title().valor().trim();
      if (modulos.existsAliveTitleInCourseForOther(courseId, titulo, modulo.getId())) {
        throw new BusinessRuleException(
            "EX-001",
            JpaCourseModuleRepository.MENSAJE_TITULO,
            List.of(new FieldError("title", "EX-001", JpaCourseModuleRepository.MENSAJE_TITULO)));
      }
    }

    Map<String, Object> cambios =
        modulo.update(
            peticion.title(),
            peticion.shortDescription(),
            peticion.longDescription(),
            peticion.presentationVideoUrl(),
            peticion.displayOrder(),
            OffsetDateTime.now(reloj));

    if (!cambios.isEmpty()) {
      modulos.flush();
      auditoria.recordChange(
          new ChangeEvent(
              CourseDetailReader.MODULO,
              CourseModuleDetailReader.ENTIDAD,
              modulo.getId(),
              ChangeAction.UPDATE,
              cambios));
    }
    return detalle.leer(courseId, modulo.getId());
  }

  private static void verificarFormato(UpdateCourseModuleRequest peticion) {
    List<FieldError> problemas = new ArrayList<>();
    if (!peticion.informaAlgo()) {
      problemas.add(
          new FieldError(
              "body", "VAL-005", "Debe informar al menos uno de los campos corregibles."));
    }
    if (peticion.title().presente()) {
      String titulo = peticion.title().valor();
      if (titulo == null || titulo.isBlank() || titulo.trim().length() > 150) {
        problemas.add(
            new FieldError(
                "title",
                "VAL-002",
                "El título del módulo no puede quedar vacío ni superar los 150 caracteres."));
      }
    }
    if (peticion.displayOrder().presente()) {
      Integer orden = peticion.displayOrder().valor();
      if (orden == null || orden < 0) {
        problemas.add(
            new FieldError(
                "displayOrder",
                "VAL-003",
                "El orden del módulo no puede quedar vacío y debe ser un entero mayor o igual que"
                    + " cero."));
      }
    }
    acotar(
        peticion.shortDescription(),
        300,
        "shortDescription",
        "La descripción corta no puede exceder 300 caracteres.",
        problemas);
    acotar(
        peticion.longDescription(),
        10_000,
        "longDescription",
        "La descripción larga no puede exceder 10 000 caracteres.",
        problemas);
    Patchable<String> video = peticion.presentationVideoUrl();
    if (video.presente()
        && video.valor() != null
        && !video.valor().isBlank()
        && !VideoUrl.esValida(video.valor().trim())) {
      problemas.add(new FieldError("presentationVideoUrl", "VAL-004", VideoUrl.mensaje()));
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

  private static void acotar(
      Patchable<String> campo,
      int tope,
      String nombre,
      String mensaje,
      List<FieldError> problemas) {
    if (campo.presente() && campo.valor() != null && campo.valor().trim().length() > tope) {
      problemas.add(new FieldError(nombre, "VAL-004", mensaje));
    }
  }
}
