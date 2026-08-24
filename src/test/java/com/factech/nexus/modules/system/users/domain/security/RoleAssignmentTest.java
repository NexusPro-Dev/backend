package com.factech.nexus.modules.system.users.domain.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El delta de una asignación y de un retiro (`RF-SP-030` · `T-04`, `RF-SP-031` · `T-02`). */
class RoleAssignmentTest {

  private static final UUID A = UUID.randomUUID();
  private static final UUID B = UUID.randomUUID();
  private static final UUID C = UUID.randomUUID();

  @Test
  @DisplayName("la asignación es aditiva: solo devuelve lo que falta")
  void aditiva() {
    assertThat(RoleAssignment.aAgregar(Set.of(A), List.of(A, B))).containsExactly(B);
  }

  @Test
  @DisplayName("repetir la asignación no agrega nada, y eso NO es un error")
  void idempotente() {
    // De aquí depende que una petición repetida no deje un evento de auditoría
    // describiendo una asignación que ya existía.
    assertThat(RoleAssignment.aAgregar(Set.of(A, B), List.of(A, B))).isEmpty();
  }

  @Test
  @DisplayName("los duplicados de la entrada se colapsan")
  void duplicados() {
    assertThat(RoleAssignment.aAgregar(Set.of(), List.of(A, A, A))).containsExactly(A);
  }

  @Test
  @DisplayName("el retiro es sustractivo: ignora lo que la persona no tiene")
  void sustractiva() {
    // `FA-001` de `RF-SP-031`: retirar un rol que no se tiene afecta cero filas
    // y no es un error.
    assertThat(RoleAssignment.aRetirar(Set.of(A), List.of(A, B))).containsExactly(A);
    assertThat(RoleAssignment.aRetirar(Set.of(A), List.of(B, C))).isEmpty();
  }

  @Test
  @DisplayName("el resultado describe el conjunto que queda, que es lo que la respuesta devuelve")
  void resultado() {
    assertThat(RoleAssignment.resultado(Set.of(A), Set.of(B))).containsExactlyInAnyOrder(A, B);
    assertThat(RoleAssignment.resultadoTrasRetirar(Set.of(A, B), Set.of(A))).containsExactly(B);
  }

  @Test
  @DisplayName("el orden de llegada se conserva: el mismo delta produce el mismo evento")
  void deterministaEnElOrden() {
    // No es cosmético: los códigos que viajan al registro de auditoría se
    // derivan de este conjunto, y un orden inestable haría que dos ejecuciones
    // idénticas dejaran eventos distintos.
    assertThat(RoleAssignment.aAgregar(Set.of(), List.of(C, A, B))).containsExactly(C, A, B);
  }
}
