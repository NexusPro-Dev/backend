package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.DeleteCourseCategoryRequest;
import com.factech.nexus.modules.academy.domain.models.CourseCategory;
import com.factech.nexus.modules.academy.domain.repository.CourseCategoryQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseCategoryQueryRepository.CategoryCourseRow;
import com.factech.nexus.modules.academy.domain.repository.CourseCategoryRepository;
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
 * Caso de uso `RF-AC-005`: retirar una categoría.
 *
 * <p>El retiro del paquete (`RF-PM-022`) con otra tabla y una instantánea más estrecha: motivo
 * <b>antes de cualquier consulta</b>, la categoría <b>en cualquier estado</b> y bloqueada para
 * distinguir «no existe» de «ya está retirada», la instantánea antes de marcar —<b>con los
 * identificadores de sus cursos vivos</b>, que es lo que el registro tiene que poder decir que
 * contenía—, y la marca sin tocar nada más. <b>No arrastra nada ni se rechaza por tener cursos</b>
 * (`RN-AC-018`): la categoría es un filtro, y sus clasificaciones permanecen.
 *
 * <p>Es el primer uso de {@link DeletionReason} fuera de `PM`: el tercer cliente que su propio
 * Javadoc esperaba para promoverlo a {@code shared/}.
 */
@Service
public class DeleteCourseCategoryService {

  private final CourseCategoryRepository categorias;
  private final CourseCategoryQueryRepository consultas;
  private final AuditWriter auditoria;
  private final Clock reloj;

  @Autowired
  public DeleteCourseCategoryService(
      CourseCategoryRepository categorias,
      CourseCategoryQueryRepository consultas,
      AuditWriter auditoria) {
    this(categorias, consultas, auditoria, Clock.systemUTC());
  }

  DeleteCourseCategoryService(
      CourseCategoryRepository categorias,
      CourseCategoryQueryRepository consultas,
      AuditWriter auditoria,
      Clock reloj) {
    this.categorias = categorias;
    this.consultas = consultas;
    this.auditoria = auditoria;
    this.reloj = reloj;
  }

  @Transactional
  public void delete(UUID id, DeleteCourseCategoryRequest peticion) {
    DeletionReason motivo = new DeletionReason(peticion == null ? null : peticion.reason());

    CourseCategory categoria =
        categorias
            .findByIdForUpdate(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe una categoría con ese identificador."));
    // `EX-002`, y se distingue de `EX-001` a propósito: el catálogo devuelve las
    // retiradas a quien tiene `course-categories:read`, y quien retira dos
    // veces merece saber que la primera funcionó.
    if (categoria.estaRetirada()) {
      String mensaje = "La categoría ya está retirada.";
      throw new BusinessRuleException(
          "EX-002", mensaje, List.of(new FieldError("id", "EX-002", mensaje)));
    }

    Map<String, Object> instantanea = new LinkedHashMap<>(categoria.instantanea());
    instantanea.put(
        "course_ids",
        consultas.findAliveCoursesOf(categoria.getId()).stream()
            .map(CategoryCourseRow::id)
            .map(UUID::toString)
            .toList());

    categoria.delete(OffsetDateTime.now(reloj));

    auditoria.recordDeletion(
        new DeletionEvent(
            CourseCategoryDetailReader.MODULO,
            CourseCategoryDetailReader.ENTIDAD,
            categoria.getId(),
            DeletionType.LOGICAL,
            motivo.value(),
            instantanea));
  }
}
