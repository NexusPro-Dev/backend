package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseModuleDetailResponse;
import com.factech.nexus.modules.academy.application.RegisterCourseModuleRequest;
import com.factech.nexus.modules.academy.domain.models.Course;
import com.factech.nexus.modules.academy.domain.models.CourseModule;
import com.factech.nexus.modules.academy.domain.repository.CourseModuleRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseRepository;
import com.factech.nexus.modules.academy.domain.repository.JpaCourseModuleRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-022`: registrar un módulo dentro de un curso.
 *
 * <p><b>El curso se bloquea al registrarle un módulo</b>: es lo que ordena esta alta frente a un
 * retiro simultáneo del curso, y lo que hace que «dentro del curso» sea una afirmación y no una
 * carrera. Título contra los módulos vivos del curso, inserción en {@code INACTIVO}, auditoría, y
 * la relectura del módulo — no del curso (`RF-AC-022` §14.1).
 */
@Service
public class RegisterCourseModuleService {

  static final String MENSAJE_CURSO = "No existe un curso vivo con ese identificador.";

  private final CourseRepository cursos;
  private final CourseModuleRepository modulos;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final CourseModuleDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public RegisterCourseModuleService(
      CourseRepository cursos,
      CourseModuleRepository modulos,
      AuditWriter auditoria,
      UuidV7Generator ids,
      CourseModuleDetailReader detalle) {
    this(cursos, modulos, auditoria, ids, detalle, Clock.systemUTC());
  }

  RegisterCourseModuleService(
      CourseRepository cursos,
      CourseModuleRepository modulos,
      AuditWriter auditoria,
      UuidV7Generator ids,
      CourseModuleDetailReader detalle,
      Clock reloj) {
    this.cursos = cursos;
    this.modulos = modulos;
    this.auditoria = auditoria;
    this.ids = ids;
    this.detalle = detalle;
    this.reloj = reloj;
  }

  @Transactional
  public CourseModuleDetailResponse register(UUID courseId, RegisterCourseModuleRequest peticion) {
    Course curso =
        cursos
            .findAliveByIdForUpdate(courseId)
            .orElseThrow(() -> new ResourceNotFoundException("EX-001", MENSAJE_CURSO));

    if (peticion.title() != null
        && modulos.existsAliveTitleInCourse(curso.getId(), peticion.title())) {
      throw new BusinessRuleException(
          "EX-002",
          JpaCourseModuleRepository.MENSAJE_TITULO,
          List.of(new FieldError("title", "EX-002", JpaCourseModuleRepository.MENSAJE_TITULO)));
    }

    CourseModule nuevo =
        modulos.save(
            CourseModule.create(
                ids.next(),
                curso.getId(),
                peticion.title(),
                peticion.shortDescription(),
                peticion.longDescription(),
                peticion.presentationVideoUrl(),
                peticion.displayOrder(),
                OffsetDateTime.now(reloj)));

    auditoria.recordChange(
        new ChangeEvent(
            CourseDetailReader.MODULO,
            CourseModuleDetailReader.ENTIDAD,
            nuevo.getId(),
            ChangeAction.CREATE,
            nuevo.instantanea()));

    return detalle.leer(curso.getId(), nuevo.getId());
  }
}
