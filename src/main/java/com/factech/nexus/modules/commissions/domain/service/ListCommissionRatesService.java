package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.CommissionRateItem;
import com.factech.nexus.modules.commissions.application.CommissionRatePageResponse;
import com.factech.nexus.modules.commissions.application.ListCommissionRatesRequest;
import com.factech.nexus.modules.commissions.domain.repository.CommissionRateQueryRepository;
import com.factech.nexus.modules.commissions.domain.repository.CommissionRateQueryRepository.RateFilters;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El listado del catálogo de tasas por rol (`RF-CM-002`).
 *
 * <p><b>Desde el 15-09-2026 devuelve lo que rige</b> (`RN-CM-021`): cada tasa viva paga a su rol
 * sobre su producto, y por eso cada fila lleva el producto resuelto en lugar de la cuenta de
 * asociaciones que hasta entonces distinguía una tasa que pagaba de una que no.
 *
 * <p><b>El orden es fijo</b>: por código de producto, y dentro de cada producto por rol. No lo
 * elige el cliente porque es parte del significado del recurso — un catálogo se lee agrupado por
 * qué paga cada producto.
 */
@Service
public class ListCommissionRatesService {

  /** El orden aplicado, publicado en la respuesta para que se sepa sobre qué se pagina. */
  private static final String ORDEN = "product.code,asc;role.code,asc";

  private final CommissionRateQueryRepository consultas;
  private final Pagination paginacion;

  public ListCommissionRatesService(
      CommissionRateQueryRepository consultas, Pagination paginacion) {
    this.consultas = consultas;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public CommissionRatePageResponse list(ListCommissionRatesRequest filtros) {
    Pagination.Slice trozo = paginacion.resolver(filtros.page(), filtros.size());

    RateFilters criterios =
        new RateFilters(
            filtros.productId(),
            filtros.roleId(),
            filtros.rateType(),
            Boolean.TRUE.equals(filtros.includeDeleted()));

    List<CommissionRateItem> contenido =
        consultas.search(criterios, trozo.page() * trozo.size(), trozo.size()).stream()
            .map(CommissionRateItem::from)
            .toList();

    long total = consultas.count(criterios);

    return CommissionRatePageResponse.de(
        PageResponse.de(contenido, total, trozo.page(), trozo.size()), ORDEN);
  }
}
