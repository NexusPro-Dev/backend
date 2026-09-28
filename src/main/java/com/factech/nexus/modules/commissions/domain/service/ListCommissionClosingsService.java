package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.CommissionClosingPageResponse;
import com.factech.nexus.modules.commissions.application.CommissionClosingResponse;
import com.factech.nexus.modules.commissions.application.ListCommissionClosingsRequest;
import com.factech.nexus.modules.commissions.domain.models.ClosingOrigin;
import com.factech.nexus.modules.commissions.domain.repository.CommissionClosingRepository;
import com.factech.nexus.modules.commissions.domain.repository.CommissionClosingRepository.ClosingFilter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los cierres del periodo, del más reciente al más antiguo (`RF-CM-009`, `CA-CM-180`). Un cierre
 * que falló no tiene fin, y se ordena por su inicio.
 */
@Service
public class ListCommissionClosingsService {

  private static final String ORDEN = "closedAt,desc";

  private final CommissionClosingRepository cierres;
  private final Pagination paginacion;

  public ListCommissionClosingsService(CommissionClosingRepository cierres, Pagination paginacion) {
    this.cierres = cierres;
    this.paginacion = paginacion;
  }

  @Transactional(readOnly = true)
  public CommissionClosingPageResponse list(ListCommissionClosingsRequest filtros) {
    // Los 400 SALEN JUNTOS.
    List<FieldError> errores = new ArrayList<>();
    ClosingOrigin origen = null;
    if (filtros.origin() != null && !filtros.origin().isBlank()) {
      try {
        origen = ClosingOrigin.valueOf(filtros.origin().trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        errores.add(new FieldError("origin", "VAL-001", "El origen es PROGRAMADO o MANUAL."));
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
    ClosingFilter filtro = new ClosingFilter(origen, filtros.from(), filtros.to());
    List<CommissionClosingResponse> contenido =
        cierres.search(filtro, trozo.offset(), trozo.size()).stream()
            .map(CommissionClosingResponse::from)
            .toList();
    return CommissionClosingPageResponse.de(
        PageResponse.de(contenido, cierres.count(filtro), trozo.page(), trozo.size()), ORDEN);
  }
}
