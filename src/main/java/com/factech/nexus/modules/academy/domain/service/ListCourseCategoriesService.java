package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseCategoryItem;
import com.factech.nexus.modules.academy.application.CourseCategoryPageResponse;
import com.factech.nexus.modules.academy.application.CourseCategorySortField;
import com.factech.nexus.modules.academy.application.ListCourseCategoriesRequest;
import com.factech.nexus.modules.academy.domain.repository.CourseCategoryQueryRepository;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-002`: el listado de categorías, en el orden en que se enseñan.
 *
 * <p><b>Dos sentencias fijas</b> (`CA-AC-014`): la página, con la cuenta de cursos como columna, y
 * el total. Los `400` de paginación y orden se devuelven <b>juntos</b> (`CA-AC-015`); la forma de
 * {@code includeDeleted} la rechaza antes el editor canónico de {@code shared/error}.
 */
@Service
public class ListCourseCategoriesService {

  private final CourseCategoryQueryRepository consultas;
  private final Pagination paginacion;

  public ListCourseCategoriesService(
      CourseCategoryQueryRepository consultas, Pagination paginacion) {
    this.consultas = consultas;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public CourseCategoryPageResponse list(ListCourseCategoriesRequest filtros) {
    List<FieldError> problemas = new ArrayList<>();
    Pagination.Slice trozo = resolverPaginacion(filtros, problemas);
    CourseCategorySortField.Orden orden = resolverOrden(filtros, problemas);
    if (!problemas.isEmpty()) {
      throw new ValidationException(problemas.get(0).code(), resumen(problemas), problemas);
    }

    List<CourseCategoryItem> pagina =
        consultas.search(filtros, orden.sql(), trozo.offset(), trozo.size()).stream()
            .map(CourseCategoryItem::from)
            .toList();

    return CourseCategoryPageResponse.de(
        PageResponse.de(pagina, consultas.count(filtros), trozo.page(), trozo.size()),
        orden.publico());
  }

  private Pagination.Slice resolverPaginacion(
      ListCourseCategoriesRequest filtros, List<FieldError> problemas) {
    try {
      return paginacion.resolver(filtros.page(), filtros.size());
    } catch (ValidationException fallo) {
      fallo.errors().stream()
          .map(error -> new FieldError(error.field(), "VAL-001", error.message()))
          .forEach(problemas::add);
      return new Pagination.Slice(0, 1);
    }
  }

  private static CourseCategorySortField.Orden resolverOrden(
      ListCourseCategoriesRequest filtros, List<FieldError> problemas) {
    try {
      return CourseCategorySortField.resolver(filtros.sort());
    } catch (ValidationException fallo) {
      problemas.addAll(fallo.errors());
      return CourseCategorySortField.resolver(null);
    }
  }

  private static String resumen(List<FieldError> problemas) {
    return problemas.size() == 1
        ? problemas.get(0).message()
        : "La consulta trae " + problemas.size() + " parámetros inválidos.";
  }
}
