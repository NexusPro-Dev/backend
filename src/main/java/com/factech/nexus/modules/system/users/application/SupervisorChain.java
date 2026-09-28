package com.factech.nexus.modules.system.users.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * <b>La cadena de mando de una persona en un instante</b> (`requirements/sp.md` v1.88.0 §8; D-25).
 *
 * <p>La pide `CM` para devengar (`RN-CM-025`): cada nivel de la cadena <b>del día de la venta</b>
 * cobra su comisión, y `CM` no lee {@code user_supervisors}. Va <b>en la dirección contraria a
 * {@link CommercialReach}</b>: aquella baja por la red vigente; esta sube por el historial, con las
 * filas vigentes en ese instante, cerradas incluidas. Responder «mis superiores de hace tres meses»
 * con la estructura de hoy haría que ascender a alguien reescribiera a quién se le debió una venta.
 */
public interface SupervisorChain {

  /**
   * La persona primero, después su superior en ese instante, y así hasta quien no lo tiene.
   *
   * @param userId la persona de la que se parte; un nulo devuelve la lista vacía
   * @param at el instante; una fila rige si {@code started_at <= at} y {@code ended_at} es nulo o
   *     posterior
   * @return nunca vacía para una persona dada: al menos ella misma
   */
  List<UUID> chainAt(UUID userId, OffsetDateTime at);
}
