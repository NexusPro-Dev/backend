package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.CommissionBatchDetailResponse;
import com.factech.nexus.modules.commissions.domain.models.BatchStatus;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository.LockedBatch;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository.LockedCommission;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository.OpenBatch;
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
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Retirar una comisión de un lote pendiente</b> (`RF-CM-022`, `RN-CM-046`): lo que Finanzas no
 * confirma al revisar un lote cerrado pasa al lote abierto de su persona y moneda, y se paga en el
 * cierre siguiente.
 *
 * <p><b>Bloquear la comisión, después los lotes, comprobar, mover y ajustar — en una
 * transacción.</b> El orden de los bloqueos es el único del módulo (`plan.md` §1): con UUID v7 el
 * pendiente tiene un identificador menor que el abierto que se abre después, de modo que bloquear
 * el pendiente y luego el abierto es bloquear por identificador. {@code lockOpenBatch} es el mismo
 * del devengo, con su {@code GREATEST}: si un cierre acaba de cerrar el abierto, se abre otro.
 *
 * <p><b>El pago que espera</b> (`CA-CM-280`): pagar toma el lote con {@code FOR UPDATE}. Si lo
 * tiene el retiro, el pago abona el total ya rebajado; si lo tiene el pago, el retiro lee {@code
 * PAGADO} y responde {@code 409}.
 */
@Service
public class WithdrawCommissionService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "commissions";

  private final CommissionBatchRepository lotes;
  private final CommissionBatchQueryService consultas;
  private final BusinessCalendar calendario;
  private final AuditWriter auditoria;

  public WithdrawCommissionService(
      CommissionBatchRepository lotes,
      CommissionBatchQueryService consultas,
      BusinessCalendar calendario,
      AuditWriter auditoria) {
    this.lotes = lotes;
    this.consultas = consultas;
    this.calendario = calendario;
    this.auditoria = auditoria;
  }

  @Transactional
  public CommissionBatchDetailResponse withdraw(UUID batchId, UUID commissionId) {
    Optional<LockedCommission> bloqueada = lotes.lockCommission(commissionId);
    LockedBatch pendiente =
        lotes.lockBatches(List.of(batchId)).stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-001", "No existe un lote de comisión con ese identificador."));
    LockedCommission comision =
        bloqueada
            .filter(c -> c.batchId().equals(batchId))
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-002", "No existe esa comisión en este lote de comisión."));
    if (BatchStatus.ABIERTO.name().equals(pendiente.status())) {
      rechazar("EX-003", "El lote sigue abierto: aún no hay nada que revisar.");
    }
    if (BatchStatus.PAGADO.name().equals(pendiente.status())) {
      rechazar("EX-004", "El lote ya está pagado.");
    }

    OffsetDateTime ahora = calendario.ahora();
    OpenBatch abierto = lotes.lockOpenBatch(pendiente.userId(), pendiente.currencyId(), ahora);
    lotes.moveCommission(commissionId, abierto.id(), batchId);
    lotes.addToTotal(batchId, comision.amount().negate(), ahora);
    lotes.addToTotal(abierto.id(), comision.amount(), ahora);

    auditoria.recordChange(
        new ChangeEvent(
            MODULO,
            ENTIDAD,
            commissionId,
            ChangeAction.UPDATE,
            Map.of(
                "before",
                ubicacion(batchId, comision.withdrawnFromBatchId(), comision),
                "after",
                ubicacion(abierto.id(), batchId, comision))));

    return consultas.get(batchId, null);
  }

  static Map<String, Object> ubicacion(UUID lote, UUID origen, LockedCommission comision) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("batch_id", lote.toString());
    // Nulo y PRESENTE: la comisión no venía de ningún retiro.
    m.put("withdrawn_from_batch_id", origen == null ? null : origen.toString());
    m.put("commission_amount", comision.amount().toPlainString());
    return m;
  }

  static void rechazar(String codigo, String mensaje) {
    throw new BusinessRuleException(
        codigo, mensaje, List.of(new FieldError("status", codigo, mensaje)));
  }
}
