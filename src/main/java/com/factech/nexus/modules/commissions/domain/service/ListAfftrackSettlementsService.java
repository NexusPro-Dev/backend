package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.AfftrackSettlementItem;
import com.factech.nexus.modules.commissions.application.AfftrackSettlementPageResponse;
import com.factech.nexus.modules.commissions.application.ListAfftrackSettlementsRequest;
import com.factech.nexus.modules.commissions.domain.repository.AfftrackSettlementQueryRepository;
import com.factech.nexus.modules.commissions.domain.repository.AfftrackSettlementQueryRepository.SettlementFilter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `RF-CM-021` — lo que cada cierre hizo con los FTD de cada persona. Filtrando por persona y
 * producto, la primera fila es su remanente vigente.
 */
@Service
public class ListAfftrackSettlementsService {

  private static final String ORDEN = "closing.closedAt,desc;user.username,asc;product.code,asc";

  private final AfftrackSettlementQueryRepository consultas;
  private final Pagination paginacion;

  public ListAfftrackSettlementsService(
      AfftrackSettlementQueryRepository consultas, Pagination paginacion) {
    this.consultas = consultas;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public AfftrackSettlementPageResponse list(ListAfftrackSettlementsRequest filtros) {
    if (filtros.from() != null && filtros.to() != null && filtros.from().isAfter(filtros.to())) {
      String mensaje = "La fecha inicial no puede ser posterior a la final.";
      throw new ValidationException(
          "VAL-001", mensaje, List.of(new FieldError("from", "VAL-001", mensaje)));
    }
    Pagination.Slice trozo = paginacion.resolver(filtros.page(), filtros.size());
    SettlementFilter filtro =
        new SettlementFilter(
            filtros.userId(),
            filtros.productId(),
            filtros.closingId(),
            filtros.from(),
            filtros.to(),
            filtros.paid());
    List<AfftrackSettlementItem> contenido =
        consultas.search(filtro, trozo.offset(), trozo.size()).stream()
            .map(AfftrackSettlementItem::from)
            .toList();
    return AfftrackSettlementPageResponse.de(
        PageResponse.de(contenido, consultas.count(filtro), trozo.page(), trozo.size()), ORDEN);
  }
}
