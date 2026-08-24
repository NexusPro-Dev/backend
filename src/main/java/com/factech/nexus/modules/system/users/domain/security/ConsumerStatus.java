package com.factech.nexus.modules.system.users.domain.security;

import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import java.util.Collection;

/**
 * `RN-SP-013` y `RN-SP-018`: <b>¿esta persona es consumidor?</b>
 *
 * <p>Una sola línea, y aun así merece un sitio propio: <b>cuatro</b> casos de uso preguntan lo
 * mismo —asignar roles, retirarlos, asignar membresía y retirarla— y dos de ellos deciden en
 * direcciones <b>opuestas</b>. `RF-SP-032` exige que la persona SÍ sea consumidor; `RF-SP-033`
 * exige que NO lo sea. Con la condición escrita cuatro veces, invertir una por descuido produce un
 * rechazo que parece un defecto del sistema y un permiso que no debería concederse, y ninguna de
 * las dos cosas falla en la otra mitad.
 *
 * <p>Basta <b>un</b> rol de clasificación {@code CONSUMIDOR} entre los que la persona porta; los
 * demás no le quitan la condición.
 */
public final class ConsumerStatus {

  private ConsumerStatus() {}

  public static boolean esConsumidor(Collection<AssignableRole> roles) {
    return roles.stream().anyMatch(AssignableRole::esConsumidor);
  }
}
