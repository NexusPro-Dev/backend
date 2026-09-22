package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.shared.pagination.PageResponse;
import java.util.List;

/**
 * La pagina del listado de tarifas, que es {@link PageResponse} mas el orden aplicado.
 *
 * <p>Se construye <b>desde</b> {@link PageResponse} y no en paralelo, por lo mismo que en `PM`: el
 * calculo del total de paginas vive en un solo sitio.
 */
public record CommissionRatePageResponse(
    List<CommissionRateItem> content,
    long totalElements,
    int totalPages,
    int page,
    int size,
    boolean totalIsExact,
    String sort) {

  public static CommissionRatePageResponse de(
      PageResponse<CommissionRateItem> pagina, String orden) {
    return new CommissionRatePageResponse(
        pagina.content(),
        pagina.totalElements(),
        pagina.totalPages(),
        pagina.page(),
        pagina.size(),
        pagina.totalIsExact(),
        orden);
  }
}
