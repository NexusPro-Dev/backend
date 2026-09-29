package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** La página de escalones de rol, con la forma envuelta de siempre y el orden aplicado. */
@Schema(name = "AfftrackRatePage")
public record AfftrackRatePageResponse(
    List<AfftrackRateItem> content,
    long totalElements,
    int totalPages,
    int page,
    int size,
    boolean totalIsExact,
    String sort) {

  public static AfftrackRatePageResponse de(PageResponse<AfftrackRateItem> pagina, String orden) {
    return new AfftrackRatePageResponse(
        pagina.content(),
        pagina.totalElements(),
        pagina.totalPages(),
        pagina.page(),
        pagina.size(),
        pagina.totalIsExact(),
        orden);
  }
}
