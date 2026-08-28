package com.factech.nexus.modules.system.audit.application;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Quién hizo la acción, resuelto (`RF-SP-011` a `RF-SP-014`).
 *
 * <p>Hasta el 28-08-2026 los cuatro registros devolvían <b>solo</b> {@code actorId}, y la razón
 * escrita era buena: el valor probatorio está en el identificador, que no cambia nunca, mientras
 * que un nombre es una foto del momento en que se <b>consulta</b> y no del momento en que
 * <b>ocurrió</b> el evento. Esa objeción no desaparece por añadir este objeto; se acota, y así
 * queda declarado:
 *
 * <ul>
 *   <li><b>{@code username} es inmutable</b> (`RN-SP-016`), de modo que dice hoy exactamente lo
 *       mismo que decía cuando ocurrió el evento. Es la identidad que la auditoría referencia.
 *   <li><b>{@code fullName} es el ACTUAL y no el de entonces.</b> `RF-SP-027` permite corregirlo, y
 *       este endpoint no guarda el de aquel día. Se devuelve porque la alternativa es que el
 *       frontend pida {@code /users/{id}} fila por fila —el problema de las {@code N+1} consultas
 *       con peor forma, porque además viaja por la red—, y no porque sea evidencia.
 * </ul>
 *
 * <p><b>{@code actorId} sigue viajando, y es el que manda.</b> Este objeto es aditivo: quien ya
 * consumía el identificador no se entera del cambio.
 *
 * <h2>Cómo se lee la ausencia</h2>
 *
 * <p>Son dos ausencias distintas y se distinguen sin ambigüedad, sin necesidad de un campo que lo
 * diga:
 *
 * <ul>
 *   <li>{@code actorId} nulo y {@code actor} nulo: <b>lo hizo el sistema</b> —una migración, una
 *       tarea programada— (Art. V.15).
 *   <li>{@code actorId} con valor y {@code actor} nulo: <b>la persona ya no está en la tabla</b>.
 *       No debería ocurrir con la eliminación de `RF-SP-029`, que es lógica y deja la fila; el caso
 *       queda cubierto porque {@code actor_id} <b>no tiene clave foránea a propósito</b> — el
 *       evento debe sobrevivir a la persona.
 * </ul>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AuditActor(String username, String fullName) {

  /**
   * Arma el actor a partir de las columnas que trajo la <b>misma</b> sentencia.
   *
   * <p>Devuelve {@code null} cuando el {@code LEFT JOIN} no encontró fila, que es lo que hace
   * distinguibles las dos ausencias de arriba.
   */
  public static AuditActor de(String username, String nombre, String apellido) {
    if (username == null) {
      return null;
    }
    String completo =
        ((nombre == null ? "" : nombre) + " " + (apellido == null ? "" : apellido)).trim();
    return new AuditActor(username, completo.isEmpty() ? null : completo);
  }
}
