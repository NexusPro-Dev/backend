package com.factech.nexus.modules.system.users.domain.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.modules.system.roles.domain.models.RoleType;
import com.factech.nexus.modules.system.users.domain.repository.AssignableRole;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `RN-SP-013` y `RN-SP-018` (`RF-SP-032` · `T-02`).
 *
 * <p>Una condición de una línea con cuatro clientes, dos de los cuales deciden en direcciones
 * <b>opuestas</b>. Estas cuatro pruebas son lo que impide que la quinta copia la invierta.
 */
class ConsumerStatusTest {

  @Test
  @DisplayName("basta UN rol de consumidor entre varios de otras clasificaciones")
  void bastaUno() {
    assertThat(
            ConsumerStatus.esConsumidor(
                List.of(
                    rol(RoleType.FUNCIONARIO), rol(RoleType.CONSUMIDOR), rol(RoleType.VENDEDOR))))
        .isTrue();
  }

  @Test
  @DisplayName("los demás roles no le quitan la condición")
  void soloConsumidor() {
    assertThat(ConsumerStatus.esConsumidor(List.of(rol(RoleType.CONSUMIDOR)))).isTrue();
  }

  @Test
  @DisplayName("sin ningún rol de consumidor, no lo es")
  void ninguno() {
    assertThat(
            ConsumerStatus.esConsumidor(List.of(rol(RoleType.FUNCIONARIO), rol(RoleType.VENDEDOR))))
        .isFalse();
  }

  @Test
  @DisplayName("sin roles tampoco lo es: `RF-SP-033` depende de que este caso sea falso")
  void sinRoles() {
    // Es el caso que la cascada de `RF-SP-031` deja detrás y el que `RF-SP-033`
    // existe para atender. Si aquí devolviera verdadero, la única operación que
    // corrige un estado incoherente lo rechazaría siempre.
    assertThat(ConsumerStatus.esConsumidor(List.of())).isFalse();
  }

  private static AssignableRole rol(RoleType tipo) {
    return new AssignableRole(
        UUID.randomUUID(), tipo.name(), tipo.name(), tipo, false, true, null, Set.of());
  }
}
