package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.domain.models.BatchStatus;
import com.factech.nexus.modules.commissions.domain.repository.CommissionAccrualRepository;
import com.factech.nexus.modules.commissions.domain.repository.CommissionAccrualRepository.AccrualRow;
import com.factech.nexus.modules.commissions.domain.repository.CommissionAccrualRepository.LiveCommission;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository;
import com.factech.nexus.modules.movements.application.CommissionedLineRelease;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.time.BusinessCalendar;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Borrar las comisiones de una línea cuyo vendedor se corrige</b> (`RF-CM-024`, `RN-CM-047`): la
 * implementación del puerto que `MV` declara ({@link CommissionedLineRelease}).
 *
 * <p><b>Desde el 07-10-2026 se borran</b>, no se marcan: ningún lote las paga ya, cada uno rebaja
 * su total, y lo que fueron —persona, lote e importe de cada una— lo guarda la auditoría.
 *
 * <p><b>Una línea tiene una comisión por nivel de la cadena</b>, cada una en el lote de otra
 * persona, y «no se ha pagado» tiene que ser verdad para todas. <b>Tampoco se libera un FTD ya
 * contado</b>: su conteo pagó escalones a toda la cadena vieja.
 *
 * <p><b>El orden</b> (`plan.md` §1): el bloqueo consultivo de la línea —el mismo del devengo, de
 * modo que un barrido que la esté devengando termina antes y el devengo de la cadena nueva espera
 * al commit de `MV`—, después sus comisiones vivas y después sus lotes, por identificador, que es
 * el orden único del módulo. <b>{@code MANDATORY}</b>: sin la transacción de `MV` alrededor, la
 * reversión quedaría escrita aunque la corrección fallara después.
 *
 * <p><b>La cadena nueva no se devenga aquí</b>: el aviso de `MV` después del commit la lleva a
 * `RF-CM-013`, que la encuentra sin desenlace como a cualquier línea recién atribuida.
 */
@Service
public class ReleaseCommissionedLineService implements CommissionedLineRelease {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "commission_accruals";

  private final CommissionAccrualRepository desenlaces;
  private final CommissionBatchRepository lotes;
  private final BusinessCalendar calendario;
  private final AuditWriter auditoria;

  public ReleaseCommissionedLineService(
      CommissionAccrualRepository desenlaces,
      CommissionBatchRepository lotes,
      BusinessCalendar calendario,
      AuditWriter auditoria) {
    this.desenlaces = desenlaces;
    this.lotes = lotes;
    this.calendario = calendario;
    this.auditoria = auditoria;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public ReleaseOutcome release(UUID movementDetailId, UUID actorId) {
    desenlaces.lockLine(movementDetailId);
    if (desenlaces.hasCountedFtd(movementDetailId)) {
      return ReleaseOutcome.FTD_CONTADO;
    }
    List<LiveCommission> vivas = desenlaces.lockLiveCommissionsOf(movementDetailId);
    Map<UUID, BigDecimal> porLote =
        vivas.stream()
            .collect(
                Collectors.groupingBy(
                    LiveCommission::batchId,
                    LinkedHashMap::new,
                    Collectors.reducing(BigDecimal.ZERO, LiveCommission::amount, BigDecimal::add)));
    boolean pagada =
        lotes.lockBatches(porLote.keySet()).stream()
            .anyMatch(l -> BatchStatus.PAGADO.name().equals(l.status()));
    if (pagada) {
      return ReleaseOutcome.COMISION_PAGADA;
    }

    Optional<AccrualRow> desenlace = desenlaces.find(movementDetailId);
    OffsetDateTime ahora = calendario.ahora();
    desenlaces.delete(vivas.stream().map(LiveCommission::id).toList());
    porLote.forEach((lote, suma) -> lotes.addToTotal(lote, suma.negate(), ahora));
    desenlaces.deleteOutcome(movementDetailId);

    if (desenlace.isPresent() || !vivas.isEmpty()) {
      auditar(movementDetailId, actorId, desenlace, vivas);
    }
    return ReleaseOutcome.LIBERADA;
  }

  private void auditar(
      UUID linea, UUID actorId, Optional<AccrualRow> desenlace, List<LiveCommission> vivas) {
    List<Map<String, Object>> comisiones = new ArrayList<>();
    for (LiveCommission c : vivas) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", c.id().toString());
      m.put("user_id", c.userId().toString());
      m.put("batch_id", c.batchId().toString());
      m.put("commission_amount", c.amount().toPlainString());
      comisiones.add(m);
    }
    Map<String, Object> antes = new LinkedHashMap<>();
    // Nulo y PRESENTE: la línea no tenía desenlace (un FTD, o una línea sin atender).
    antes.put("outcome", desenlace.map(d -> d.outcome().name()).orElse(null));
    antes.put("commissions", comisiones);
    Map<String, Object> despues = new LinkedHashMap<>();
    despues.put("outcome", null);
    despues.put("deleted_commissions", comisiones.stream().map(m -> m.get("id")).toList());
    despues.put("deleted_by", actorId.toString());
    Map<String, Object> cambios = new LinkedHashMap<>();
    cambios.put("before", antes);
    cambios.put("after", despues);
    auditoria.recordChange(new ChangeEvent(MODULO, ENTIDAD, linea, ChangeAction.UPDATE, cambios));
  }
}
