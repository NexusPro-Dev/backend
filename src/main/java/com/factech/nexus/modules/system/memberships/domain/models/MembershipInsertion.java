package com.factech.nexus.modules.system.memberships.domain.models;

import java.util.List;
import java.util.UUID;

/**
 * Dónde queda la membresía nueva y qué hay que mover para que quepa.
 *
 * <p>Es el resultado de {@link MembershipChain#insertAbove(UUID)}: el dominio decide, y el
 * adaptador se limita a escribirlo. Esa separación es deliberada —`plan.md` §3— porque no hay
 * ninguna regla que evaluar por fila: cargar cada entidad afectada para modificarla produciría
 * tantas sentencias como membresías haya sin aportar nada.
 *
 * @param level nivel que ocupa la nueva membresía
 * @param parentId su superior; nulo cuando queda como cima de la cadena
 * @param childId su hija; nulo cuando queda en el extremo inferior
 * @param desplazadas membresías cuyo nivel cambia, con su valor anterior y el nuevo. Incluye a la
 *     reencadenada, que también baja un nivel
 * @param reencadenada hija cuya superior pasa a ser la nueva membresía; nula en `FA-001` y `FA-002`
 * @param superiorAnteriorDeLaReencadenada la que era su superior antes, para el diff de auditoría
 */
public record MembershipInsertion(
    int level,
    UUID parentId,
    UUID childId,
    List<LevelShift> desplazadas,
    UUID reencadenada,
    UUID superiorAnteriorDeLaReencadenada) {

  public MembershipInsertion {
    desplazadas = List.copyOf(desplazadas);
  }

  /** `FA-002` y `FA-001`: el alta que no toca ninguna otra fila. */
  public boolean sinReordenamiento() {
    return desplazadas.isEmpty();
  }
}
