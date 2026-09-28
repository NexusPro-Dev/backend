package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** La página de cierres, que es {@link PageResponse} más el orden aplicado (`RF-CM-009`). */
@Schema(name = "CommissionClosingPageResponse")
public record CommissionClosingPageResponse(
    List<CommissionClosingResponse> content,
    long totalElements,
    int totalPages,
    int page,
    int size,
    boolean totalIsExact,
    String sort) {

  public static CommissionClosingPageResponse de(
      PageResponse<CommissionClosingResponse> pagina, String orden) {
    return new CommissionClosingPageResponse(
        pagina.content(),
        pagina.totalElements(),
        pagina.totalPages(),
        pagina.page(),
        pagina.size(),
        pagina.totalIsExact(),
        orden);
  }
}
