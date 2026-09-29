package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.shared.pagination.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** La página de escalones de persona, con la forma envuelta de siempre y el orden aplicado. */
@Schema(name = "UserAfftrackRatePage")
public record UserAfftrackRatePageResponse(
    List<UserAfftrackRateItem> content,
    long totalElements,
    int totalPages,
    int page,
    int size,
    boolean totalIsExact,
    String sort) {

  public static UserAfftrackRatePageResponse de(
      PageResponse<UserAfftrackRateItem> pagina, String orden) {
    return new UserAfftrackRatePageResponse(
        pagina.content(),
        pagina.totalElements(),
        pagina.totalPages(),
        pagina.page(),
        pagina.size(),
        pagina.totalIsExact(),
        orden);
  }
}
