package com.factech.nexus.modules.system.users.domain.security;

import java.util.UUID;

/**
 * `RN-SP-017`: <b>nadie ejecuta sobre su propia cuenta</b> una operación administrativa.
 *
 * <p>La regla protege dos cosas a la vez, y la segunda es la menos obvia. Impide que alguien se
 * desactive, se elimine o se reasigne por error irreversible; y sobre todo impide que <b>una sola
 * persona pueda alterar su posición sin que nadie más intervenga</b>, que es la condición mínima
 * para que la auditoría de esos cambios sirva de algo: si el afectado y el actor son la misma
 * persona, el registro no documenta una decisión, documenta una preferencia.
 *
 * <p><b>Devuelve un booleano y no lanza</b>, y el motivo es que los planes aprobados <b>no
 * coinciden en el código HTTP</b>: `RF-SP-028` §4 y `RF-SP-029` §4 la traducen a {@code 403} —«es
 * una prohibición sobre quién ejecuta: el mismo cuerpo enviado por otro actor sería válido»— y
 * `RF-SP-041` §4 la traduce a {@code 409}. La comparación y su razón viven aquí, en un solo sitio;
 * la elección del estado la hace cada caso de uso según su contrato.
 *
 * <p>La divergencia queda declarada como tal en `tasks.md` de `RF-SP-041`: es una discrepancia
 * entre planes aprobados, no una decisión de implementación, y se resuelve al reabrirse alguno de
 * los dos.
 */
public final class SelfOperationGuard {

  private SelfOperationGuard() {}

  /** ¿El actor está operando sobre su propia cuenta? */
  public static boolean esSuPropiaCuenta(UUID actor, UUID objetivo) {
    return actor != null && actor.equals(objetivo);
  }
}
