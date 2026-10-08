package com.factech.nexus.modules.commissions.domain.service;

import com.factech.nexus.modules.commissions.application.EmptyBatchesDeletionResponse;
import com.factech.nexus.modules.commissions.domain.models.BatchStatus;
import com.factech.nexus.modules.commissions.domain.repository.CommissionBatchRepository;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Borrar los lotes vacíos</b> (`RF-CM-027`, `RN-CM-052`): todos a la vez por la orden, los
 * abiertos al cerrar (`RF-CM-009`) y los pendientes tras un pago (`RF-CM-011`, `RF-CM-025`).
 *
 * <p><b>La búsqueda no decide; decide el {@code DELETE}</b> (`plan.md` §1). La lista se toma sin
 * bloquear y puede envejecer: un candidato puede recibir una comisión antes de que se bloquee. Por
 * eso cada lote se borra con {@link CommissionBatchRepository#deleteIfEmpty}, que vuelve a mirar en
 * su propia sentencia, con el lote ya bloqueado.
 *
 * <p><b>El orden de los bloqueos es el del módulo</b>: primero las comisiones retiradas de los
 * candidatos —borrar un pendiente les escribe {@code withdrawn_from_batch_id}, y devolver bloquea
 * la comisión antes que los lotes—, después los lotes por identificador.
 *
 * <p>{@code REQUIRED}: el cierre lo llama dentro de su transacción, y lo borrado vuelve si el
 * cierre falla (`RF-CM-009` `plan.md` §15).
 */
@Service
public class DeleteEmptyBatchesService {

  private static final String MOTIVO =
      "RN-CM-052: el lote no tenía comisiones y se borró a mano (RF-CM-027).";

  private final CommissionBatchRepository lotes;
  private final EmptyBatchRemoval vacios;

  public DeleteEmptyBatchesService(CommissionBatchRepository lotes, EmptyBatchRemoval vacios) {
    this.lotes = lotes;
    this.vacios = vacios;
  }

  /** La orden de `RF-CM-027`: los abiertos y los pendientes. */
  @Transactional
  public EmptyBatchesDeletionResponse deleteAll() {
    return deleteEmpty(MOTIVO, BatchStatus.ABIERTO, BatchStatus.PENDIENTE);
  }

  /**
   * Borra los lotes vacíos <b>en esos estados</b>, con ese motivo en la auditoría (`plan.md` §12).
   */
  @Transactional
  public EmptyBatchesDeletionResponse deleteEmpty(String motivo, BatchStatus... estados) {
    List<UUID> candidatos =
        lotes.findEmptyUnpaidBatchIds(Arrays.stream(estados).map(BatchStatus::name).toList());
    if (candidatos.isEmpty()) {
      return EmptyBatchesDeletionResponse.de(List.of());
    }
    lotes.lockWithdrawnFrom(candidatos);
    lotes.lockBatches(candidatos);
    return EmptyBatchesDeletionResponse.de(vacios.removeIfEmpty(candidatos, motivo));
  }
}
