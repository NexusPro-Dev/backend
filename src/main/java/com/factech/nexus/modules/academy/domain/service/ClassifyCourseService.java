package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.ClassifyCourseRequest;
import com.factech.nexus.modules.academy.application.CourseDetailResponse;
import com.factech.nexus.modules.academy.domain.models.Course;
import com.factech.nexus.modules.academy.domain.models.CourseCategory;
import com.factech.nexus.modules.academy.domain.models.CourseCategoryItem;
import com.factech.nexus.modules.academy.domain.repository.CourseCategoryItemRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseCategoryRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseRepository;
import com.factech.nexus.modules.academy.domain.repository.JpaCourseCategoryItemRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-016`: clasificar un curso en una categoría.
 *
 * <p>La asociación del paquete (`RF-PM-023`) sin descuento ni moneda: <b>el curso se bloquea y la
 * categoría no</b> (`spec.md` §14.1) —nada de ella cambia—, la pareja se comprueba bajo ese
 * bloqueo, y la clave primaria es la red de la carrera. <b>No toca el estado ni la
 * ofrecibilidad</b>: un curso {@code INACTIVO} se clasifica igual, y un curso sin categoría se
 * ofrece igual (`RN-AC-010`).
 *
 * <p><b>`EX-002` es `422` y no `404`</b>: la categoría viene en el cuerpo y no en la ruta, como la
 * moneda en `PM`.
 */
@Service
public class ClassifyCourseService {

  static final String ENTIDAD_FILA = "course_category_items";

  private final CourseRepository cursos;
  private final CourseCategoryRepository categorias;
  private final CourseCategoryItemRepository filas;
  private final AuditWriter auditoria;
  private final CourseDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public ClassifyCourseService(
      CourseRepository cursos,
      CourseCategoryRepository categorias,
      CourseCategoryItemRepository filas,
      AuditWriter auditoria,
      CourseDetailReader detalle) {
    this(cursos, categorias, filas, auditoria, detalle, Clock.systemUTC());
  }

  ClassifyCourseService(
      CourseRepository cursos,
      CourseCategoryRepository categorias,
      CourseCategoryItemRepository filas,
      AuditWriter auditoria,
      CourseDetailReader detalle,
      Clock reloj) {
    this.cursos = cursos;
    this.categorias = categorias;
    this.filas = filas;
    this.auditoria = auditoria;
    this.detalle = detalle;
    this.reloj = reloj;
  }

  @Transactional
  public CourseDetailResponse classify(UUID courseId, ClassifyCourseRequest peticion) {
    Course curso = cursoVivo(cursos, courseId);

    CourseCategory categoria =
        categorias
            .findAliveById(peticion.categoryId())
            .orElseThrow(
                () -> {
                  String mensaje = "La categoría indicada no existe o está retirada.";
                  return new UnprocessableEntityException(
                      "EX-002", mensaje, List.of(new FieldError("categoryId", "EX-002", mensaje)));
                });

    if (filas.find(curso.getId(), categoria.getId()).isPresent()) {
      throw JpaCourseCategoryItemRepository.yaClasificado(categoria.getName());
    }

    CourseCategoryItem fila =
        filas.save(
            CourseCategoryItem.create(curso.getId(), categoria.getId(), OffsetDateTime.now(reloj)),
            categoria.getName());

    // El `entity_id` es el del CURSO: la fila es suya y no tiene identificador,
    // y es por el curso por donde se pregunta «qué le hicieron».
    auditoria.recordChange(
        new ChangeEvent(
            CourseDetailReader.MODULO,
            ENTIDAD_FILA,
            curso.getId(),
            ChangeAction.CREATE,
            fila.instantanea(categoria.getName())));

    return detalle.leer(curso.getId());
  }

  /** El curso vivo, bloqueado, o el `404` que comparten las escrituras de sus relaciones. */
  static Course cursoVivo(CourseRepository cursos, UUID id) {
    return cursos
        .findAliveByIdForUpdate(id)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "EX-001", "No existe un curso vivo con ese identificador."));
  }
}
