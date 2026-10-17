package com.factech.nexus.modules.movements.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lo que `MV` publica para <b>llevar la oficina de lo vendido</b> cuando un vendedor cambia de
 * superior (`RN-MV-080`, 16-10-2026). La llama `CM`, que sabe dónde empieza el periodo abierto
 * (`RN-CM-055`).
 */
public interface SaleLineTeamTransfer {

  /**
   * Pone en su oficina vigente las líneas del vendedor y de su red vendidas desde {@code since}.
   * Una línea cuyo vendedor no tiene oficina hoy conserva la suya.
   *
   * @return cuántas líneas cambiaron de oficina
   */
  int transfer(UUID sellerId, OffsetDateTime since);
}
