package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.AfftrackRateItem;
import com.factech.nexus.modules.commissions.application.AfftrackRatePageResponse;
import com.factech.nexus.modules.commissions.application.ListAfftrackRatesRequest;
import com.factech.nexus.modules.commissions.domain.repository.AfftrackRateQueryRepository;
import com.factech.nexus.modules.commissions.domain.repository.AfftrackRateQueryRepository.AfftrackRateFilters;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** `RF-CM-016` — la escala de cada rol en cada producto FTD. */
@Service
public class ListAfftrackRatesService {

  private static final String ORDEN = "product.code,asc;role.code,asc;threshold,asc";

  private final AfftrackRateQueryRepository consultas;
  private final Pagination paginacion;

  public ListAfftrackRatesService(AfftrackRateQueryRepository consultas, Pagination paginacion) {
    this.consultas = consultas;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public AfftrackRatePageResponse list(ListAfftrackRatesRequest filtros) {
    Pagination.Slice trozo = paginacion.resolver(filtros.page(), filtros.size());
    AfftrackRateFilters criterios =
        new AfftrackRateFilters(
            filtros.productId(), filtros.roleId(), Boolean.TRUE.equals(filtros.includeDeleted()));
    List<AfftrackRateItem> contenido =
        consultas.search(criterios, trozo.page() * trozo.size(), trozo.size()).stream()
            .map(AfftrackRateItem::from)
            .toList();
    long total = consultas.count(criterios);
    return AfftrackRatePageResponse.de(
        PageResponse.de(contenido, total, trozo.page(), trozo.size()), ORDEN);
  }
}
