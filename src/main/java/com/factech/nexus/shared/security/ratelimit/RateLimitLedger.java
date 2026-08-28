package com.factech.nexus.shared.security.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Cuenta peticiones por llave y ventana (`security.md` §5.5, issue #21).
 *
 * <p><b>Ventana deslizante y no fija.</b> Una ventana fija —«diez por minuto de reloj»— admite
 * veinte peticiones en dos segundos si caen a caballo del cambio de minuto, que es justo la ráfaga
 * que se quiere impedir. Aquí se recuerdan los instantes de las últimas peticiones admitidas de
 * cada llave: la cota se cumple para <b>cualquier</b> intervalo de esa duración, no solo para los
 * que empiezan en punto.
 *
 * <p><b>El coste está acotado por construcción.</b> Cada llave guarda como mucho {@code maximo}
 * instantes —cinco, diez, sesenta—, y las llaves se limitan a {@code capacidad}. Eso importa más de
 * lo que parece: <b>la llave la elige quien llama</b> —su dirección de red, el identificador que
 * teclea—, de modo que un mapa sin tope es un consumo de memoria que el atacante controla, y la
 * defensa se convertiría en el ataque.
 *
 * <p><b>Al llenarse, se descarta lo más antiguo.</b> {@link LinkedHashMap} en orden de acceso hace
 * que la llave desalojada sea la que lleva más tiempo sin usarse, que es la que menos probable es
 * que esté en mitad de una ráfaga. Se acepta la consecuencia: con el mapa lleno, un atacante que
 * rote direcciones puede desalojar la entrada de otro y regalarle una ventana limpia. Es preferible
 * a quedarse sin memoria, y la capacidad se dimensiona para que no ocurra en operación normal.
 *
 * <p><b>Un solo cerrojo para todo el mapa.</b> Lo que se hace dentro es contar y descartar
 * caducados —nanosegundos—, de modo que la contención no compite con el trabajo real de una
 * petición, que incluye base de datos. Si algún día lo hiciera, la sustitución es un mapa
 * segmentado y no cambia esta interfaz.
 */
@Component
public class RateLimitLedger {

  private final Clock reloj;
  private final Map<String, Deque<Long>> historial;
  private final Map<String, Long> avisos;

  /**
   * Hasta cuándo está castigada cada llave que superó una cota con penalización.
   *
   * <p>Va aparte del historial porque responde otra pregunta: el historial dice <b>cuántas</b>
   * peticiones cupieron en la ventana; esto dice que da igual, porque esta llave no puede pedir
   * todavía. Con el mismo tope y el mismo olvido que los otros dos mapas, y por el mismo motivo: la
   * llave la elige quien llama.
   */
  private final Map<String, Long> penalizados;

  /**
   * Constructor de producción.
   *
   * <p>La anotación no es decorativa: Spring solo infiere el constructor cuando la clase declara
   * exactamente uno, y aquí hay dos —el segundo existe para que la prueba pueda fijar el reloj y
   * comprobar una ventana deslizante sin dormir el hilo—. Sin ella busca el constructor sin
   * argumentos, no lo encuentra y el contexto no arranca.
   */
  @org.springframework.beans.factory.annotation.Autowired
  public RateLimitLedger(RateLimitSettings ajustes) {
    this(ajustes, Clock.systemUTC());
  }

  RateLimitLedger(RateLimitSettings ajustes, Clock reloj) {
    this.reloj = reloj;
    int capacidad = ajustes.capacity() > 0 ? ajustes.capacity() : 20_000;
    this.historial =
        new LinkedHashMap<>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, Deque<Long>> mayor) {
            return size() > capacidad;
          }
        };
    this.avisos =
        new LinkedHashMap<>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, Long> mayor) {
            return size() > capacidad;
          }
        };
    this.penalizados =
        new LinkedHashMap<>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, Long> mayor) {
            return size() > capacidad;
          }
        };
  }

  /**
   * Registra una petición y dice si cabe dentro de la cota, sin penalización.
   *
   * <p>Es el comportamiento de las políticas que no la declaran: quien topa vuelve a poder pedir en
   * cuanto la ventana deslizante deje sitio.
   */
  public Veredicto registrar(String llave, int maximo, Duration ventana) {
    return registrar(llave, maximo, ventana, null);
  }

  /**
   * Registra una petición y dice si cabe dentro de la cota.
   *
   * <p><b>La petición rechazada NO se apunta.</b> Si se apuntara, una ráfaga sostenida renovaría su
   * propia ventana en cada intento y el castigo sería perpetuo: quien topa con el límite una vez no
   * podría volver nunca, aunque dejara de insistir. Lo que se cuenta es lo que se atiende.
   *
   * <p><b>La penalización tampoco se renueva al insistir</b>, y por el mismo motivo. Se fija <b>una
   * sola vez</b>, en la petición que cruza la cota; las que llegan durante el castigo se rechazan
   * con lo que quede y no lo alargan. Renovarla convertiría cinco peticiones de más en un bloqueo
   * indefinido para quien tenga un cliente que reintenta solo.
   *
   * <p><b>Al penalizar se olvida la ventana.</b> Cumplido el castigo, la cota vuelve entera: si el
   * historial se conservara y la penalización fuese más corta que la ventana, la primera petición
   * de después volvería a topar y el castigo se encadenaría sin que nadie lo hubiera pedido.
   *
   * @param penalizacion espera fija al superar la cota; nula, no hay castigo
   * @return el veredicto, con cuánto falta para que quepa la siguiente
   */
  public Veredicto registrar(String llave, int maximo, Duration ventana, Duration penalizacion) {
    long ahora = reloj.millis();
    long desde = ahora - ventana.toMillis();

    synchronized (historial) {
      // El castigo se comprueba ANTES de contar: mientras dure, cuántas
      // peticiones cupieron en la ventana es una pregunta sin efecto.
      Long castigadaHasta = penalizados.get(llave);
      if (castigadaHasta != null) {
        if (castigadaHasta > ahora) {
          return new Veredicto(false, enSegundos(castigadaHasta - ahora));
        }
        penalizados.remove(llave);
      }

      Deque<Long> instantes = historial.computeIfAbsent(llave, k -> new ArrayDeque<>());

      // Fuera lo que ya no cuenta. Es también lo que impide que una llave
      // inactiva conserve datos indefinidamente.
      while (!instantes.isEmpty() && instantes.peekFirst() <= desde) {
        instantes.pollFirst();
      }

      if (instantes.size() >= maximo) {
        if (penalizacion != null && !penalizacion.isZero() && !penalizacion.isNegative()) {
          penalizados.put(llave, ahora + penalizacion.toMillis());
          instantes.clear();
          return new Veredicto(false, enSegundos(penalizacion.toMillis()));
        }
        long esperaMs = instantes.peekFirst() + ventana.toMillis() - ahora;
        return new Veredicto(false, enSegundos(esperaMs));
      }

      instantes.addLast(ahora);
      return new Veredicto(true, 0);
    }
  }

  /** Nunca cero: decirle a un cliente que espere cero segundos es invitarle a reintentar ya. */
  private static long enSegundos(long milisegundos) {
    return Math.max(1, (long) Math.ceil(milisegundos / 1000.0));
  }

  /**
   * ¿Toca dejar constancia de esta ráfaga, o ya se dejó en esta ventana?
   *
   * <p><b>Un evento por ventana y no uno por petición rechazada.</b> Una ráfaga de mil peticiones
   * por segundo escribiría mil filas por segundo en {@code audit_security_log}: la defensa se
   * convertiría en el ataque, y el registro que sirve para investigar quedaría sepultado justo
   * cuando hace falta leerlo.
   *
   * <p>Vive aquí y no en el filtro porque es <b>el mismo estado en memoria</b> que el contador —con
   * el mismo tope y el mismo olvido—, y tenerlo repartido en dos sitios significaba que {@link
   * #limpiar()} solo alcanzaba a la mitad. Se descubrió con una prueba que heredaba el aviso de la
   * anterior y no registraba nada.
   */
  public boolean debeAvisar(String llave, Duration ventana) {
    long ahora = reloj.millis();

    synchronized (avisos) {
      Long ultimo = avisos.get(llave);
      if (ultimo != null && ahora - ultimo < ventana.toMillis()) {
        return false;
      }
      avisos.put(llave, ahora);
      return true;
    }
  }

  /** Olvida todo lo contado. Existe para que una prueba no herede la ráfaga de la anterior. */
  public void limpiar() {
    synchronized (historial) {
      historial.clear();
    }
    synchronized (avisos) {
      avisos.clear();
    }
    synchronized (penalizados) {
      penalizados.clear();
    }
  }

  /**
   * @param admitida si la petición cabe dentro de la cota
   * @param esperaSegundos cuánto falta para que quepa la siguiente; cero cuando se admitió
   */
  public record Veredicto(boolean admitida, long esperaSegundos) {}
}
