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
 *   <li><b>Recuperación — 5/minuto por identidad y por origen, con cinco minutos de espera al
 *       superarla.</b> Decidido el 26-08-2026 por el responsable del proyecto, sobre la advertencia
 *       de que es la única operación pública que provoca un envío saliente: sin cota se puede
 *       inundar de correos a una persona real —lo que es acoso, y quema la reputación del dominio
 *       de envío— y sondear identidades en masa. <b>La penalización es lo que sostiene el
 *       número</b>: cinco por minuto sin ella son setenta y dos mil correos al día; con ella el
 *       ritmo sostenido es de cinco cada cinco minutos, unos sesenta a la hora. Era de tres a la
 *       hora.
 * </ul>
 *
 * <p><b>Las dos cotas de recuperación se aplican desde el 26-08-2026</b>, al existir sus endpoints:
 * `RF-SP-040` entró ese día al cerrarse **D-23**. La confirmación se acota solo por origen, porque
 * su cuerpo no lleva identidad ninguna: lleva un permiso.
 *
 * @param enabled permite apagarlo. Existe para las pruebas de otros requerimientos, que emiten
 *     ráfagas contra el login a propósito; en un entorno real no debería tocarse
 * @param capacity cuántas llaves distintas se recuerdan a la vez. La llave la elige quien llama —su
 *     IP, su identificador—, de modo que un mapa sin tope crece hasta donde quiera el atacante
 */
@ConfigurationProperties(prefix = "nexus.security.rate-limit")
public record RateLimitSettings(
    boolean enabled,
    int capacity,
    Politica login,
    Politica refresh,
    Politica recovery,
    Politica recoveryConfirmation) {

  /**
   * Una política: cuántas peticiones por ventana, por origen y por identidad.
   *
   * @param porOrigen máximo por dirección de red; nulo significa que este endpoint no lo acota
   * @param porIdentidad máximo por credencial o identidad declarada; nulo, ídem
   * @param penalizacion espera fija que se impone al superar la cota; nula, no hay castigo y basta
   *     con que la ventana deslizante deje sitio
   */
  public record Politica(
      Integer porOrigen, Integer porIdentidad, Duration ventana, Duration penalizacion) {

    /**
     * ¿Superar la cota cuesta una espera fija, además de la que impone la ventana?
     *
     * <p>Nula significa que no: se vuelve a poder pedir en cuanto la ventana deslizante deje sitio,
     * que es el comportamiento de las otras tres políticas. Con penalización, quien topa espera lo
     * que diga aunque deje de insistir.
     */
    public boolean penaliza() {
      return penalizacion != null && !penalizacion.isZero() && !penalizacion.isNegative();
    }

    public boolean acotaPorOrigen() {
      return porOrigen != null && porOrigen > 0;
    }

    public boolean acotaPorIdentidad() {
      return porIdentidad != null && porIdentidad > 0;
    }
  }
}
