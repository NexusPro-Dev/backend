package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseDetailResponse;
import com.factech.nexus.modules.academy.domain.models.Course;
import com.factech.nexus.modules.academy.domain.models.CourseCategoryItem;
import com.factech.nexus.modules.academy.domain.repository.CourseCategoryItemRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseCategoryQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseCategoryQueryRepository.CourseCategoryRow;
import com.factech.nexus.modules.academy.domain.repository.CourseRepository;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-017`: sacar un curso de una categoría.
 *
 * <p>{@code DissociatePackageProductService} con otra fila: <b>sin motivo, borrado físico, {@code
 * ASSOCIATION}</b> (Art. V.13). <b>Responde el curso</b> y no {@code 204}, porque quien acaba de
 * quitar quiere repintarlo. <b>Una categoría retirada se desclasifica igual</b> (`FA-001`): su fila
 * existe, y es la forma de limpiar lo que el retiro dejó.
 *
 * <p>El bloqueo del curso ordena dos desclasificaciones de la misma pareja: la segunda ya no
 * encuentra la fila y responde `404` sin auditar (`CA-AC-134`).
 */
@Service
public class DeclassifyCourseService {

  private final CourseRepository cursos;
  private final CourseCategoryItemRepository filas;
  private final CourseCategoryQueryRepository categorias;
  private final AuditWriter auditoria;
  private final CourseDetailReader detalle;

  public DeclassifyCourseService(
      CourseRepository cursos,
      CourseCategoryItemRepository filas,
      CourseCategoryQueryRepository categorias,
      AuditWriter auditoria,
      CourseDetailReader detalle) {
    this.cursos = cursos;
    this.filas = filas;
    this.categorias = categorias;
    this.auditoria = auditoria;
    this.detalle = detalle;
  }

  @Transactional
  public CourseDetailResponse declassify(UUID courseId, UUID categoryId) {
    Course curso = ClassifyCourseService.cursoVivo(cursos, courseId);
    CourseCategoryItem fila =
        filas
            .find(curso.getId(), categoryId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-002", "El curso no está clasificado en esa categoría."));

    // La instantánea ANTES de borrar, con el nombre que la categoría tiene hoy
    // —viva o retirada: la clave foránea garantiza que existe—.
    String nombre = categorias.findDetail(categoryId).map(CourseCategoryRow::name).orElse(null);
    Map<String, Object> instantanea = fila.instantanea(nombre);

    filas.delete(fila);

    // `ASSOCIATION` admite motivo nulo (`ck_deletion_reason`).
    auditoria.recordDeletion(
        new DeletionEvent(
            CourseDetailReader.MODULO,
            ClassifyCourseService.ENTIDAD_FILA,
            curso.getId(),
            DeletionType.ASSOCIATION,
            null,
            instantanea));

    return detalle.leer(curso.getId());
  }
}
