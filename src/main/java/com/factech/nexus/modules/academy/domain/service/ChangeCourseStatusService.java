package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.ChangeCourseStatusRequest;
import com.factech.nexus.modules.academy.application.CourseDetailResponse;
import com.factech.nexus.modules.academy.domain.models.Course;
import com.factech.nexus.modules.academy.domain.models.CourseStatus;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
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
 * Caso de uso `RF-AC-012`: publicar o despublicar un curso.
 *
 * <p>Es `RF-PM-021` para cursos: activar exige lo que `RN-AC-009` dice —descripción corta,
 * descripción larga y <b>al menos un módulo {@code ACTIVO}</b> no retirado— <b>comprobado
 * junto</b>, de modo que si faltan las tres cosas la respuesta trae los tres motivos; desactivar no
 * exige nada. <b>No se exige una membresía</b>: un curso activo sin lista existe y no se ofrece, y
 * el detalle lo dice (`CA-AC-066`). Pedir el estado que ya tiene responde {@code 200} sin escribir.
 */
@Service
public class ChangeCourseStatusService {

  static final String SIN_CORTA =
      "El curso no tiene descripción corta: no se publica lo que no se explica.";
  static final String SIN_LARGA =
      "El curso no tiene descripción larga: no se publica lo que no se explica.";
  static final String SIN_MODULO =
      "El curso no tiene ningún módulo activo: no se publica lo que está vacío.";

  private final CourseRepository cursos;
  private final CourseQueryRepository consultas;
  private final AuditWriter auditoria;
  private final CourseDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public ChangeCourseStatusService(
      CourseRepository cursos,
      CourseQueryRepository consultas,
      AuditWriter auditoria,
      CourseDetailReader detalle) {
    this(cursos, consultas, auditoria, detalle, Clock.systemUTC());
  }

  ChangeCourseStatusService(
      CourseRepository cursos,
      CourseQueryRepository consultas,
      AuditWriter auditoria,
      CourseDetailReader detalle,
      Clock reloj) {
    this.cursos = cursos;
    this.consultas = consultas;
    this.auditoria = auditoria;
    this.detalle = detalle;
    this.reloj = reloj;
  }

  @Transactional
  public CourseDetailResponse change(UUID id, ChangeCourseStatusRequest peticion) {
    Course curso =
        cursos
            .findAliveByIdForUpdate(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un curso vivo con ese identificador."));

    CourseStatus antes = curso.getStatus();
    boolean cambio;
    if (peticion.status() == CourseStatus.ACTIVO) {
      if (antes != CourseStatus.ACTIVO) {
        verificarCondicionesDeActivacion(curso);
      }
      cambio = curso.activate(OffsetDateTime.now(reloj));
    } else {
      cambio = curso.deactivate(OffsetDateTime.now(reloj));
    }

    if (cambio) {
      auditoria.recordChange(
          new ChangeEvent(
              CourseDetailReader.MODULO,
              CourseDetailReader.ENTIDAD,
              curso.getId(),
              ChangeAction.UPDATE,
              Map.of("status", Map.of("before", antes.name(), "after", curso.getStatus().name()))));
    }
    return detalle.leer(curso.getId());
  }

  /** `EX-002`, `EX-003` y `EX-004`, <b>juntos</b> (`CA-AC-065`). */
  private void verificarCondicionesDeActivacion(Course curso) {
    List<FieldError> faltas = new ArrayList<>();
    if (!curso.tieneDescripcionCorta()) {
      faltas.add(new FieldError("shortDescription", "EX-002", SIN_CORTA));
    }
    if (!curso.tieneDescripcionLarga()) {
      faltas.add(new FieldError("longDescription", "EX-003", SIN_LARGA));
    }
    if (consultas.countActiveModulesOf(curso.getId()) == 0) {
      faltas.add(new FieldError("modules", "EX-004", SIN_MODULO));
    }
    if (!faltas.isEmpty()) {
      throw new BusinessRuleException(
          faltas.get(0).code(),
          faltas.size() == 1
              ? faltas.get(0).message()
              : "El curso no puede activarse por " + faltas.size() + " motivos.",
          faltas);
    }
  }
}
