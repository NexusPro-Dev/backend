package com.factech.nexus.modules.system.auth.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.factech.nexus.modules.system.auth.domain.service.FailedAttemptLedger.Fallos;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * El contador de los identificadores sin cuenta (`RF-SP-034`).
 *
 * <p>Lo que se prueba aquí no es una defensa contra la fuerza bruta —esa vive en la base de datos—,
 * sino que el identificador inventado se comporte <b>igual</b> que uno real. Cada prueba
 * corresponde a una forma concreta de que dejara de comportarse así.
 */
class FailedAttemptLedgerTest {

  private static final OffsetDateTime AHORA =
      OffsetDateTime.of(2026, 8, 25, 10, 0, 0, 0, ZoneOffset.UTC);

  private static final Duration VENTANA = Duration.ofHours(1);
  private static final Duration TECHO = Duration.ofHours(1);

  private final FailedAttemptLedger registro = new FailedAttemptLedger(VENTANA, 3, TECHO);

  @Test
  @DisplayName("un identificador del que no se sabe nada no tiene fallos ni bloqueo")
  void sinNoticias() {
    assertThat(registro.consultar("nadie", AHORA)).isEqualTo(Fallos.NINGUNO);
  }

  @Test
  @DisplayName("cuenta los fallos y conserva el bloqueo que se le anota")
  void cuentaYBloquea() {
    OffsetDateTime hasta = AHORA.plusMinutes(1);
    registro.registrarFallo("nadie", 5, hasta, AHORA);

    Fallos fallos = registro.consultar("nadie", AHORA);
    assertThat(fallos.intentos()).isEqualTo(5);
    assertThat(fallos.bloqueado(AHORA)).isTrue();
    assertThat(fallos.bloqueado(hasta)).isFalse();
  }

  @Test
  @DisplayName("la caja del identificador NO abre un contador nuevo")
  void mismaCajaQueLaBusquedaDeLaCuenta() {
    // `findByIdentifier` compara el nombre de usuario sin distinguir
    // mayúsculas. Si aquí se distinguieran, alternar la caja daría intentos
    // infinitos sobre un identificador inventado y ninguno sobre uno real —que
    // es exactamente la diferencia observable que este registro borra—.
    registro.registrarFallo("JPerez", 1, null, AHORA);
    registro.registrarFallo("  jperez ", 2, null, AHORA);

    assertThat(registro.consultar("JPEREZ", AHORA).intentos()).isEqualTo(2);
  }

  @Test
  @DisplayName("la entrada se olvida al cerrarse la ventana")
  void laVentanaCaduca() {
    registro.registrarFallo("nadie", 4, null, AHORA);

    assertThat(registro.consultar("nadie", AHORA.plus(VENTANA).minusSeconds(1)).intentos())
        .isEqualTo(4);
    assertThat(registro.consultar("nadie", AHORA.plus(VENTANA))).isEqualTo(Fallos.NINGUNO);
  }

  @Test
  @DisplayName("la ventana NUNCA queda por debajo del techo del bloqueo")
  void laVentanaNoPuedeLevantarUnBloqueoVigente() {
    // Con una ventana más corta que el techo, la entrada caducaría con su
    // propio bloqueo todavía vigente: el identificador inventado volvería a
    // contar desde cero mientras la cuenta real sigue bloqueada, y la
    // diferencia sería visible desde fuera.
    FailedAttemptLedger malConfigurado =
        new FailedAttemptLedger(Duration.ofMinutes(1), 10, Duration.ofHours(1));

    OffsetDateTime hasta = AHORA.plusMinutes(59);
    malConfigurado.registrarFallo("nadie", 5, hasta, AHORA);

    Fallos fallos = malConfigurado.consultar("nadie", AHORA.plusMinutes(30));
    assertThat(fallos.bloqueado(AHORA.plusMinutes(30))).isTrue();
  }

  @Nested
  @DisplayName("el tope de entradas")
  class Capacidad {

    @Test
    @DisplayName("lleno de entradas VIGENTES, deja de anotar en lugar de desalojar")
    void llenoDeVigentes() {
      registro.registrarFallo("uno", 1, null, AHORA);
      registro.registrarFallo("dos", 1, null, AHORA);
      registro.registrarFallo("tres", 1, null, AHORA);

      registro.registrarFallo("cuatro", 1, null, AHORA);

      // Desalojando, quien inunda el registro elegiría qué se olvida. Se paga
      // dejando de contar al cuarto, que es la mitad menos mala.
      assertThat(registro.consultar("cuatro", AHORA)).isEqualTo(Fallos.NINGUNO);
      assertThat(registro.consultar("uno", AHORA).intentos()).isEqualTo(1);
    }

    @Test
    @DisplayName("lleno de entradas CADUCADAS, las purga y sigue contando")
    void llenoDeCaducadas() {
      registro.registrarFallo("uno", 1, null, AHORA);
      registro.registrarFallo("dos", 1, null, AHORA);
      registro.registrarFallo("tres", 1, null, AHORA);

      OffsetDateTime despues = AHORA.plus(VENTANA).plusMinutes(1);
      registro.registrarFallo("cuatro", 1, null, despues);

      assertThat(registro.consultar("cuatro", despues).intentos()).isEqualTo(1);
    }

    @Test
    @DisplayName("un identificador YA anotado se actualiza aunque el registro esté lleno")
    void elQueYaEstaNoDependeDelTope() {
      registro.registrarFallo("uno", 1, null, AHORA);
      registro.registrarFallo("dos", 1, null, AHORA);
      registro.registrarFallo("tres", 1, null, AHORA);

      registro.registrarFallo("uno", 2, null, AHORA);

      assertThat(registro.consultar("uno", AHORA).intentos()).isEqualTo(2);
    }
  }
}
