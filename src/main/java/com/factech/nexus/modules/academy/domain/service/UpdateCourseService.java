package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseDetailResponse;
import com.factech.nexus.modules.academy.application.UpdateCourseRequest;
import com.factech.nexus.modules.academy.domain.models.Course;
import com.factech.nexus.modules.academy.domain.models.VideoUrl;
import com.factech.nexus.modules.academy.domain.repository.CourseRepository;
import com.factech.nexus.modules.academy.domain.repository.JpaCourseRepository;
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
 * Caso de uso `RF-AC-011`: corregir un curso.
 *
 * <p>Es `RF-AC-004` para cursos —parcial, nulo explícito como orden donde el vacío es legítimo,
 * unicidad del título frente a los otros vivos, auditoría solo de lo que cambió, <b>sin
 * inmutables</b>— más dos cosas: <b>el instructor se reasigna</b> repitiendo la comprobación del
 * alta sobre el nuevo ({@link InstructorVerifier}), y <b>las descripciones y el video se vacían
 * aunque el curso esté activo</b>: `RN-AC-009` rige al activar, y un curso activo sin descripción
 * sigue activo y deja de ofrecerse (`RN-AC-015`, `CA-AC-214`).
 *
 * <p>Las validaciones de forma se devuelven <b>juntas</b> y <b>antes de cualquier consulta</b>.
 */
@Service
public class UpdateCourseService {

  private final CourseRepository cursos;
  private final InstructorVerifier instructor;
  private final AuditWriter auditoria;
  private final CourseDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public UpdateCourseService(
      CourseRepository cursos,
      InstructorVerifier instructor,
      AuditWriter auditoria,
      CourseDetailReader detalle) {
    this(cursos, instructor, auditoria, detalle, Clock.systemUTC());
  }

  UpdateCourseService(
      CourseRepository cursos,
      InstructorVerifier instructor,
      AuditWriter auditoria,
      CourseDetailReader detalle,
      Clock reloj) {
    this.cursos = cursos;
    this.instructor = instructor;
    this.auditoria = auditoria;
    this.detalle = detalle;
    this.reloj = reloj;
  }

  @Transactional
  public CourseDetailResponse update(UUID id, UpdateCourseRequest peticion) {
    verificarFormato(peticion);

    Course curso =
        cursos
            .findAliveByIdForUpdate(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-002", "No existe un curso vivo con ese identificador."));

    if (peticion.title().presente()) {
      String titulo = peticion.title().valor().trim();
      if (cursos.existsAliveTitleForOther(titulo, curso.getId())) {
        throw new BusinessRuleException(
            "EX-001",
            JpaCourseRepository.MENSAJE_TITULO,
            List.of(new FieldError("title", "EX-001", JpaCourseRepository.MENSAJE_TITULO)));
      }
    }
    // Reasignar al mismo no repite la comprobación: nada cambia y nada se audita.
    if (peticion.instructorId().presente()
        && !peticion.instructorId().valor().equals(curso.getInstructorId())) {
      instructor.verificar(peticion.instructorId().valor());
    }

    Map<String, Object> cambios =
        curso.update(
            peticion.title(),
            peticion.instructorId(),
            peticion.difficulty(),
            peticion.shortDescription(),
            peticion.longDescription(),
            peticion.introVideoUrl(),
            peticion.displayOrder(),
            OffsetDateTime.now(reloj));

    if (!cambios.isEmpty()) {
      // El volcado explícito convierte una carrera sobre el título en el `409`
      // traducido, y no en un fallo al confirmar fuera de este método.
      cursos.flush();
      auditoria.recordChange(
          new ChangeEvent(
              CourseDetailReader.MODULO,
              CourseDetailReader.ENTIDAD,
              curso.getId(),
              ChangeAction.UPDATE,
              cambios));
    }
    return detalle.leer(curso.getId());
  }

  /**
   * `VAL-002` a `VAL-007`, <b>juntas</b>: título, instructor, dificultad y orden no admiten
   * vaciarse; las descripciones y el video sí, y con valor se acotan; y tiene que venir al menos
   * uno de los siete.
   */
  private static void verificarFormato(UpdateCourseRequest peticion) {
    List<FieldError> problemas = new ArrayList<>();
    if (!peticion.informaAlgo()) {
      problemas.add(
          new FieldError(
              "body", "VAL-007", "Debe informar al menos uno de los campos corregibles."));
    }
    if (peticion.title().presente()) {
      String titulo = peticion.title().valor();
      if (titulo == null || titulo.isBlank() || titulo.trim().length() > 150) {
        problemas.add(
            new FieldError(
                "title",
                "VAL-002",
                "El título del curso no puede quedar vacío ni superar los 150 caracteres."));
      }
    }
    if (peticion.instructorId().presente() && peticion.instructorId().valor() == null) {
      problemas.add(
          new FieldError(
              "instructorId", "VAL-003", "El instructor del curso no puede quedar vacío."));
    }
    if (peticion.difficulty().presente() && peticion.difficulty().valor() == null) {
      problemas.add(
          new FieldError(
              "difficulty",
              "VAL-004",
              "La dificultad del curso no puede quedar vacía y debe ser PRINCIPIANTE, INTERMEDIO"
                  + " o AVANZADO."));
    }
    if (peticion.displayOrder().presente()) {
      Integer orden = peticion.displayOrder().valor();
      if (orden == null || orden < 0) {
        problemas.add(
            new FieldError(
                "displayOrder",
                "VAL-005",
                "El orden del curso no puede quedar vacío y debe ser un entero mayor o igual que"
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
    Patchable<String> video = peticion.introVideoUrl();
    if (video.presente() && video.valor() != null && !video.valor().isBlank()) {
      String enlace = video.valor().trim();
      if (!VideoUrl.esValida(enlace)) {
        problemas.add(new FieldError("introVideoUrl", "VAL-006", VideoUrl.mensaje()));
      }
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
      problemas.add(new FieldError(nombre, "VAL-006", mensaje));
    }
  }
}
