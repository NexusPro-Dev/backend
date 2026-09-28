package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** La página de lotes, que es {@link PageResponse} más el orden aplicado (`RF-CM-010`). */
@Schema(name = "CommissionBatchPageResponse")
public record CommissionBatchPageResponse(
    List<CommissionBatchItem> content,
    long totalElements,
    int totalPages,
    int page,
    int size,
    boolean totalIsExact,
    String sort) {

  public static CommissionBatchPageResponse de(
      PageResponse<CommissionBatchItem> pagina, String orden) {
    return new CommissionBatchPageResponse(
        pagina.content(),
        pagina.totalElements(),
        pagina.totalPages(),
        pagina.page(),
        pagina.size(),
        pagina.totalIsExact(),
        orden);
  }
}
