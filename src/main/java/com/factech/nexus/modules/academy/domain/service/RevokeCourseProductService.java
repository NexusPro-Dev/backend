package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseDetailResponse;
import com.factech.nexus.modules.academy.domain.models.Course;
import com.factech.nexus.modules.academy.domain.models.CourseProduct;
import com.factech.nexus.modules.academy.domain.repository.CourseProductRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.ProductRef;
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
 * Caso de uso `RF-AC-038`: quitar la visibilidad de un curso a un servicio.
 *
 * <p>{@link DeclassifyCourseService} con otra fila: <b>sin motivo, borrado físico, {@code
 * ASSOCIATION}</b>, y el curso en la respuesta. <b>Quitar el último nunca se rechaza</b>: si
 * tampoco hay membresías, el curso deja de ofrecerse y el detalle lo dice. Un servicio retirado en
 * `PM` se quita igual. El código para la instantánea sale del mismo {@code JOIN} del detalle: no
 * hay regla que cruce el puerto.
 */
@Service
public class RevokeCourseProductService {

  private final CourseRepository cursos;
  private final CourseProductRepository filas;
  private final CourseQueryRepository consultas;
  private final AuditWriter auditoria;
  private final CourseDetailReader detalle;

  public RevokeCourseProductService(
      CourseRepository cursos,
      CourseProductRepository filas,
      CourseQueryRepository consultas,
      AuditWriter auditoria,
      CourseDetailReader detalle) {
    this.cursos = cursos;
    this.filas = filas;
    this.consultas = consultas;
    this.auditoria = auditoria;
    this.detalle = detalle;
  }

  @Transactional
  public CourseDetailResponse revoke(UUID courseId, UUID productId) {
    Course curso = ClassifyCourseService.cursoVivo(cursos, courseId);
    CourseProduct fila =
        filas
            .find(curso.getId(), productId)
            .orElseThrow(
                () -> new ResourceNotFoundException("EX-002", "Ese servicio no abre este curso."));

    // La instantánea ANTES de borrar: después el JOIN ya no encuentra la fila.
    String codigo =
        consultas.findProductsOf(curso.getId()).stream()
            .filter(p -> p.id().equals(productId))
            .map(ProductRef::code)
            .findFirst()
            .orElse(null);
    Map<String, Object> instantanea = fila.instantanea(codigo);

    filas.delete(fila);

    auditoria.recordDeletion(
        new DeletionEvent(
            CourseDetailReader.MODULO,
            GrantCourseProductService.ENTIDAD_FILA,
            curso.getId(),
            DeletionType.ASSOCIATION,
            null,
            instantanea));

    return detalle.leer(curso.getId());
  }
}
