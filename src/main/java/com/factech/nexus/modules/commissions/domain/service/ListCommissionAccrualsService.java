package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.CommissionAccrualItem;
import com.factech.nexus.modules.commissions.application.CommissionAccrualPageResponse;
import com.factech.nexus.modules.commissions.application.ListCommissionAccrualsRequest;
import com.factech.nexus.modules.commissions.domain.models.AccrualOutcome;
import com.factech.nexus.modules.commissions.domain.repository.CommissionAccrualQueryRepository;
import com.factech.nexus.modules.commissions.domain.repository.CommissionAccrualQueryRepository.AccrualFilter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El desenlace de las líneas de venta (`RF-CM-014`): lo que antes era la respuesta de liquidar, y
 * sobre todo <b>las rechazadas y por qué</b>. Una línea que aún no tiene desenlace —espera al
 * barrido— no aparece: esto dice qué pasó, no qué falta.
 */
@Service
public class ListCommissionAccrualsService {

  private static final String ORDEN = "updatedAt,desc";

  private final CommissionAccrualQueryRepository consultas;
  private final Pagination paginacion;

  public ListCommissionAccrualsService(
      CommissionAccrualQueryRepository consultas, Pagination paginacion) {
    this.consultas = consultas;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public CommissionAccrualPageResponse list(ListCommissionAccrualsRequest filtros) {
    // Los 400 SALEN JUNTOS.
    List<FieldError> errores = new ArrayList<>();
    AccrualOutcome desenlace = null;
    if (filtros.outcome() != null && !filtros.outcome().isBlank()) {
      try {
        desenlace = AccrualOutcome.valueOf(filtros.outcome().trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        errores.add(
            new FieldError(
                "outcome", "VAL-001", "El desenlace es DEVENGADA, SIN_COMISION o RECHAZADA."));
      }
    }
    if (filtros.from() != null && filtros.to() != null && filtros.from().isAfter(filtros.to())) {
      errores.add(
          new FieldError("from", "VAL-002", "La fecha inicial no puede ser posterior a la final."));
    }
    if (!errores.isEmpty()) {
      throw new ValidationException(errores.get(0).code(), errores.get(0).message(), errores);
    }
    Pagination.Slice trozo = paginacion.resolver(filtros.page(), filtros.size());
    AccrualFilter filtro =
        new AccrualFilter(
            desenlace, filtros.movementId(), filtros.productId(), filtros.from(), filtros.to());
    List<CommissionAccrualItem> contenido =
        consultas.search(filtro, trozo.offset(), trozo.size()).stream()
            .map(CommissionAccrualItem::from)
            .toList();
    return CommissionAccrualPageResponse.de(
        PageResponse.de(contenido, consultas.count(filtro), trozo.page(), trozo.size()), ORDEN);
  }
}
