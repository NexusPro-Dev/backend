package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.domain.models.CourseModule;
import com.factech.nexus.modules.academy.domain.models.Lesson;
import com.factech.nexus.modules.academy.domain.repository.CourseModuleRepository;
import com.factech.nexus.modules.academy.domain.repository.LessonRepository;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * El arrastre de `RN-AC-018`: retirar un curso retira sus módulos y lecciones vivos, y retirar un
 * módulo sus lecciones, <b>con el mismo instante, el mismo motivo y una fila de auditoría por cada
 * uno</b> (`RF-AC-013` · `T-05`, `RF-AC-025`).
 *
 * <p><b>Una sola forma de arrastrar, escrita una vez</b>, para que el retiro del curso y el del
 * módulo no diverjan. Los módulos vivos se leen con {@code FOR UPDATE} en su orden y sus lecciones
 * vivas también; lo ya retirado no se toca. La instantánea de cada lección arrastrada es <b>la
 * completa</b>, con el contenido (`RF-AC-031`), y la del módulo lleva los identificadores de las
 * lecciones que arrastró.
 */
@Component
public class CourseTreeRetirement {

  static final String ENTIDAD_LECCION = "lessons";

  private final CourseModuleRepository modulos;
  private final LessonRepository lecciones;
  private final AuditWriter auditoria;

  public CourseTreeRetirement(
      CourseModuleRepository modulos, LessonRepository lecciones, AuditWriter auditoria) {
    this.modulos = modulos;
    this.lecciones = lecciones;
    this.auditoria = auditoria;
  }

  /** Retira los módulos vivos del curso y sus lecciones; los ya retirados no se tocan. */
  public void retirarArbolDe(UUID courseId, String motivo, OffsetDateTime ahora) {
    for (CourseModule modulo : modulos.findAliveByCourseForUpdate(courseId)) {
      retirarModulo(modulo, motivo, ahora);
    }
  }

  /**
   * Retira un módulo <b>ya bloqueado y vivo</b> con sus lecciones vivas: primero las lecciones —una
   * baja cada una—, después el módulo con sus identificadores en la instantánea.
   */
  public void retirarModulo(CourseModule modulo, String motivo, OffsetDateTime ahora) {
    List<Lesson> vivas = lecciones.findAliveByModuleForUpdate(modulo.getId());
    for (Lesson leccion : vivas) {
      Map<String, Object> instantanea = leccion.instantaneaCompleta();
      leccion.delete(ahora);
      auditoria.recordDeletion(
          new DeletionEvent(
              CourseDetailReader.MODULO,
              ENTIDAD_LECCION,
              leccion.getId(),
              DeletionType.LOGICAL,
              motivo,
              instantanea));
    }
    Map<String, Object> instantanea = new LinkedHashMap<>(modulo.instantanea());
    instantanea.put("lesson_ids", vivas.stream().map(Lesson::getId).map(UUID::toString).toList());
    modulo.delete(ahora);
    auditoria.recordDeletion(
        new DeletionEvent(
            CourseDetailReader.MODULO,
            CourseModuleDetailReader.ENTIDAD,
            modulo.getId(),
            DeletionType.LOGICAL,
            motivo,
            instantanea));
  }
}
