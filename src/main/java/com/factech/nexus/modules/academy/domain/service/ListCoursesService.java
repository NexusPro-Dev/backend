package com.factech.nexus.modules.academy.domain.service;

import com.factech.nexus.modules.academy.application.CourseItem;
import com.factech.nexus.modules.academy.application.CoursePageResponse;
import com.factech.nexus.modules.academy.application.CourseSortField;
import com.factech.nexus.modules.academy.application.ListCoursesRequest;
import com.factech.nexus.modules.academy.domain.models.CourseDifficulty;
import com.factech.nexus.modules.academy.domain.models.CourseStatus;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.CategoryRef;
import com.factech.nexus.modules.academy.domain.repository.CourseQueryRepository.CourseRow;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso `RF-AC-009`: el listado de cursos, en el orden en que se enseñan.
 *
 * <p><b>Tres sentencias fijas</b> —la página con el instructor y las cuentas como columnas, el
 * total y, desde `RF-AC-016`, las categorías de toda la página en una—: el número no crece con el
 * tamaño de la página (`CA-AC-048`). Los `400` de paginación, orden, dificultad y estado se
 * devuelven <b>juntos</b> (`CA-AC-049`).
 */
@Service
public class ListCoursesService {

  private final CourseQueryRepository consultas;
  private final Pagination paginacion;

  public ListCoursesService(CourseQueryRepository consultas, Pagination paginacion) {
    this.consultas = consultas;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public CoursePageResponse list(ListCoursesRequest filtros) {
    List<FieldError> problemas = new ArrayList<>();
    Pagination.Slice trozo = resolverPaginacion(filtros, problemas);
    CourseSortField.Orden orden = resolverOrden(filtros, problemas);
    verificarDominio(filtros, problemas);
    if (!problemas.isEmpty()) {
      throw new ValidationException(problemas.get(0).code(), resumen(problemas), problemas);
    }

    List<CourseRow> filas = consultas.search(filtros, orden.sql(), trozo.offset(), trozo.size());
    Map<UUID, List<CategoryRef>> categorias =
        filas.isEmpty()
            ? Map.of()
            : consultas.findCategoriesOfCourses(filas.stream().map(CourseRow::id).toList());
    List<CourseItem> pagina =
        filas.stream()
            .map(fila -> CourseItem.from(fila, categorias.getOrDefault(fila.id(), List.of())))
            .toList();

    return CoursePageResponse.de(
        PageResponse.de(pagina, consultas.count(filtros), trozo.page(), trozo.size()),
        orden.publico());
  }

  private Pagination.Slice resolverPaginacion(
      ListCoursesRequest filtros, List<FieldError> problemas) {
    try {
      return paginacion.resolver(filtros.page(), filtros.size());
    } catch (ValidationException fallo) {
      fallo.errors().stream()
          .map(error -> new FieldError(error.field(), "VAL-001", error.message()))
          .forEach(problemas::add);
      return new Pagination.Slice(0, 1);
    }
  }

  private static CourseSortField.Orden resolverOrden(
      ListCoursesRequest filtros, List<FieldError> problemas) {
    try {
      return CourseSortField.resolver(filtros.sort());
    } catch (ValidationException fallo) {
      problemas.addAll(fallo.errors());
      return CourseSortField.resolver(null);
    }
  }

  /** Dificultad y estado son dominios cerrados (`VAL-003`); un valor de fuera es `400`, junto. */
  private static void verificarDominio(ListCoursesRequest filtros, List<FieldError> problemas) {
    if (filtros.difficulty() != null
        && Arrays.stream(CourseDifficulty.values())
            .noneMatch(valor -> valor.name().equals(filtros.difficulty()))) {
      problemas.add(
          new FieldError(
              "difficulty",
              "VAL-003",
              "La dificultad debe ser PRINCIPIANTE, INTERMEDIO o AVANZADO."));
    }
    if (filtros.status() != null
        && Arrays.stream(CourseStatus.values())
            .noneMatch(valor -> valor.name().equals(filtros.status()))) {
      problemas.add(new FieldError("status", "VAL-003", "El estado debe ser ACTIVO o INACTIVO."));
    }
  }

  private static String resumen(List<FieldError> problemas) {
    return problemas.size() == 1
        ? problemas.get(0).message()
        : "La consulta trae " + problemas.size() + " parámetros inválidos.";
  }
}
