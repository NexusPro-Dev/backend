package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository.DeletedBatch;
import com.factech.nexus.shared.audit.AuditEnums.DeletionType;
import com.factech.nexus.shared.audit.AuditEvents.DeletionEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Un lote sin pagar que se queda vacío se borra</b> (`RN-CM-052`, `RF-CM-022` `plan.md` §13): lo
 * llaman retirar, devolver y liberar una línea, después de sacar la comisión y con los lotes ya
 * bloqueados. Lo que era el lote lo guarda la auditoría, como eliminación física.
 */
@Component
public class EmptyBatchRemoval {

  private static final String MOTIVO =
      "RN-CM-052: el lote se quedó sin comisiones y se borra en el mismo acto.";

  private final CommissionBatchRepository lotes;
  private final AuditWriter auditoria;

  public EmptyBatchRemoval(CommissionBatchRepository lotes, AuditWriter auditoria) {
    this.lotes = lotes;
    this.auditoria = auditoria;
  }

  /**
   * Borra cada lote que no tenga comisiones ni esté pagado.
   *
   * @return los identificadores de los que borró
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public Set<UUID> removeIfEmpty(Collection<UUID> batchIds) {
    // En orden de identificador, el único del módulo: ya están bloqueados así.
    Set<UUID> borrados = new TreeSet<>();
    for (UUID id : new TreeSet<>(batchIds)) {
      lotes
          .deleteIfEmpty(id)
          .ifPresent(
              lote -> {
                auditar(lote);
                borrados.add(lote.id());
              });
    }
    return borrados;
  }

  private void auditar(DeletedBatch lote) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("code", lote.code());
    m.put("user_id", lote.userId().toString());
    m.put("currency_id", lote.currencyId().toString());
    m.put("status", lote.status());
    m.put("period_start", lote.periodStart().toString());
    // Nulo y PRESENTE: un lote abierto no tiene fin.
    m.put("period_end", lote.periodEnd() == null ? null : lote.periodEnd().toString());
    auditoria.recordDeletion(
        new DeletionEvent("CM", "commission_batches", lote.id(), DeletionType.PHYSICAL, MOTIVO, m));
  }
}
