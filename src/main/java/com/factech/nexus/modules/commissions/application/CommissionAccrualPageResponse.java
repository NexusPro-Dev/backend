package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** La página de desenlaces, que es {@link PageResponse} más el orden aplicado (`RF-CM-014`). */
@Schema(name = "CommissionAccrualPageResponse")
public record CommissionAccrualPageResponse(
    List<CommissionAccrualItem> content,
    long totalElements,
    int totalPages,
    int page,
    int size,
    boolean totalIsExact,
    String sort) {

  public static CommissionAccrualPageResponse de(
      PageResponse<CommissionAccrualItem> pagina, String orden) {
    return new CommissionAccrualPageResponse(
        pagina.content(),
        pagina.totalElements(),
        pagina.totalPages(),
        pagina.page(),
        pagina.size(),
        pagina.totalIsExact(),
        orden);
  }
}
