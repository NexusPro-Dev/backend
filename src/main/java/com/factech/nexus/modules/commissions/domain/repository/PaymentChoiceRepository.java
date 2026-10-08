package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.PaymentMode;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Puerto de las elecciones de cómo se paga cada cierre programado (`RN-CM-054`). */
public interface PaymentChoiceRepository {

  /** La elección hecha para ese turno, si alguien eligió. */
  Optional<PaymentChoice> find(OffsetDateTime scheduledFor);

  /**
   * Escribe la elección del turno, o reescribe la que había: gana la última.
   *
   * @return el identificador de la fila, que no cambia entre elecciones del mismo turno
   */
  UUID upsert(
      UUID id, OffsetDateTime scheduledFor, PaymentMode mode, UUID chosenBy, OffsetDateTime at);

  /**
   * Bloqueo consultivo de transacción <b>del turno</b>: lo toman elegir y abrir el cierre de ese
   * turno, para que una elección nunca se acepte sin que el cierre la lea (`CA-CM-389`).
   */
  void lockTurn(OffsetDateTime scheduledFor);

  record PaymentChoice(
      OffsetDateTime scheduledFor, PaymentMode mode, UUID chosenBy, OffsetDateTime chosenAt) {}
}
