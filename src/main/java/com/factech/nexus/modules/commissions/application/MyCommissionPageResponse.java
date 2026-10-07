package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** La página de mis comisiones, que es {@link PageResponse} más el orden aplicado (`RF-CM-026`). */
@Schema(name = "MyCommissionPageResponse")
public record MyCommissionPageResponse(
    List<MyCommissionItem> content,
    long totalElements,
    int totalPages,
    int page,
    int size,
    boolean totalIsExact,
    String sort) {

  public static MyCommissionPageResponse de(PageResponse<MyCommissionItem> pagina, String orden) {
    return new MyCommissionPageResponse(
        pagina.content(),
        pagina.totalElements(),
        pagina.totalPages(),
        pagina.page(),
        pagina.size(),
        pagina.totalIsExact(),
        orden);
  }
}
