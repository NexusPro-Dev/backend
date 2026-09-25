package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.LessonResponse;
import com.factech.nexus.modules.academy.application.RegisterLessonRequest;
import com.factech.nexus.modules.academy.domain.models.CourseModule;
import com.factech.nexus.modules.academy.domain.models.Lesson;
import com.factech.nexus.modules.academy.domain.models.LessonContent;
import com.factech.nexus.modules.academy.domain.models.LessonType;
import com.factech.nexus.modules.academy.domain.models.VideoUrl;
import com.factech.nexus.modules.academy.domain.repository.CourseModuleRepository;
import com.factech.nexus.modules.academy.domain.repository.JpaLessonRepository;
import com.factech.nexus.modules.academy.domain.repository.LessonRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-028`: registrar una lección dentro de un módulo.
 *
 * <p><b>Se bloquea el módulo, no el curso</b>: es el padre inmediato, y lo que hay que ordenar es
 * el retiro del módulo. <b>Las validaciones se devuelven juntas aquí y no por Bean Validation</b>,
 * porque una de ellas —el contenido de un {@code VIDEO} es una URL— depende del tipo, y `CA-AC-088`
 * exige que llegue junto con las demás. La lección nace {@code INACTIVA}, con {@code open} falsa si
 * no vino, y con el contenido tal como llegó.
 */
@Service
public class RegisterLessonService {

  static final String MENSAJE_MODULO =
      "No existe un módulo vivo con ese identificador en este curso.";

  private final CourseModuleRepository modulos;
  private final LessonRepository lecciones;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final LessonDetailReader detalle;
  private final LessonDurationReader duraciones;
  private final Clock reloj;

  @Autowired
  public RegisterLessonService(
      CourseModuleRepository modulos,
      LessonRepository lecciones,
      AuditWriter auditoria,
      UuidV7Generator ids,
      LessonDetailReader detalle,
      LessonDurationReader duraciones) {
    this(modulos, lecciones, auditoria, ids, detalle, duraciones, Clock.systemUTC());
  }

  RegisterLessonService(
      CourseModuleRepository modulos,
      LessonRepository lecciones,
      AuditWriter auditoria,
      UuidV7Generator ids,
      LessonDetailReader detalle,
      LessonDurationReader duraciones,
      Clock reloj) {
    this.modulos = modulos;
    this.lecciones = lecciones;
    this.auditoria = auditoria;
    this.ids = ids;
    this.detalle = detalle;
    this.duraciones = duraciones;
    this.reloj = reloj;
  }

  @Transactional
  public LessonResponse register(UUID courseId, UUID moduleId, RegisterLessonRequest peticion) {
    verificarFormato(peticion);

    CourseModule modulo =
        modulos
            .findAliveByIdInCourseForUpdate(courseId, moduleId)
            .orElseThrow(() -> new ResourceNotFoundException("EX-001", MENSAJE_MODULO));

    if (lecciones.existsAliveTitleInModule(modulo.getId(), peticion.title())) {
      throw new BusinessRuleException(
          "EX-002",
          JpaLessonRepository.MENSAJE_TITULO,
          List.of(new FieldError("title", "EX-002", JpaLessonRepository.MENSAJE_TITULO)));
    }

    // La duración del video, del proveedor, cuando no vino (`EX-003`). Después de
    // las comprobaciones que no cuestan red, y antes de escribir nada.
    Integer duracion =
        peticion.durationSeconds() != null
            ? peticion.durationSeconds()
            : duraciones.leer(peticion.content());

    Lesson nueva =
        lecciones.save(
            Lesson.create(
                ids.next(),
                modulo.getId(),
                peticion.type(),
                peticion.title(),
                peticion.description(),
                peticion.content(),
                duracion,
                peticion.displayOrder(),
                peticion.open(),
                OffsetDateTime.now(reloj)));

    auditoria.recordChange(
        new ChangeEvent(
            CourseDetailReader.MODULO,
            LessonDetailReader.ENTIDAD,
            nueva.getId(),
            ChangeAction.CREATE,
            nueva.instantanea()));

    return detalle.leer(courseId, modulo.getId(), nueva.getId());
  }

  /**
   * `VAL-002` a `VAL-007`, <b>juntas</b>; `VAL-006` solo si el tipo es válido y es {@code VIDEO}.
   */
  static void verificarFormato(RegisterLessonRequest peticion) {
    List<FieldError> problemas = new ArrayList<>();
    if (peticion.type() == null) {
      problemas.add(
          new FieldError("type", "VAL-002", "El tipo es obligatorio y debe ser VIDEO o TEXTO."));
    }
    if (peticion.title() == null || peticion.title().isBlank() || peticion.title().length() > 150) {
      problemas.add(
          new FieldError(
              "title",
              "VAL-003",
              "El título es obligatorio y no puede superar los 150 caracteres."));
    }
    // `RN-AC-017` desde el 25-09-2026: si viene, positiva; si no viene, solo se
    // admite en un VIDEO con enlace, que es de donde se lee.
    boolean seLeeDelVideo =
        peticion.type() == LessonType.VIDEO
            && peticion.content() != null
            && !peticion.content().isBlank();
    if (peticion.durationSeconds() == null ? !seLeeDelVideo : peticion.durationSeconds() <= 0) {
      problemas.add(
          new FieldError(
              "durationSeconds",
              "VAL-004",
              "La duración es obligatoria y debe ser un entero de segundos mayor que cero."));
    }
    if (peticion.displayOrder() == null || peticion.displayOrder() < 0) {
      problemas.add(
          new FieldError(
              "displayOrder",
              "VAL-005",
              "El orden es obligatorio y debe ser un entero mayor o igual que cero."));
    }
    if (peticion.type() == LessonType.VIDEO
        && peticion.content() != null
        && !peticion.content().isBlank()
        && !VideoUrl.esValida(peticion.content().strip())) {
      problemas.add(new FieldError("content", "VAL-006", LessonContent.MENSAJE_VIDEO));
    }
    if (peticion.description() != null && peticion.description().length() > 1000) {
      problemas.add(
          new FieldError(
              "description", "VAL-007", "La descripción no puede exceder 1000 caracteres."));
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
