package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.domain.models.CourseMembership;
import com.factech.nexus.modules.academy.domain.models.CourseProduct;
import com.factech.nexus.modules.academy.domain.repository.CourseMembershipRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseProductRepository;
import com.factech.nexus.modules.products.application.ProductCatalog.KindView;
import com.factech.nexus.modules.system.memberships.application.MembershipCatalog.MembershipView;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Las dos llaves de un curso —servicio y membresía— escritas en un solo sitio (`RF-AC-008` §12,
 * `RF-AC-020`, `RF-AC-037`): la fila y su {@code CREATE} de auditoría con el curso como entidad.
 *
 * <p>Como {@link CourseClassifier}, existe porque <b>hay dos puertas</b> para cada llave —la
 * operación suelta y el alta del curso— y dos copias acabarían auditando distinto. Las reglas las
 * comprueba cada caso de uso antes de llamar, con lo que ya resolvió por el puerto; esto solo
 * escribe.
 */
@Component
public class CourseAccessWriter {

  static final String ENTIDAD_SERVICIOS = "course_products";
  static final String ENTIDAD_MEMBRESIAS = "course_memberships";

  private final CourseProductRepository servicios;
  private final CourseMembershipRepository membresias;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public CourseAccessWriter(
      CourseProductRepository servicios,
      CourseMembershipRepository membresias,
      AuditWriter auditoria) {
    this(servicios, membresias, auditoria, Clock.systemUTC());
  }

  CourseAccessWriter(
      CourseProductRepository servicios,
      CourseMembershipRepository membresias,
      AuditWriter auditoria,
      Clock reloj) {
    this.servicios = servicios;
    this.membresias = membresias;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  /** Un servicio ya comprobado —existe, es {@code BOT}, no está retirado— abre el curso. */
  public void darServicio(UUID courseId, KindView servicio) {
    CourseProduct fila =
        servicios.save(
            CourseProduct.create(courseId, servicio.id(), OffsetDateTime.now(reloj)),
            servicio.code());
    auditar(ENTIDAD_SERVICIOS, courseId, fila.instantanea(servicio.code()));
  }

  /** Una membresía ya comprobada —existe en `SP`— abre el curso. */
  public void darMembresia(UUID courseId, MembershipView membresia) {
    CourseMembership fila =
        membresias.save(
            CourseMembership.create(courseId, membresia.id(), OffsetDateTime.now(reloj)),
            membresia.code());
    auditar(ENTIDAD_MEMBRESIAS, courseId, fila.instantanea(membresia.code()));
  }

  private void auditar(String entidad, UUID courseId, java.util.Map<String, Object> instantanea) {
    auditoria.recordChange(
        new ChangeEvent(
            CourseDetailReader.MODULO, entidad, courseId, ChangeAction.CREATE, instantanea));
  }
}
