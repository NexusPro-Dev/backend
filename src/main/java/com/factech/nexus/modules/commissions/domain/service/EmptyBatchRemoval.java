package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository.DeletedBatch;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Un lote sin pagar y sin comisiones se borra</b> (`RN-CM-052`, `RF-CM-022` `plan.md` §13).
 * Desde el 08-10-2026 solo lo llama {@link DeleteEmptyBatchesService} —la orden de `RF-CM-027`, el
 * cierre y el pago—, con los lotes ya bloqueados; retirar, devolver y liberar una línea dejaron de
 * hacerlo. Lo que era el lote lo guarda la auditoría, como eliminación física, con el motivo de
 * quien llama.
 */
@Component
public class EmptyBatchRemoval {

  private final CommissionBatchRepository lotes;
  private final AuditWriter auditoria;

  public EmptyBatchRemoval(CommissionBatchRepository lotes, AuditWriter auditoria) {
    this.lotes = lotes;
    this.auditoria = auditoria;
  }

  /**
   * Borra cada lote que no tenga comisiones ni esté pagado.
   *
   * @return lo que era cada uno de los que borró, por identificador
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public List<DeletedBatch> removeIfEmpty(Collection<UUID> batchIds, String motivo) {
    // En orden de identificador, el único del módulo: ya están bloqueados así.
    List<DeletedBatch> borrados = new ArrayList<>();
    for (UUID id : new TreeSet<>(batchIds)) {
      lotes
          .deleteIfEmpty(id)
          .ifPresent(
              lote -> {
                auditar(lote, motivo);
                borrados.add(lote);
              });
    }
    return borrados;
  }

  private void auditar(DeletedBatch lote, String motivo) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("code", lote.code());
    m.put("user_id", lote.userId().toString());
    m.put("currency_id", lote.currencyId().toString());
    m.put("status", lote.status());
    m.put("period_start", lote.periodStart().toString());
    // Nulo y PRESENTE: un lote abierto no tiene fin.
    m.put("period_end", lote.periodEnd() == null ? null : lote.periodEnd().toString());
    auditoria.recordDeletion(
        new DeletionEvent("CM", "commission_batches", lote.id(), DeletionType.PHYSICAL, motivo, m));
  }
}
