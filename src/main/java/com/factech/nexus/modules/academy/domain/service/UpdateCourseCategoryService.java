package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseCategoryDetailResponse;
import com.factech.nexus.modules.academy.application.UpdateCourseCategoryRequest;
import com.factech.nexus.modules.academy.domain.models.CourseCategory;
import com.factech.nexus.modules.academy.domain.repository.CourseCategoryRepository;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.patch.Patchable;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-004`: corregir nombre, descripción, color, icono y orden de una categoría.
 *
 * <p><b>Sin inmutables y sin la regla del icono</b>: los cinco campos se corrigen, y solo la
 * descripción admite vaciarse. Las validaciones de forma se devuelven <b>juntas</b> y <b>antes de
 * cualquier consulta</b> (`CA-AC-022`); el color se normaliza antes de comparar, de modo que un
 * cambio de caja no es un cambio y no se audita (`CA-AC-026`).
 */
@Service
public class UpdateCourseCategoryService {

  private static final Pattern COLOR = Pattern.compile("^[0-9A-Fa-f]{6}$");
  private static final Pattern ICONO = Pattern.compile("^[a-z][a-z0-9-]*$");

  private final CourseCategoryRepository categorias;
  private final AuditWriter auditoria;
  private final CourseCategoryDetailReader detalle;
  private final Clock reloj;

  @Autowired
  public UpdateCourseCategoryService(
      CourseCategoryRepository categorias,
      AuditWriter auditoria,
      CourseCategoryDetailReader detalle) {
    this(categorias, auditoria, detalle, Clock.systemUTC());
  }

  UpdateCourseCategoryService(
      CourseCategoryRepository categorias,
      AuditWriter auditoria,
      CourseCategoryDetailReader detalle,
      Clock reloj) {
    this.categorias = categorias;
    this.auditoria = auditoria;
    this.detalle = detalle;
    this.reloj = reloj;
  }

  @Transactional
  public CourseCategoryDetailResponse update(UUID id, UpdateCourseCategoryRequest peticion) {
    verificarFormato(peticion);

    CourseCategory categoria =
        categorias
            .findAliveByIdForUpdate(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-002", "No existe una categoría viva con ese identificador."));

    if (peticion.name().presente()) {
      String nombre = peticion.name().valor().trim();
      if (categorias.existsAliveNameForOther(nombre, categoria.getId())) {
        String mensaje = "Ya existe una categoría con ese nombre.";
        throw new BusinessRuleException(
            "EX-001", mensaje, List.of(new FieldError("name", "EX-001", mensaje)));
      }
    }

    Map<String, Object> cambios =
        categoria.update(
            peticion.name(),
            peticion.description(),
            peticion.color(),
            peticion.icon(),
            peticion.displayOrder(),
            OffsetDateTime.now(reloj));

    if (!cambios.isEmpty()) {
      // El volcado explícito convierte una carrera sobre el nombre en el `409`
      // traducido, y no en un fallo al confirmar fuera de este método.
      categorias.flush();
      auditoria.recordChange(
          new ChangeEvent(
              CourseCategoryDetailReader.MODULO,
              CourseCategoryDetailReader.ENTIDAD,
              categoria.getId(),
              ChangeAction.UPDATE,
              cambios));
    }
    return detalle.leer(categoria.getId());
  }

  /**
   * `VAL-002` a `VAL-006`, <b>juntas</b>: nombre, color, icono y orden no admiten vaciarse —son
   * obligatorios en la columna, y «bórralo» no tiene ningún estado al que llevar la categoría— y
   * tiene que venir al menos uno de los cinco. La descripción sí se vacía.
   */
  private static void verificarFormato(UpdateCourseCategoryRequest peticion) {
    List<FieldError> problemas = new ArrayList<>();
    if (!peticion.informaAlgo()) {
      problemas.add(
          new FieldError(
              "body", "VAL-006", "Debe informar al menos uno de los campos corregibles."));
    }
    if (peticion.name().presente()) {
      String nombre = peticion.name().valor();
      if (nombre == null || nombre.isBlank() || nombre.trim().length() > 150) {
        problemas.add(
            new FieldError(
                "name",
                "VAL-002",
                "El nombre de la categoría no puede quedar vacío ni superar los 150 caracteres."));
      }
    }
    if (peticion.color().presente()) {
      String color = peticion.color().valor();
      if (color == null || !COLOR.matcher(color.trim()).matches()) {
        problemas.add(
            new FieldError(
                "color",
                "VAL-003",
                "El color de la categoría no puede quedar vacío y debe ser seis dígitos"
                    + " hexadecimales sin el símbolo #."));
      }
    }
    if (peticion.icon().presente()) {
      String icono = peticion.icon().valor();
      if (icono == null || icono.trim().length() > 50 || !ICONO.matcher(icono.trim()).matches()) {
        problemas.add(
            new FieldError(
                "icon",
                "VAL-004",
                "El icono de la categoría no puede quedar vacío, solo admite minúsculas, dígitos y"
                    + " guion medio, debe empezar por letra y no puede exceder 50 caracteres."));
      }
    }
    if (peticion.displayOrder().presente()) {
      Integer orden = peticion.displayOrder().valor();
      if (orden == null || orden < 0) {
        problemas.add(
            new FieldError(
                "displayOrder",
                "VAL-005",
                "El orden de la categoría no puede quedar vacío y debe ser un entero mayor o igual"
                    + " que cero."));
      }
    }
    Patchable<String> descripcion = peticion.description();
    if (descripcion.presente()
        && descripcion.valor() != null
        && descripcion.valor().trim().length() > 1000) {
      problemas.add(
          new FieldError(
              "description", "VAL-002", "La descripción no puede exceder 1000 caracteres."));
    }
    if (!problemas.isEmpty()) {
      throw new ValidationException(
          problemas.get(0).code(),
          problemas.size() == 1
              ? problemas.get(0).message()
              : "La petición trae " + problemas.size() + " campos inválidos.",
          problemas);
    }
  }
}
