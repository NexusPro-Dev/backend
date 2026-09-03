package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.shared.pagination.PageResponse;
import java.util.List;

/**
 * La página del listado de tasas personalizadas, que es {@link PageResponse} más el orden aplicado.
 *
 * <p>Se construye <b>desde</b> {@link PageResponse} y no en paralelo, por lo mismo que en `PM`: el
 * cálculo del total de páginas vive en un solo sitio.
 */
public record UserCommissionRatePageResponse(
    List<UserCommissionRateItem> content,
    long totalElements,
    int totalPages,
    int page,
    int size,
    boolean totalIsExact,
    String sort) {

  public static UserCommissionRatePageResponse de(
      PageResponse<UserCommissionRateItem> pagina, String orden) {
    return new UserCommissionRatePageResponse(
        pagina.content(),
        pagina.totalElements(),
        pagina.totalPages(),
        pagina.page(),
        pagina.size(),
        pagina.totalIsExact(),
        orden);
  }
}
