package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.domain.models.CourseCategory;
import com.factech.nexus.modules.academy.domain.models.CourseCategoryItem;
import com.factech.nexus.modules.academy.domain.repository.CourseCategoryItemRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * La escritura de una clasificación, en un solo sitio (`RF-AC-008` `plan.md` §12): la fila y su
 * {@code CREATE} de auditoría con el curso como entidad.
 *
 * <p>Existe desde el 25-09-2026 porque <b>hay dos puertas</b> para clasificar un curso —la
 * clasificación suelta (`RF-AC-016`) y el alta con {@code categoryIds}— y dos copias acabarían
 * auditando distinto. Las reglas —curso vivo, categoría viva, pareja nueva— las comprueba cada caso
 * de uso antes de llamar; esto solo escribe.
 */
@Component
public class CourseClassifier {

  static final String ENTIDAD_FILA = "course_category_items";

  private final CourseCategoryItemRepository filas;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public CourseClassifier(CourseCategoryItemRepository filas, AuditWriter auditoria) {
    this(filas, auditoria, Clock.systemUTC());
  }

  CourseClassifier(CourseCategoryItemRepository filas, AuditWriter auditoria, Clock reloj) {
    this.filas = filas;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  public void clasificar(UUID courseId, CourseCategory categoria) {
    CourseCategoryItem fila =
        filas.save(
            CourseCategoryItem.create(courseId, categoria.getId(), OffsetDateTime.now(reloj)),
            categoria.getName());
    auditoria.recordChange(
        new ChangeEvent(
            CourseDetailReader.MODULO,
            ENTIDAD_FILA,
            courseId,
            ChangeAction.CREATE,
            fila.instantanea(categoria.getName())));
  }
}
