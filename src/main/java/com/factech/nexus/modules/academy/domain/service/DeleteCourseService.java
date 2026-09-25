package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.DeleteCourseRequest;
import com.factech.nexus.modules.academy.domain.models.Course;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.CategoryRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.MembershipRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ModuleRow;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ProductRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.RecommendedCourseRow;
import com.factech.nexus.modules.academy.domain.repository.CourseRepository;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.audit.DeletionReason;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-013`: retirar un curso, <b>con arrastre</b>.
 *
 * <p>El retiro de la categoría con un sexto paso: motivo <b>antes de cualquier consulta</b>, el
 * curso <b>en cualquier estado</b> y bloqueado para distinguir «no existe» de «ya está retirado»,
 * la instantánea antes de marcar —<b>con los identificadores de sus categorías, membresías,
 * servicios, recomendados y módulos</b>—, la marca sin tocar nada más —ni el estado—, y <b>el
 * arrastre</b> por {@link CourseTreeRetirement}: sus módulos y lecciones vivos, con el mismo
 * instante y el mismo motivo, una fila de auditoría cada uno (`RN-AC-018`). Las relaciones
 * <b>permanecen</b>.
 */
@Service
public class DeleteCourseService {

  private final CourseRepository cursos;
  private final CourseQueryRepository consultas;
  private final CourseTreeRetirement arrastre;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public DeleteCourseService(
      CourseRepository cursos,
      CourseQueryRepository consultas,
      CourseTreeRetirement arrastre,
      AuditWriter auditoria) {
    this(cursos, consultas, arrastre, auditoria, Clock.systemUTC());
  }

  DeleteCourseService(
      CourseRepository cursos,
      CourseQueryRepository consultas,
      CourseTreeRetirement arrastre,
      AuditWriter auditoria,
      Clock reloj) {
    this.cursos = cursos;
    this.consultas = consultas;
    this.arrastre = arrastre;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public void delete(UUID id, DeleteCourseRequest peticion) {
    DeletionReason motivo = new DeletionReason(peticion == null ? null : peticion.reason());

    Course curso =
        cursos
            .findByIdForUpdate(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un curso con ese identificador."));
    if (curso.estaRetirado()) {
      String mensaje = "El curso ya está retirado.";
      throw new BusinessRuleException(
          "EX-002", mensaje, List.of(new FieldError("id", "EX-002", mensaje)));
    }

    Map<String, Object> instantanea = new LinkedHashMap<>(curso.instantanea());
    instantanea.put(
        "category_ids",
        consultas.findCategoriesOf(curso.getId()).stream()
            .map(CategoryRef::id)
            .map(UUID::toString)
            .toList());
    instantanea.put(
        "membership_ids",
        consultas.findMembershipsOf(curso.getId()).stream()
            .map(MembershipRef::id)
            .map(UUID::toString)
            .toList());
    instantanea.put(
        "product_ids",
        consultas.findProductsOf(curso.getId()).stream()
            .map(ProductRef::id)
            .map(UUID::toString)
            .toList());
    instantanea.put(
        "recommended_course_ids",
        consultas.findRecommendedOf(curso.getId()).stream()
            .map(RecommendedCourseRow::id)
            .map(UUID::toString)
            .toList());
    instantanea.put(
        "module_ids",
        consultas.findModulesOf(curso.getId()).stream()
            .filter(modulo -> !modulo.retirado())
            .map(ModuleRow::id)
            .map(UUID::toString)
            .toList());

    OffsetDateTime ahora = OffsetDateTime.now(reloj);
    curso.delete(ahora);

    auditoria.recordDeletion(
        new DeletionEvent(
            CourseDetailReader.MODULO,
            CourseDetailReader.ENTIDAD,
            curso.getId(),
            DeletionType.LOGICAL,
            motivo.value(),
            instantanea));

    arrastre.retirarArbolDe(curso.getId(), motivo.value(), ahora);
  }
}
