package com.factech.nexus.modules.system.auth.domain.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Contador de intentos fallidos <b>de los identificadores que no tienen cuenta</b> (`RF-SP-034`).
 *
 * <p><b>Existe por la enumeración, no por la fuerza bruta.</b> El bloqueo real vive en {@code
 * users.failed_attempts}, que es la única defensa que importa cuando la cuenta existe. Lo que aquí
 * se cuenta no protege nada: sirve para que la respuesta a un identificador inventado sea
 * <b>indistinguible</b> de la respuesta a uno real. Sin este registro, decir «le quedan 3 intentos»
 * solo cuando hay cuenta convierte el inicio de sesión en el verificador de cuentas que `CA-SP-292`
 * y `security.md` §3.2 prohíben: bastaría una contraseña cualquiera y mirar si el campo aparece.
 *
 * <p>Por eso replica la <b>misma</b> mecánica que el contador de la base de datos —cuenta, y al
 * alcanzar el umbral marca un bloqueo con la misma {@code LockoutPolicy}—, y no una aproximación:
 * si el falso no llegara a «bloquearse», el {@code 423} volvería a ser el oráculo que el {@code
 * 401} deja de ser.
 *
 * <p><b>En memoria, acotado y con caducidad</b>, y las tres cosas son deliberadas:
 *
 * <ul>
 *   <li><b>En memoria</b> porque la clave la elige quien ataca. Persistir una fila por
 *       identificador inventado convertiría la propia defensa en el amplificador del ataque —el
 *       mismo motivo por el que `plan.md` §6 decidió no auditar el {@code 429}—.
 *   <li><b>Acotado</b> porque un mapa cuya clave llega de fuera crece hasta donde el atacante
 *       quiera. Al llegar al tope se purga lo caducado y, si aun así sigue lleno, <b>se deja de
 *       anotar</b> en lugar de desalojar: desalojando, quien inunda el registro elige qué entradas
 *       se olvidan.
 *   <li><b>Con caducidad</b> para que una máquina que lleve semanas en pie no acumule
 *       identificadores que nadie volverá a probar. La ventana nunca es menor que el techo del
 *       bloqueo, o una entrada podría caducar con su bloqueo todavía vigente y regalar el intento.
 * </ul>
 *
 * <p><b>Lo que este diseño no cierra</b>, y conviene dejarlo escrito: la cuenta real recuerda sus
 * fallos indefinidamente —hasta que alguien entre bien— y el identificador inventado los olvida al
 * cerrar la ventana. Quien pruebe un identificador, espere más de una hora y lo vuelva a probar
 * puede notar la diferencia. Se acepta porque esa señal es la misma que el {@code 423} ya entrega
 * por diseño desde la resolución 3 de la `spec.md`, y cerrarla del todo exigiría persistir los
 * fallos de identificadores inventados, que es peor.
 */
@Component
public class FailedAttemptLedger {

  /** Lo que el registro sabe de un identificador. */
  public record Fallos(int intentos, OffsetDateTime bloqueadoHasta) {

    /** Ningún fallo anotado: ni lo hay, ni lo hubo dentro de la ventana. */
    public static final Fallos NINGUNO = new Fallos(0, null);

    public boolean bloqueado(OffsetDateTime ahora) {
      return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }
  }

  /**
   * @param expiraEn cuándo se olvida la entrada. Se renueva con cada fallo
   */
  private record Entrada(int intentos, OffsetDateTime bloqueadoHasta, OffsetDateTime expiraEn) {}

  private final Map<String, Entrada> registro = new ConcurrentHashMap<>();
  private final int capacidad;
  private final Duration ventana;

  public FailedAttemptLedger(
      @Value("${nexus.security.lockout.identifier-window:PT1H}") Duration ventana,
      @Value("${nexus.security.lockout.identifier-capacity:10000}") int capacidad,
      @Value("${nexus.security.lockout.max-delay:PT1H}") Duration bloqueoMaximo) {
    // La ventana NO puede quedar por debajo del techo del bloqueo: una entrada
    // que caduca antes que su propio bloqueo lo levantaría sola, y el
    // identificador inventado volvería a contar desde cero mientras el real
    // sigue bloqueado. Se corrige en silencio en lugar de fallar al arrancar
    // porque la configuración segura es deducible y no hay nada que preguntar.
    this.ventana = ventana.compareTo(bloqueoMaximo) < 0 ? bloqueoMaximo : ventana;
    this.capacidad = capacidad;
  }

  /**
   * Qué se sabe de este identificador ahora mismo.
   *
   * <p>Una entrada caducada se trata como inexistente <b>y se retira de paso</b>: es la limpieza
   * que evita que el registro dependa de una tarea programada para no crecer.
   */
  public Fallos consultar(String identificador, OffsetDateTime ahora) {
    String clave = clave(identificador);
    Entrada entrada = registro.get(clave);

    if (entrada == null) {
      return Fallos.NINGUNO;
    }
    if (!entrada.expiraEn().isAfter(ahora)) {
      registro.remove(clave, entrada);
      return Fallos.NINGUNO;
    }
    return new Fallos(entrada.intentos(), entrada.bloqueadoHasta());
  }

  /**
   * Anota el fallo, con la misma firma que {@code AuthUserRepository.registrarFallo}.
   *
   * <p>La simetría no es estética: los dos caminos —cuenta real y cuenta inventada— tienen que
   * anotar lo mismo para que respondan lo mismo, y una firma distinta invitaría a que uno de los
   * dos se quedara atrás en la próxima modificación.
   *
   * @param bloquearHasta instante hasta el que queda bloqueado, o {@code null} si aún no toca
   */
  public void registrarFallo(
      String identificador, int intentos, OffsetDateTime bloquearHasta, OffsetDateTime ahora) {
    String clave = clave(identificador);

    if (!registro.containsKey(clave) && registro.size() >= capacidad) {
      purgar(ahora);
      if (registro.size() >= capacidad) {
        // Se deja de contar. La consecuencia está declarada arriba y es la
        // menos mala: quien llenó el registro es quien lo está atacando.
        return;
      }
    }
    registro.put(clave, new Entrada(intentos, bloquearHasta, ahora.plus(ventana)));
  }

  /** Solo para las pruebas: el registro es estado compartido entre ellas. */
  public void limpiar() {
    registro.clear();
  }

  /**
   * <b>La misma normalización que la búsqueda de la cuenta.</b>
   *
   * <p>{@code AuthUserRepository.findByIdentifier} compara el nombre de usuario sin distinguir
   * mayúsculas, de modo que {@code JPerez} y {@code jperez} comparten contador. Si aquí no se
   * hiciera lo mismo, alternar la caja daría intentos infinitos sobre un identificador inventado y
   * ninguno sobre uno real — que es exactamente la diferencia observable que este registro existe
   * para borrar.
   */
  private static String clave(String identificador) {
    return identificador.trim().toLowerCase(Locale.ROOT);
  }

  private void purgar(OffsetDateTime ahora) {
    registro.values().removeIf(entrada -> !entrada.expiraEn().isAfter(ahora));
  }
}
