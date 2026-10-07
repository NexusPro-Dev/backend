package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.CommissionBatchDetailResponse;
import com.factech.nexus.modules.commissions.application.CommissionBatchItem;
import com.factech.nexus.modules.commissions.application.CommissionBatchPageResponse;
import com.factech.nexus.modules.commissions.application.ListCommissionBatchesRequest;
import com.factech.nexus.modules.commissions.application.MyCommissionItem;
import com.factech.nexus.modules.commissions.application.MyCommissionPageResponse;
import com.factech.nexus.modules.commissions.application.MyCommissionsRequest;
import com.factech.nexus.modules.commissions.domain.models.BatchStatus;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchQueryRepository;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchQueryRepository.BatchFilter;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchQueryRepository.OwnFilter;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.pagination.PageResponse;
import com.factech.nexus.shared.pagination.Pagination;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los lotes de comisión: los de todos (`RF-CM-010`) y los propios (`RF-CM-012`); y las comisiones
 * propias sin pasar por los lotes (`RF-CM-026`).
 *
 * <p><b>Una sola lógica para las dos</b>, con la persona como parámetro: en la propia la fija el
 * token, y el filtro de persona de la petición se ignora. Duplicar el servicio sería dar a la
 * variante propia la ocasión de filtrar distinto, que es la avería que `list-own` existe para
 * evitar.
 */
@Service
public class CommissionBatchQueryService {

  private static final String ORDEN = "periodStart,desc";
  private static final String ORDEN_COMISIONES = "accruedAt,desc";
  private static final Set<String> CLASES = Set.of("POR_VENTA", "POR_AFFTRACK");

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
   * Todas las comisiones de {@code owner}, sin pasar por sus lotes (`RF-CM-026`). La persona la
   * pone el token y no hay forma de pedir otra.
   */
  @Transactional(readOnly = true)
  public MyCommissionPageResponse listOwnCommissions(MyCommissionsRequest filtros, UUID owner) {
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
    String clase = null;
    if (filtros.commissionKind() != null && !filtros.commissionKind().isBlank()) {
      clase = filtros.commissionKind().trim().toUpperCase();
      if (!CLASES.contains(clase)) {
        errores.add(
            new FieldError("commissionKind", "VAL-001", "La clase es POR_VENTA o POR_AFFTRACK."));
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
    OwnFilter filtro =
        new OwnFilter(
            owner,
            estado,
            filtros.currencyId(),
            filtros.productId(),
            clase,
            filtros.from(),
            filtros.to());
    List<MyCommissionItem> contenido =
        consultas.searchOwn(filtro, trozo.offset(), trozo.size()).stream()
            .map(MyCommissionItem::from)
            .toList();
    return MyCommissionPageResponse.de(
        PageResponse.de(contenido, consultas.countOwn(filtro), trozo.page(), trozo.size()),
        ORDEN_COMISIONES);
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
    return CommissionBatchDetailResponse.from(
        lote, consultas.commissionsOf(id), consultas.withdrawnFrom(id));
  }
}
