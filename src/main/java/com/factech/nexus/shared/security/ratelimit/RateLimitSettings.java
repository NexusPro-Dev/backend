package com.factech.nexus.shared.security.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Las cotas del límite de tasa (`security.md` §5.5, issue #21).
 *
 * <p><b>{@code @ConfigurationProperties} y no once {@code @Value}</b>, que es lo que el resto del
 * sistema usa: aquí no hay un valor suelto sino tres políticas con la misma forma, y once
 * anotaciones sueltas en un constructor invitan a cruzar dos de ellas sin que el compilador lo
 * note. Cuando lo que se configura es una estructura, se declara como estructura.
 *
 * <p><b>Los números salen de la naturaleza de cada endpoint</b>, no de una cifra redonda:
 *
 * <ul>
 *   <li><b>Login — 10/min por origen y 5/min por credencial.</b> Una persona que teclea mal su
 *       contraseña reintenta dos o tres veces; diez por minuto desde una misma IP ya es una
 *       herramienta. La cota por credencial es más estricta que la del origen a propósito: el
 *       rociado de contraseñas reparte los intentos entre muchas cuentas y **no** dispara el
 *       bloqueo de ninguna, de modo que la única defensa es el origen; y quien ataca una cuenta
 *       concreta topa antes con los cinco intentos del bloqueo, que es lo correcto.
 *   <li><b>Refresco — 60/min por origen.</b> Un cliente legítimo refresca cada quince minutos; el
 *       margen es enorme a propósito, porque una interfaz con varias pestañas abiertas puede
 *       refrescar en ráfaga. Lo que se corta es el bucle: el endpoint es público y **consulta la
 *       base de datos en cada llamada**.
 *   <li><b>Recuperación — 3/hora por identidad y 10/hora por origen.</b> Es la cota más estricta de
 *       las tres y `security.md` §5.5 explica por qué: es la única operación pública que provoca un
 *       envío saliente, de modo que sin ella se puede inundar de correos a una persona real —lo que
 *       es acoso, y quema la reputación del dominio de envío— y sondear identidades en masa. Tres
 *       al día bastan para quien de verdad olvidó su contraseña.
 * </ul>
 *
 * <p><b>La cota de recuperación está declarada y todavía no se aplica</b>, porque el endpoint no
 * existe: `RF-SP-040` está bloqueado por **D-23**. La regla se registra igual para que el día que
 * exista no dependa de que alguien recuerde añadirla.
 *
 * @param enabled permite apagarlo. Existe para las pruebas de otros requerimientos, que emiten
 *     ráfagas contra el login a propósito; en un entorno real no debería tocarse
 * @param capacity cuántas llaves distintas se recuerdan a la vez. La llave la elige quien llama —su
 *     IP, su identificador—, de modo que un mapa sin tope crece hasta donde quiera el atacante
 */
@ConfigurationProperties(prefix = "nexus.security.rate-limit")
public record RateLimitSettings(
    boolean enabled, int capacity, Politica login, Politica refresh, Politica recovery) {

  /**
   * Una política: cuántas peticiones por ventana, por origen y por identidad.
   *
   * @param porOrigen máximo por dirección de red; nulo significa que este endpoint no lo acota
   * @param porIdentidad máximo por credencial o identidad declarada; nulo, ídem
   */
  public record Politica(Integer porOrigen, Integer porIdentidad, Duration ventana) {

    public boolean acotaPorOrigen() {
      return porOrigen != null && porOrigen > 0;
    }

    public boolean acotaPorIdentidad() {
      return porIdentidad != null && porIdentidad > 0;
    }
  }
}
