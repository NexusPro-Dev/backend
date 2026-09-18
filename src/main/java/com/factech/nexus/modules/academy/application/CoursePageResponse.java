package com.factech.nexus.modules.academy.application;

import com.factech.nexus.shared.pagination.PageResponse;
import java.util.List;

/** La página del listado de cursos (`RF-AC-009`), con la envoltura del sistema. */
public record CoursePageResponse(
    List<CourseItem> content,
    long totalElements,
    int totalPages,
    int page,
    int size,
    boolean totalIsExact,
    String sort) {

  public static CoursePageResponse de(PageResponse<CourseItem> pagina, String orden) {
    return new CoursePageResponse(
        pagina.content(),
        pagina.totalElements(),
        pagina.totalPages(),
        pagina.page(),
        pagina.size(),
        pagina.totalIsExact(),
        orden);
  }
}
