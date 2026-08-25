package com.factech.nexus.modules.system.auth.domain.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La progresión del bloqueo y su techo (`RF-SP-034` · `T-03`).
 *
 * <p>La prueba del <b>techo</b> es la única que falla cuando alguien lo quita, y es la que decide
 * si esto es una defensa o una denegación de servicio contra el titular de la cuenta.
 */
class LockoutPolicyTest {

  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 8, 24, 12, 0, 0, 0, ZoneOffset.UTC);

  private final LockoutPolicy politica =
      new LockoutPolicy(5, Duration.ofMinutes(1), Duration.ofHours(1));

  @Test
  @DisplayName("los primeros fallos solo cuentan: no bloquean")
  void antesDelUmbral() {
    for (int intento = 1; intento < 5; intento++) {
      assertThat(politica.bloqueoTras(intento, AHORA)).isEmpty();
    }
  }

  @Test
  @DisplayName("al quinto se bloquea, y la espera CRECE con cada fallo posterior")
  void progresion() {
    assertThat(politica.bloqueoTras(5, AHORA)).contains(AHORA.plusMinutes(1));
    assertThat(politica.bloqueoTras(6, AHORA)).contains(AHORA.plusMinutes(2));
    assertThat(politica.bloqueoTras(7, AHORA)).contains(AHORA.plusMinutes(4));
    assertThat(politica.bloqueoTras(8, AHORA)).contains(AHORA.plusMinutes(8));
  }

  @Test
  @DisplayName("EL TECHO: a partir de cierto punto la espera deja de crecer")
  void techo() {
    // Sin techo, alguien puede mantener la cuenta de otra persona bloqueada
    // indefinidamente provocando fallos a propósito. La progresión castiga al
    // atacante; el techo protege al titular.
    assertThat(politica.bloqueoTras(20, AHORA)).contains(AHORA.plusHours(1));
    assertThat(politica.bloqueoTras(500, AHORA)).contains(AHORA.plusHours(1));
  }

  @Test
  @DisplayName("un número de intentos desmesurado no desborda ni deja de aplicar el techo")
  void sinDesbordamiento() {
    // El desplazamiento de bits sin tope daría un factor negativo, y con él una
    // fecha en el pasado: la cuenta quedaría desbloqueada justo cuando más
    // fallos acumula.
    assertThat(politica.bloqueoTras(Integer.MAX_VALUE, AHORA)).contains(AHORA.plusHours(1));
  }
}
