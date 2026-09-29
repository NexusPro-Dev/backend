package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.ListUserAfftrackRatesRequest;
import com.factech.nexus.modules.commissions.application.UserAfftrackRateItem;
import com.factech.nexus.modules.commissions.application.UserAfftrackRatePageResponse;
import com.factech.nexus.modules.commissions.domain.repository.UserAfftrackRateQueryRepository;
import com.factech.nexus.modules.commissions.domain.repository.UserAfftrackRateQueryRepository.UserAfftrackRateFilters;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RF-CM-019`, la lectura: los escalones de persona, y con {@code onDate}, la escala de ese día.
 */
@Service
public class ListUserAfftrackRatesService {

  private static final String ORDEN =
      "user.username,asc;product.code,asc;threshold,asc;validFrom,desc";

  private final UserAfftrackRateQueryRepository consultas;
  private final Pagination paginacion;

  public ListUserAfftrackRatesService(
      UserAfftrackRateQueryRepository consultas, Pagination paginacion) {
    this.consultas = consultas;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public UserAfftrackRatePageResponse list(ListUserAfftrackRatesRequest filtros) {
    Pagination.Slice trozo = paginacion.resolver(filtros.page(), filtros.size());
    UserAfftrackRateFilters criterios =
        new UserAfftrackRateFilters(
            filtros.userId(),
            filtros.productId(),
            filtros.onDate(),
            Boolean.TRUE.equals(filtros.includeDeleted()));
    List<UserAfftrackRateItem> contenido =
        consultas.search(criterios, trozo.page() * trozo.size(), trozo.size()).stream()
            .map(UserAfftrackRateItem::from)
            .toList();
    long total = consultas.count(criterios);
    return UserAfftrackRatePageResponse.de(
        PageResponse.de(contenido, total, trozo.page(), trozo.size()), ORDEN);
  }
}
