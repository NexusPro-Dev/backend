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
 * <p><b>Devuelve el catálogo y no lo que rige.</b> Una tasa puede aparecer aquí con su porcentaje y
 * <b>no pagar nada a nadie</b>, si no está asociada a ningún producto (`RN-CM-012`). Por eso cada
 * fila lleva {@code associatedProducts}: sin ese número, el listado diría exactamente lo mismo en
 * los dos casos.
 *
 * <p><b>El orden es fijo</b>: por código de rol, y dentro de cada rol del porcentaje mayor al
 * menor. No lo elige el cliente porque es parte del significado del recurso — un catálogo se lee
 * agrupado por a quién paga.
 */
@Service
public class ListCommissionRatesService {

  /** El orden aplicado, publicado en la respuesta para que se sepa sobre qué se pagina. */
  private static final String ORDEN = "role.code,asc";

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
            filtros.roleId(), filtros.rateType(), Boolean.TRUE.equals(filtros.includeDeleted()));

    List<CommissionRateItem> contenido =
        consultas.search(criterios, trozo.page() * trozo.size(), trozo.size()).stream()
            .map(CommissionRateItem::from)
            .toList();

    long total = consultas.count(criterios);

    return CommissionRatePageResponse.de(
        PageResponse.de(contenido, total, trozo.page(), trozo.size()), ORDEN);
  }
}
