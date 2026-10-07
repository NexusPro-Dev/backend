package com.factech.nexus.modules.commissions.domain.service;

import static com.factech.nexus.modules.commissions.domain.service.WithdrawCommissionService.rechazar;
import static com.factech.nexus.modules.commissions.domain.service.WithdrawCommissionService.ubicacion;

import com.factech.nexus.modules.commissions.application.CommissionBatchDetailResponse;
import com.factech.nexus.modules.commissions.domain.models.BatchStatus;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository.LockedBatch;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository.LockedCommission;
import com.factech.nexus.shared.audit.AuditEnums.ChangeAction;
import com.factech.nexus.shared.audit.AuditEvents.ChangeEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import com.factech.nexus.shared.time.BusinessCalendar;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Devolver a su lote pendiente una comisión retirada</b> (`RF-CM-023`, `RN-CM-046`): deshacer un
 * retiro hecho por error.
 *
 * <p><b>Es deshacer, no mover</b>: solo al lote del que salió, mientras él siga {@code PENDIENTE} y
 * ella siga en un lote {@code ABIERTO}. <b>Los dos lotes se bloquean en una sentencia ordenada por
 * identificador</b>, después de la comisión (`RF-CM-022` `plan.md` §1): aquí se conocen los dos de
 * antemano y no se abre nada. Contra el cierre (`FA-002`), el {@code FOR UPDATE} del abierto es el
 * mismo que toma el cierre.
 */
@Service
public class ReturnCommissionService {

  private static final String MODULO = "CM";
  private static final String ENTIDAD = "commissions";

  private final CommissionBatchRepository lotes;
  private final CommissionBatchQueryService consultas;
  private final BusinessCalendar calendario;
  private final AuditWriter auditoria;

  public ReturnCommissionService(
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
  public CommissionBatchDetailResponse giveBack(UUID batchId, UUID commissionId) {
    Optional<LockedCommission> bloqueada = lotes.lockCommission(commissionId);
    Set<UUID> ids = new HashSet<>();
    ids.add(batchId);
    bloqueada.ifPresent(c -> ids.add(c.batchId()));
    Map<UUID, LockedBatch> bloqueados =
        lotes.lockBatches(ids).stream()
            .collect(Collectors.toMap(LockedBatch::id, Function.identity()));

    LockedBatch origen = bloqueados.get(batchId);
    if (origen == null) {
      throw new ResourceNotFoundException(
          "EX-001", "No existe un lote de comisión con ese identificador.");
    }
    LockedCommission comision =
        bloqueada
            .filter(c -> batchId.equals(c.withdrawnFromBatchId()))
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "EX-002", "No existe esa comisión entre las retiradas de este lote."));
    if (!BatchStatus.PENDIENTE.name().equals(origen.status())) {
      rechazar("EX-003", "El lote ya está pagado: lo retirado se queda donde está.");
    }
    LockedBatch actual = bloqueados.get(comision.batchId());
    if (!BatchStatus.ABIERTO.name().equals(actual.status())) {
      rechazar("EX-004", "El lote en que está la comisión ya se cerró.");
    }

    OffsetDateTime ahora = calendario.ahora();
    lotes.moveCommission(commissionId, batchId, null);
    lotes.addToTotal(actual.id(), comision.amount().negate(), ahora);
    lotes.addToTotal(batchId, comision.amount(), ahora);

    auditoria.recordChange(
        new ChangeEvent(
            MODULO,
            ENTIDAD,
            commissionId,
            ChangeAction.UPDATE,
            Map.of(
                "before", ubicacion(actual.id(), batchId, comision),
                "after", ubicacion(batchId, null, comision))));

    return consultas.get(batchId, null);
  }
}
