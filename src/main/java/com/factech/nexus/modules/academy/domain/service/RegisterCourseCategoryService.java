package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseCategoryDetailResponse;
import com.factech.nexus.modules.academy.application.RegisterCourseCategoryRequest;
import com.factech.nexus.modules.academy.domain.models.CourseCategory;
import com.factech.nexus.modules.academy.domain.repository.CourseCategoryRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-001`: registrar una categoría.
 *
 * <p><b>El alta del paquete sin código, sin moneda y sin estado.</b> Nombre contra las vivas,
 * inserción, auditoría, y la relectura del detalle con su forma completa: sobre una categoría
 * recién creada, cero cursos y portada nula. No consume nada de `SP`.
 */
@Service
public class RegisterCourseCategoryService {

  private final CourseCategoryRepository categorias;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final CourseCategoryDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public RegisterCourseCategoryService(
      CourseCategoryRepository categorias,
      AuditWriter auditoria,
      UuidV7Generator ids,
      CourseCategoryDetailReader detalle) {
    this(categorias, auditoria, ids, detalle, Clock.systemUTC());
  }

  RegisterCourseCategoryService(
      CourseCategoryRepository categorias,
      AuditWriter auditoria,
      UuidV7Generator ids,
      CourseCategoryDetailReader detalle,
      Clock reloj) {
    this.categorias = categorias;
    this.auditoria = auditoria;
    this.ids = ids;
    this.detalle = detalle;
    this.reloj = reloj;
  }

  @Transactional
  public CourseCategoryDetailResponse register(RegisterCourseCategoryRequest peticion) {
    // Solo contra las VIVAS (`EX-001`): una retirada libera el nombre. La red
    // es el índice parcial, que muerde en el INSERT y sale con el mismo código.
    if (peticion.name() != null && categorias.existsAliveName(peticion.name())) {
      String mensaje = "Ya existe una categoría con ese nombre.";
      throw new BusinessRuleException(
          "EX-001", mensaje, List.of(new FieldError("name", "EX-001", mensaje)));
    }

    CourseCategory nueva =
        categorias.save(
            CourseCategory.create(
                ids.next(),
                peticion.name(),
                peticion.description(),
                peticion.color(),
                peticion.icon(),
                peticion.displayOrder(),
                OffsetDateTime.now(reloj)));

    // La instantánea la arma el agregado, y es la misma que usa el retiro
    // (`RF-AC-005`): el registro de creación y el de eliminación describen la
    // misma categoría con las mismas claves.
    auditoria.recordChange(
        new ChangeEvent(
            CourseCategoryDetailReader.MODULO,
            CourseCategoryDetailReader.ENTIDAD,
            nueva.getId(),
            ChangeAction.CREATE,
            nueva.instantanea()));

    return detalle.leer(nueva.getId());
  }
}
