package com.factech.nexus.modules.system.users.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A quién avisa `SP` cuando un vendedor <b>cambia de superior</b> (`RN-SP-083`, 16-10-2026).
 *
 * <p>Invierte la dirección, como {@link FirstDepositActivation}: `SP` es la raíz del grafo y no
 * importa a nadie. Lo implementa `CM`, dueño de los cierres, que sabe dónde empieza el periodo
 * abierto (`RN-CM-055`) y pide a `MV` llevar la oficina de lo vendido desde ahí (`RN-MV-080`).
 */
public interface SupervisorChangeFollower {

  /**
   * Se llama <b>dentro de la transacción del cambio</b> y solo cuando el superior cambia de verdad:
   * si falla, el superior no cambia.
   *
   * @param userId quien cambió de superior; su red va con él
   * @param at el instante del cambio
   */
  void supervisorChanged(UUID userId, OffsetDateTime at);
}
