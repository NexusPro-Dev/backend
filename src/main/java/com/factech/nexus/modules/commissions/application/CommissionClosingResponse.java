package com.factech.nexus.modules.commissions.application;

import com.factech.nexus.modules.commissions.domain.repository.CommissionClosingRepository.ClosingRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * La constancia de un cierre del periodo (`RF-CM-009`).
 *
 * <p><b>{@code closedAt} nulo</b> es un cierre que corre ahora mismo, o que falló entre el barrido
 * y el cierre de los lotes (`EX-002`): sus lotes siguen abiertos y los cerrará el siguiente.
 * <b>{@code linesSwept} es el número que hay que vigilar</b>: si no es cero, el aviso de `MV` se
 * está perdiendo y el barrido lo está tapando.
 *
 * <p><b>{@code paymentMode}</b> es cómo se pagó lo que cerró (`RN-CM-053`, 08-10-2026): presente
 * solo en los programados, con cuántos lotes pagó el pago automático y cuántos se quedaron
 * pendientes.
 */
@Schema(name = "CommissionClosingResponse")
public record CommissionClosingResponse(
    UUID id,
    String origin,
    OffsetDateTime scheduledFor,
    UUID triggeredBy,
    OffsetDateTime startedAt,
    OffsetDateTime closedAt,
    int batchesClosed,
    int linesSwept,
    int linesRetried,
    int linesRecovered,
    String paymentMode,
    int batchesPaid,
    int batchesNotPaid) {

  public static CommissionClosingResponse from(ClosingRow fila) {
    return new CommissionClosingResponse(
        fila.id(),
        fila.origin().name(),
        fila.scheduledFor(),
        fila.triggeredBy(),
        fila.startedAt(),
        fila.closedAt(),
        fila.batchesClosed(),
        fila.linesSwept(),
        fila.linesRetried(),
        fila.linesRecovered(),
        fila.paymentMode() == null ? null : fila.paymentMode().name(),
        fila.batchesPaid(),
        fila.batchesNotPaid());
  }
}
