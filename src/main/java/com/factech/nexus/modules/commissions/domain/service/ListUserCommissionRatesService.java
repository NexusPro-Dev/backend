package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.ListUserCommissionRatesRequest;
import com.factech.nexus.modules.commissions.application.UserCommissionRateItem;
import com.factech.nexus.modules.commissions.application.UserCommissionRatePageResponse;
import com.factech.nexus.modules.commissions.domain.repository.UserCommissionRateQueryRepository;
import com.factech.nexus.modules.commissions.domain.repository.UserCommissionRateQueryRepository.UserRateFilters;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El listado de tasas personalizadas (`RF-CM-002`).
 *
 * <p><b>Incluye el historial</b>: las vencidas viajan junto a la vigente salvo que se filtre por
 * fecha con {@code onDate}. Y es <b>el único historial que le queda al módulo</b> — las tasas de
 * rol perdieron la vigencia y con ella la capacidad de decir qué rigió cuándo.
 *
 * <p><b>El orden es fijo</b>: del inicio de vigencia más reciente al más antiguo, con desempate por
 * identificador. No lo elige el cliente porque es parte del significado del recurso — el historial
 * se lee del presente hacia atrás.
 */
@Service
public class ListUserCommissionRatesService {

  /** El orden aplicado, publicado en la respuesta para que se sepa sobre qué se pagina. */
  private static final String ORDEN = "validFrom,desc";

  private final UserCommissionRateQueryRepository consultas;
  private final Pagination paginacion;

  public ListUserCommissionRatesService(
      UserCommissionRateQueryRepository consultas, Pagination paginacion) {
    this.consultas = consultas;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public UserCommissionRatePageResponse list(ListUserCommissionRatesRequest filtros) {
    Pagination.Slice trozo = paginacion.resolver(filtros.page(), filtros.size());

    UserRateFilters criterios =
        new UserRateFilters(
            filtros.userId(), filtros.onDate(), Boolean.TRUE.equals(filtros.includeDeleted()));

    List<UserCommissionRateItem> contenido =
        consultas.search(criterios, trozo.page() * trozo.size(), trozo.size()).stream()
            .map(UserCommissionRateItem::from)
            .toList();

    long total = consultas.count(criterios);

    return UserCommissionRatePageResponse.de(
        PageResponse.de(contenido, total, trozo.page(), trozo.size()), ORDEN);
  }
}
