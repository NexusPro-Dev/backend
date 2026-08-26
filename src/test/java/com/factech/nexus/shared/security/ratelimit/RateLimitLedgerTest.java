package com.factech.nexus.shared.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El contador del límite de tasa (issue #21).
 *
 * <p>Con un reloj fijado a mano, que es la única forma de comprobar una ventana deslizante sin
 * dormir el hilo: una prueba que espera un minuto real acaba borrándose.
 */
class RateLimitLedgerTest {

  private static final Duration MINUTO = Duration.ofMinutes(1);

  @Test
  @DisplayName("admite hasta el máximo y rechaza el siguiente")
  void cotaSimple() {
    Reloj reloj = new Reloj(Instant.parse("2026-08-25T10:00:00Z"));
    RateLimitLedger contador = ledger(reloj, 1000);

    for (int i = 0; i < 3; i++) {
      assertThat(contador.registrar("ip|1.1.1.1", 3, MINUTO).admitida()).isTrue();
    }
    assertThat(contador.registrar("ip|1.1.1.1", 3, MINUTO).admitida()).isFalse();
  }

  @Test
  @DisplayName("la ventana DESLIZA: no se renueva de golpe al cambiar de minuto")
  void ventanaDeslizante() {
    Reloj reloj = new Reloj(Instant.parse("2026-08-25T10:00:30Z"));
    RateLimitLedger contador = ledger(reloj, 1000);

    // Tres peticiones a las 10:00:30.
    for (int i = 0; i < 3; i++) {
      contador.registrar("ip|1.1.1.1", 3, MINUTO);
    }

    // A las 10:01:00 —minuto nuevo de reloj— una ventana FIJA habría admitido
    // otras tres, y eso es exactamente la ráfaga del doble que se quiere evitar.
    reloj.avanzar(Duration.ofSeconds(30));
    assertThat(contador.registrar("ip|1.1.1.1", 3, MINUTO).admitida()).isFalse();

    // Pasado un minuto desde AQUELLAS tres, vuelve a caber.
    reloj.avanzar(Duration.ofSeconds(31));
    assertThat(contador.registrar("ip|1.1.1.1", 3, MINUTO).admitida()).isTrue();
  }

  @Test
  @DisplayName("la petición rechazada NO se apunta: insistir no perpetúa el castigo")
  void elRechazoNoRenuevaLaVentana() {
    Reloj reloj = new Reloj(Instant.parse("2026-08-25T10:00:00Z"));
    RateLimitLedger contador = ledger(reloj, 1000);

    contador.registrar("ip|1.1.1.1", 1, MINUTO);

    // Insiste durante cincuenta segundos.
    for (int i = 0; i < 5; i++) {
      reloj.avanzar(Duration.ofSeconds(10));
      assertThat(contador.registrar("ip|1.1.1.1", 1, MINUTO).admitida()).isFalse();
    }

    // Al cumplirse el minuto de la ÚNICA admitida, vuelve a caber. Si los
    // rechazos se apuntaran, la ventana se habría renovado con cada intento y
    // quien insiste no podría volver nunca.
    reloj.avanzar(Duration.ofSeconds(11));
    assertThat(contador.registrar("ip|1.1.1.1", 1, MINUTO).admitida()).isTrue();
  }

  @Test
  @DisplayName("dice cuántos segundos faltan, y nunca cero")
  void esperaDeclarada() {
    Reloj reloj = new Reloj(Instant.parse("2026-08-25T10:00:00Z"));
    RateLimitLedger contador = ledger(reloj, 1000);

    contador.registrar("ip|1.1.1.1", 1, MINUTO);
    reloj.avanzar(Duration.ofSeconds(20));

    RateLimitLedger.Veredicto veredicto = contador.registrar("ip|1.1.1.1", 1, MINUTO);
    assertThat(veredicto.admitida()).isFalse();
    assertThat(veredicto.esperaSegundos()).isEqualTo(40);

    // Al filo del vencimiento la espera se redondea hacia arriba: un
    // `Retry-After: 0` le diría al cliente que reintente ya, y volvería a
    // recibir el rechazo.
    reloj.avanzar(Duration.ofMillis(39_900));
    assertThat(contador.registrar("ip|1.1.1.1", 1, MINUTO).esperaSegundos()).isEqualTo(1);
  }

  @Test
  @DisplayName("las llaves son independientes entre sí")
  void llavesIndependientes() {
    RateLimitLedger contador = ledger(new Reloj(Instant.parse("2026-08-25T10:00:00Z")), 1000);

    contador.registrar("ip|1.1.1.1", 1, MINUTO);

    // Otro origen, y el mismo origen sobre otra ruta, no heredan el consumo.
    assertThat(contador.registrar("ip|2.2.2.2", 1, MINUTO).admitida()).isTrue();
    assertThat(contador.registrar("login|ip|1.1.1.1", 1, MINUTO).admitida()).isTrue();
  }

  @Test
  @DisplayName("la memoria está acotada: la llave la elige quien llama")
  void capacidadAcotada() {
    RateLimitLedger contador = ledger(new Reloj(Instant.parse("2026-08-25T10:00:00Z")), 10);

    // Mil orígenes distintos, capacidad diez. Sin tope, esto es un consumo de
    // memoria que el atacante controla — la defensa se convertiría en el ataque.
    for (int i = 0; i < 1000; i++) {
      contador.registrar("ip|10.0.0." + i, 1, MINUTO);
    }

    // Lo antiguo se desaloja, de modo que la primera llave vuelve a caber. Es la
    // consecuencia aceptada de acotar: rotar direcciones regala una ventana
    // limpia, y es preferible a quedarse sin memoria.
    assertThat(contador.registrar("ip|10.0.0.0", 1, MINUTO).admitida()).isTrue();
    // Y lo reciente sigue contado.
    assertThat(contador.registrar("ip|10.0.0.999", 1, MINUTO).admitida()).isFalse();
  }

  private static RateLimitLedger ledger(Clock reloj, int capacidad) {
    return new RateLimitLedger(
        new RateLimitSettings(true, capacidad, null, null, null, null), reloj);
  }

  /** Reloj que avanza cuando la prueba lo dice, y no cuando pasa el tiempo. */
  private static final class Reloj extends Clock {

    private Instant ahora;

    private Reloj(Instant inicio) {
      this.ahora = inicio;
    }

    void avanzar(Duration cuanto) {
      ahora = ahora.plus(cuanto);
    }

    @Override
    public Instant instant() {
      return ahora;
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zona) {
      return this;
    }
  }
}
