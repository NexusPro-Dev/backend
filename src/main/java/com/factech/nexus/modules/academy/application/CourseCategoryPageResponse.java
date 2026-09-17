package com.factech.nexus.modules.academy.application;

import com.factech.nexus.shared.pagination.PageResponse;
import java.util.List;

/** La página del listado de categorías (`RF-AC-002`), con la envoltura del sistema. */
public record CourseCategoryPageResponse(
    List<CourseCategoryItem> content,
    long totalElements,
    int totalPages,
    int page,
    int size,
    boolean totalIsExact,
    String sort) {

  public static CourseCategoryPageResponse de(
      PageResponse<CourseCategoryItem> pagina, String orden) {
    return new CourseCategoryPageResponse(
        pagina.content(),
        pagina.totalElements(),
        pagina.totalPages(),
        pagina.page(),
        pagina.size(),
        pagina.totalIsExact(),
        orden);
  }
}
