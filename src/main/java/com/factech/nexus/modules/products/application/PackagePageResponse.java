package com.factech.nexus.modules.products.application;

import com.factech.nexus.shared.pagination.PageResponse;
import java.util.List;

/** La página del listado de paquetes (`RF-PM-018`), con la envoltura del sistema. */
public record PackagePageResponse(
    List<PackageItemSummary> content,
    long totalElements,
    int totalPages,
    int page,
    int size,
    boolean totalIsExact,
    String sort) {

  public static PackagePageResponse de(PageResponse<PackageItemSummary> pagina, String orden) {
    return new PackagePageResponse(
        pagina.content(),
        pagina.totalElements(),
        pagina.totalPages(),
        pagina.page(),
        pagina.size(),
        pagina.totalIsExact(),
        orden);
  }
}
