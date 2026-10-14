package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.LiveSessionCategoryRequests.Delete;
import com.factech.nexus.modules.academy.application.LiveSessionCategoryRequests.Register;
import com.factech.nexus.modules.academy.application.LiveSessionCategoryRequests.Update;
import com.factech.nexus.modules.academy.application.LiveSessionCategoryResponse;
import com.factech.nexus.modules.academy.domain.models.LiveSessionCategory;
import com.factech.nexus.modules.academy.domain.repository.LiveSessionCategoryRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.audit.DeletionReason;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso `RF-AC-063` a `RF-AC-066`: consultar, registrar, corregir y retirar las categorías
 * de las clases en vivo (`RN-AC-034`).
 *
 * <p>Los de {@code CourseCategory} en pequeño: el nombre contra las vivas antes de escribir y la
 * carrera traducida al mismo {@code 409}; corregir solo una viva y auditar solo lo que cambió;
 * retirar con motivo, distinguiendo «no existe» de «ya retirada», <b>sin tocar las clases</b>
 * (`RN-AC-018`).
 */
@Service
public class LiveSessionCategoryService {

  static final String MODULO = "AC";
  static final String ENTIDAD = "live_session_categories";

  private final LiveSessionCategoryRepository categorias;
  private final AuditWriter auditoria;
  private final UuidV7Generator ids;
  private final Clock reloj;

  public LiveSessionCategoryService(
      LiveSessionCategoryRepository categorias, AuditWriter auditoria, UuidV7Generator ids) {
    this.categorias = categorias;
    this.auditoria = auditoria;
    this.ids = ids;
    this.reloj = Clock.systemUTC();
  }

  /** `RF-AC-063`: las vivas, por orden, nombre e identificador. */
  @Transactional(readOnly = true)
  public List<LiveSessionCategoryResponse> list() {
    return categorias.findAlive().stream().map(LiveSessionCategoryResponse::from).toList();
  }

  /** `RF-AC-064`. */
  @Transactional
  public LiveSessionCategoryResponse register(Register peticion) {
    String nombre = LiveSessionCategory.verificarNombre(peticion == null ? null : peticion.name());
    int orden = LiveSessionCategory.verificarOrden(peticion.displayOrder());
    if (categorias.existsAliveName(nombre, null)) {
      throw repetido("EX-001");
    }
    LiveSessionCategory nueva =
        categorias.save(
            LiveSessionCategory.create(ids.next(), nombre, orden, OffsetDateTime.now(reloj)),
            "EX-001");
    auditoria.recordChange(
        new ChangeEvent(MODULO, ENTIDAD, nueva.getId(), ChangeAction.CREATE, nueva.instantanea()));
    return LiveSessionCategoryResponse.from(nueva);
  }

  /** `RF-AC-065`: solo una viva; nombre y orden no se vacían. */
  @Transactional
  public LiveSessionCategoryResponse update(UUID id, Update peticion) {
    if (peticion == null || !peticion.informaAlgo()) {
      String mensaje = "Debe informar al menos uno de los campos corregibles.";
      throw new ValidationException(
          "VAL-003", mensaje, List.of(new FieldError("body", "VAL-003", mensaje)));
    }
    if (peticion.name().presente()) {
      LiveSessionCategory.verificarNombre(peticion.name().valor());
    }
    if (peticion.displayOrder().presente()) {
      LiveSessionCategory.verificarOrden(peticion.displayOrder().valor());
    }
    LiveSessionCategory categoria =
        categorias
            .findByIdForUpdate(id)
            .filter(c -> !c.estaRetirada())
            .orElseThrow(LiveSessionCategoryService::noExiste);
    if (peticion.name().presente()
        && categorias.existsAliveName(peticion.name().valor().trim(), categoria.getId())) {
      throw repetido("EX-002");
    }
    Map<String, Object> cambios =
        categoria.update(peticion.name(), peticion.displayOrder(), OffsetDateTime.now(reloj));
    if (!cambios.isEmpty()) {
      categorias.flush("EX-002");
      auditoria.recordChange(
          new ChangeEvent(MODULO, ENTIDAD, categoria.getId(), ChangeAction.UPDATE, cambios));
    }
    return LiveSessionCategoryResponse.from(categoria);
  }

  /** `RF-AC-066`: baja lógica con motivo; las clases que la tienen la conservan. */
  @Transactional
  public void delete(UUID id, Delete peticion) {
    DeletionReason motivo = new DeletionReason(peticion == null ? null : peticion.reason());
    LiveSessionCategory categoria =
        categorias.findByIdForUpdate(id).orElseThrow(LiveSessionCategoryService::noExiste);
    if (categoria.estaRetirada()) {
      String mensaje = "La categoría ya está retirada.";
      throw new BusinessRuleException(
          "EX-002", mensaje, List.of(new FieldError("id", "EX-002", mensaje)));
    }
    Map<String, Object> instantanea = categoria.instantanea();
    categoria.delete(OffsetDateTime.now(reloj));
    auditoria.recordDeletion(
        new DeletionEvent(
            MODULO, ENTIDAD, categoria.getId(), DeletionType.LOGICAL, motivo.value(), instantanea));
  }

  private static ResourceNotFoundException noExiste() {
    return new ResourceNotFoundException(
        "EX-001", "No existe una categoría de clases en vivo con ese identificador.");
  }

  private static BusinessRuleException repetido(String codigo) {
    String mensaje = "Ya existe una categoría con ese nombre.";
    return new BusinessRuleException(
        codigo, mensaje, List.of(new FieldError("name", codigo, mensaje)));
  }
}
