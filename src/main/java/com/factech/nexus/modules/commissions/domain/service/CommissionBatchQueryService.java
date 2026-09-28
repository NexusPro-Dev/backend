package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.CommissionBatchDetailResponse;
import com.factech.nexus.modules.commissions.application.CommissionBatchItem;
import com.factech.nexus.modules.commissions.application.CommissionBatchPageResponse;
import com.factech.nexus.modules.commissions.application.ListCommissionBatchesRequest;
import com.factech.nexus.modules.commissions.domain.models.BatchStatus;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchQueryRepository;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchQueryRepository.BatchFilter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los lotes de comisión: los de todos (`RF-CM-010`) y los propios (`RF-CM-012`).
 *
 * <p><b>Una sola lógica para las dos</b>, con la persona como parámetro: en la propia la fija el
 * token, y el filtro de persona de la petición se ignora. Duplicar el servicio sería dar a la
 * variante propia la ocasión de filtrar distinto, que es la avería que `list-own` existe para
 * evitar.
 */
@Service
public class CommissionBatchQueryService {

  private static final String ORDEN = "periodStart,desc";

  private final CommissionBatchQueryRepository consultas;
  private final Pagination paginacion;

  public CommissionBatchQueryService(
      CommissionBatchQueryRepository consultas, Pagination paginacion) {
    this.consultas = consultas;
    this.paginacion = paginacion;
  }

  /**
   * @param owner la persona del token en `RF-CM-012`; nulo en `RF-CM-010`
   */
  @Transactional(readOnly = true)
  public CommissionBatchPageResponse list(ListCommissionBatchesRequest filtros, UUID owner) {
    // Los 400 SALEN JUNTOS.
    List<FieldError> errores = new ArrayList<>();
    BatchStatus estado = null;
    if (filtros.status() != null && !filtros.status().isBlank()) {
      try {
        estado = BatchStatus.valueOf(filtros.status().trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        errores.add(
            new FieldError("status", "VAL-001", "El estado es ABIERTO, PENDIENTE o PAGADO."));
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
    BatchFilter filtro =
        new BatchFilter(
            estado,
            owner != null ? owner : filtros.userId(),
            filtros.currencyId(),
            filtros.from(),
            filtros.to());
    List<CommissionBatchItem> contenido =
        consultas.search(filtro, trozo.offset(), trozo.size()).stream()
            .map(CommissionBatchItem::from)
            .toList();
    return CommissionBatchPageResponse.de(
        PageResponse.de(contenido, consultas.count(filtro), trozo.page(), trozo.size()), ORDEN);
  }

  /**
   * @param owner la persona del token en `RF-CM-012`: un lote ajeno responde igual que uno que no
   *     existe, para no confirmar que existe
   */
  @Transactional(readOnly = true)
  public CommissionBatchDetailResponse get(UUID id, UUID owner) {
    CommissionBatchItem lote =
        consultas
            .find(id, owner)
            .map(CommissionBatchItem::from)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un lote de comisión con ese identificador."));
    return CommissionBatchDetailResponse.from(lote, consultas.commissionsOf(id));
  }
}
