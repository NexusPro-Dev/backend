package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.CommissionBatchDetailResponse;
import com.factech.nexus.modules.commissions.domain.models.BatchStatus;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository.BatchToPay;
import com.factech.nexus.modules.movements.application.CommissionPayout;
import com.factech.nexus.modules.movements.application.CommissionPayout.PayoutOrder;
import com.factech.nexus.modules.movements.application.CommissionPayout.PayoutResult;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.time.BusinessCalendar;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Marcar un lote como pagado es abonarlo en la billetera</b> (`RF-CM-011`, `RN-CM-030`).
 *
 * <p><b>Bloquear, comprobar, abonar, marcar — en una transacción.</b> El bloqueo va <b>antes</b>
 * del abono porque la marca va <b>después</b>: {@code ck_commission_batches_pagado} exige el
 * movimiento en la misma fila que {@code PAGADO}. El segundo de dos pagos simultáneos espera al
 * primero y encuentra {@code PAGADO} sin llegar a `MV` (`CA-CM-192`); la clave del lote en `MV`
 * queda detrás como segunda defensa.
 *
 * <p><b>Solo se paga un lote {@code PENDIENTE}</b>: uno {@code ABIERTO} sigue creciendo, y pagarlo
 * sería pagar una cifra que dentro de una hora ya no es la suya.
 */
@Service
public class PayCommissionBatchService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "commission_batches";

  private final CommissionBatchRepository lotes;
  private final CommissionPayout abono;
  private final CommissionBatchQueryService consultas;
  private final BusinessCalendar calendario;
  private final AuditWriter auditoria;

  public PayCommissionBatchService(
      CommissionBatchRepository lotes,
      CommissionPayout abono,
      CommissionBatchQueryService consultas,
      BusinessCalendar calendario,
      AuditWriter auditoria) {
    this.lotes = lotes;
    this.abono = abono;
    this.consultas = consultas;
    this.calendario = calendario;
    this.auditoria = auditoria;
  }

  @Transactional
  public CommissionBatchDetailResponse pay(UUID batchId) {
    BatchToPay lote =
        lotes
            .lockForPayment(batchId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un lote de comisión con ese identificador."));
    if (BatchStatus.ABIERTO.name().equals(lote.status())) {
      rechazar("EX-002", "El lote sigue abierto: se paga después del cierre.");
    }
    if (BatchStatus.PAGADO.name().equals(lote.status())) {
      rechazar("EX-003", "El lote ya está pagado.");
    }
    // `RN-CM-048` (30-09-2026): a un pendiente se le pueden retirar o revertir
    // todas, y abonar un lote vacío dejaría un PAGO_COMISION que no paga nada.
    // Cuentan las comisiones VIVAS, no el total: una viva de cero se paga.
    if (!lotes.hasLiveCommissions(batchId)) {
      rechazar("EX-005", "El lote no tiene nada que pagar.");
    }

    PayoutResult abonado =
        abono.pay(
            new PayoutOrder(
                lote.userId(), lote.currencyId(), lote.totalAmount(), lote.id(), lote.code()));
    OffsetDateTime ahora = calendario.ahora();
    lotes.markPaid(batchId, ahora, abonado.movementId());

    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("status", BatchStatus.PAGADO.name());
    despues.put("paid_at", ahora.toString());
    despues.put("total_amount", lote.totalAmount().toPlainString());
    despues.put("paid_amount", abonado.amount().toPlainString());
    despues.put("movement_id", abonado.movementId().toString());
    auditoria.recordChange(
        new ChangeEvent(
            MODULO,
            ENTIDAD,
            batchId,
            ChangeAction.UPDATE,
            Map.of("before", Map.of("status", BatchStatus.PENDIENTE.name()), "after", despues)));

    return consultas.get(batchId, null);
  }

  private static void rechazar(String codigo, String mensaje) {
    throw new BusinessRuleException(
        codigo, mensaje, List.of(new FieldError("status", codigo, mensaje)));
  }
}
