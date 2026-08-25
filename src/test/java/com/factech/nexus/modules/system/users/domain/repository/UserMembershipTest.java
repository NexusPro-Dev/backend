package com.factech.nexus.modules.system.users.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La única definición de «vigente» del sistema (`RF-SP-032` · `T-01`).
 *
 * <p>Tres requerimientos la necesitan y el borde es lo que se implementa mal, de modo que las tres
 * pruebas del borde son el motivo de este archivo.
 */
class UserMembershipTest {

  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 8, 24, 12, 0, 0, 0, ZoneOffset.UTC);

  @Test
  @DisplayName("sin fecha es SIEMPRE vigente: nula significa indefinida, no desconocida")
  void indefinida() {
    assertThat(membresia(null).isCurrentAt(AHORA)).isTrue();
    assertThat(membresia(null).isCurrentAt(AHORA.plusYears(50))).isTrue();
  }

  @Test
  @DisplayName("una fecha futura está vigente")
  void futura() {
    assertThat(membresia(AHORA.plusSeconds(1)).isCurrentAt(AHORA)).isTrue();
  }

  @Test
  @DisplayName("EL BORDE: una fecha exactamente igual al instante consultado ya NO está vigente")
  void elBorde() {
    // `ends_at` es el momento en que la membresía deja de valer, no el último en
    // que vale. Con `>=` habría un instante —el de la propia frontera— en que
    // seguiría concediendo lo que ya expiró.
    assertThat(membresia(AHORA).isCurrentAt(AHORA)).isFalse();
    assertThat(membresia(AHORA.minusSeconds(1)).isCurrentAt(AHORA)).isFalse();
  }

  @Test
  @DisplayName("coincide comparando el INSTANTE, no el objeto: otro huso horario es la misma fecha")
  void mismoInstanteOtroHuso() {
    // Tratarlo como un cambio dejaría una fila de auditoría que no describe
    // ningún cambio, cada vez que un cliente enviara la fecha en su hora local.
    UUID id = UUID.randomUUID();
    UserMembership vigente = new UserMembership(id, "ORO", "Oro", (short) 1, AHORA.plusDays(1));

    assertThat(
            vigente.coincideCon(
                id, AHORA.plusDays(1).withOffsetSameInstant(ZoneOffset.ofHours(-5))))
        .isTrue();
  }

  @Test
  @DisplayName("indefinida y fechada NO coinciden, en ninguna de las dos direcciones")
  void indefinidaFrenteAFechada() {
    // Es `FA-003`: convertir una indefinida en fechada, y al revés, son cambios
    // reales y tienen que auditarse.
    UUID id = UUID.randomUUID();
    assertThat(membresia(null, id).coincideCon(id, AHORA)).isFalse();
    assertThat(membresia(AHORA, id).coincideCon(id, null)).isFalse();
    assertThat(membresia(null, id).coincideCon(id, null)).isTrue();
  }

  @Test
  @DisplayName("otra membresía nunca coincide, aunque la vigencia sea la misma")
  void otraMembresia() {
    assertThat(membresia(null).coincideCon(UUID.randomUUID(), null)).isFalse();
  }

  private static UserMembership membresia(OffsetDateTime hasta) {
    return membresia(hasta, UUID.randomUUID());
  }

  private static UserMembership membresia(OffsetDateTime hasta, UUID id) {
    return new UserMembership(id, "ORO", "Oro", (short) 1, hasta);
  }
}
