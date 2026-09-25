package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseDetailResponse;
import com.factech.nexus.modules.academy.application.RegisterCourseRequest;
import com.factech.nexus.modules.academy.domain.models.Course;
import com.factech.nexus.modules.academy.domain.models.CourseCategory;
import com.factech.nexus.modules.academy.domain.repository.CourseCategoryRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseRepository;
import com.factech.nexus.modules.academy.domain.repository.JpaCourseRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.UnprocessableEntityException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-008`: registrar un curso.
 *
 * <p><b>El alta de la categoría con una entidad más ancha, dos puertos de `SP` y un objeto de
 * ofrecibilidad por delante.</b> Título contra los vivos, el instructor por {@link
 * InstructorVerifier} —existe, no retirado, porta {@code courses:teach}—, inserción en {@code
 * INACTIVO}, auditoría, y la relectura del detalle con su forma completa: listas vacías, portada
 * nula y {@code offerable: false} diciendo «inactivo». Cinco sentencias y una más para releer.
 */
@Service
public class RegisterCourseService {

  private final CourseRepository cursos;
  private final CourseCategoryRepository categorias;
  private final CourseClassifier clasificador;
  private final InstructorVerifier instructor;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final CourseDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public RegisterCourseService(
      CourseRepository cursos,
      CourseCategoryRepository categorias,
      CourseClassifier clasificador,
      InstructorVerifier instructor,
      AuditWriter auditoria,
      UuidV7Generator ids,
      CourseDetailReader detalle) {
    this(cursos, categorias, clasificador, instructor, auditoria, ids, detalle, Clock.systemUTC());
  }

  RegisterCourseService(
      CourseRepository cursos,
      CourseCategoryRepository categorias,
      CourseClassifier clasificador,
      InstructorVerifier instructor,
      AuditWriter auditoria,
      UuidV7Generator ids,
      CourseDetailReader detalle,
      Clock reloj) {
    this.cursos = cursos;
    this.categorias = categorias;
    this.clasificador = clasificador;
    this.instructor = instructor;
    this.auditoria = auditoria;
    this.ids = ids;
    this.detalle = detalle;
    this.reloj = reloj;
  }

  @Transactional
  public CourseDetailResponse register(RegisterCourseRequest peticion) {
    // `VAL-008`, antes de consultar nada: una lista con repetidas es un error de
    // forma del cliente, y se le dice sin gastar una sentencia.
    if (new HashSet<>(peticion.categoryIds()).size() != peticion.categoryIds().size()) {
      String mensaje = "La lista de categorías no puede traer identificadores repetidos ni vacíos.";
      throw new ValidationException(
          "VAL-008", mensaje, List.of(new FieldError("categoryIds", "VAL-008", mensaje)));
    }

    // Solo contra los VIVOS (`EX-001`): un retirado libera el título. La red
    // es el índice parcial, que muerde en el INSERT y sale con el mismo código.
    if (peticion.title() != null && cursos.existsAliveTitle(peticion.title())) {
      throw new BusinessRuleException(
          "EX-001",
          JpaCourseRepository.MENSAJE_TITULO,
          List.of(new FieldError("title", "EX-001", JpaCourseRepository.MENSAJE_TITULO)));
    }

    instructor.verificar(peticion.instructorId());

    // Las categorías ANTES de insertar nada (`EX-004`): el `422` no debe costar
    // un INSERT revertido, y se nombran TODAS las que fallan, no la primera.
    List<CourseCategory> cajones = categoriasVivas(peticion.categoryIds());

    Course nuevo =
        cursos.save(
            Course.create(
                ids.next(),
                peticion.title(),
                peticion.instructorId(),
                peticion.difficulty(),
                peticion.shortDescription(),
                peticion.longDescription(),
                peticion.introVideoUrl(),
                peticion.displayOrder(),
                OffsetDateTime.now(reloj)));

    auditoria.recordChange(
        new ChangeEvent(
            CourseDetailReader.MODULO,
            CourseDetailReader.ENTIDAD,
            nuevo.getId(),
            ChangeAction.CREATE,
            nuevo.instantanea()));

    // Las mismas filas y la misma auditoría que `RF-AC-016`: una puerta más, no
    // otra forma de clasificar. Sin bloquear el curso: acaba de nacer.
    for (CourseCategory cajon : cajones) {
      clasificador.clasificar(nuevo.getId(), cajon);
    }

    return detalle.leer(nuevo.getId());
  }

  private List<CourseCategory> categoriasVivas(List<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    List<CourseCategory> vivas = categorias.findAliveByIds(ids);
    Set<UUID> encontradas = vivas.stream().map(CourseCategory::getId).collect(Collectors.toSet());
    List<String> faltan =
        ids.stream().filter(id -> !encontradas.contains(id)).map(UUID::toString).toList();
    if (!faltan.isEmpty()) {
      String mensaje =
          "Estas categorías no existen o están retiradas: %s.".formatted(String.join(", ", faltan));
      throw new UnprocessableEntityException(
          "EX-004", mensaje, List.of(new FieldError("categoryIds", "EX-004", mensaje)));
    }
    return vivas;
  }
}
