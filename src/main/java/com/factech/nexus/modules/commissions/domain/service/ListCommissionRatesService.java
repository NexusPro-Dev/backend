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
 * El listado de tarifas (`RF-CM-002`).
 *
 * <p><b>Devuelve las tarifas tal como se declararon y no resuelve nada.</b> Si un rol tiene tarifa
 * por omision y una persona su excepcion, aqui aparecen las dos. Cual se aplica lo responde
 * `RF-CM-005`, y son preguntas distintas.
 *
 * <p><b>El orden es fijo</b>: del inicio de vigencia mas reciente al mas antiguo, con desempate por
 * identificador. No lo elige el cliente porque es parte del significado del recurso — el historial
 * se lee del presente hacia atras.
 */
@Service
public class ListCommissionRatesService {

  /** El orden aplicado, publicado en la respuesta para que se sepa sobre que se pagina. */
  private static final String ORDEN = "validFrom,desc";

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
            filtros.roleId(),
            filtros.productId(),
            filtros.userId(),
            filtros.onDate(),
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
