package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.EmptyBatchesDeletionResponse;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Borrar los lotes vacíos</b>, todos a la vez (`RF-CM-027`, `RN-CM-052`).
 *
 * <p><b>La búsqueda no decide; decide el {@code DELETE}</b> (`plan.md` §1). La lista se toma sin
 * bloquear y puede envejecer: un candidato puede recibir una comisión antes de que se bloquee. Por
 * eso cada lote se borra con {@link CommissionBatchRepository#deleteIfEmpty}, que vuelve a mirar en
 * su propia sentencia, con el lote ya bloqueado.
 *
 * <p><b>El orden de los bloqueos es el del módulo</b>: primero las comisiones retiradas de los
 * candidatos —borrar un pendiente les escribe {@code withdrawn_from_batch_id}, y devolver bloquea
 * la comisión antes que los lotes—, después los lotes por identificador.
 */
@Service
public class DeleteEmptyBatchesService {

  private final CommissionBatchRepository lotes;
  private final EmptyBatchRemoval vacios;

  public DeleteEmptyBatchesService(CommissionBatchRepository lotes, EmptyBatchRemoval vacios) {
    this.lotes = lotes;
    this.vacios = vacios;
  }

  @Transactional
  public EmptyBatchesDeletionResponse deleteAll() {
    List<UUID> candidatos = lotes.findEmptyUnpaidBatchIds();
    if (candidatos.isEmpty()) {
      return EmptyBatchesDeletionResponse.de(List.of());
    }
    lotes.lockWithdrawnFrom(candidatos);
    lotes.lockBatches(candidatos);
    return EmptyBatchesDeletionResponse.de(vacios.removeIfEmpty(candidatos));
  }
}
