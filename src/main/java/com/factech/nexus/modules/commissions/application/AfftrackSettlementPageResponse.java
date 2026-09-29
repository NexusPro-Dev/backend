package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** La página de liquidaciones afftrack, con la forma envuelta de siempre y el orden aplicado. */
@Schema(name = "AfftrackSettlementPage")
public record AfftrackSettlementPageResponse(
    List<AfftrackSettlementItem> content,
    long totalElements,
    int totalPages,
    int page,
    int size,
    boolean totalIsExact,
    String sort) {

  public static AfftrackSettlementPageResponse de(
      PageResponse<AfftrackSettlementItem> pagina, String orden) {
    return new AfftrackSettlementPageResponse(
        pagina.content(),
        pagina.totalElements(),
        pagina.totalPages(),
        pagina.page(),
        pagina.size(),
        pagina.totalIsExact(),
        orden);
  }
}
