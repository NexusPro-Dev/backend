package com.factech.nexus.shared.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Registro en memoria de los cortes de token de acceso (`RF-SP-028` `plan.md` §7).
 *
 * <p>Guarda, por persona, el instante a partir del cual sus tokens de acceso dejan de admitirse.
 * {@link AccessRevocationValidator} lo consulta al validar cada token.
 *
 * <p><b>En memoria y no en la base de datos</b>, que es la decisión que define este componente.
 * Consultar el estado de la cuenta en cada petición convertiría el diseño sin estado en una
 * consulta por petición sobre el camino más caliente del sistema, para atender algo que ocurre unas
 * pocas veces al día. Y una lista negra por {@code jti} obligaría a recordar tokens individuales
 * sin ganar nada: el corte es siempre <b>por persona</b>.
 *
 * <p><b>No crece.</b> Cada entrada deja de servir para nada al cabo de la vida del token de acceso
 * —a partir de ahí ningún token afectado sigue siendo válido por firma— y se retira al consultarla
 * o al publicar la siguiente. El tamaño está acotado por cuántas cuentas pierden el acceso en
 * quince minutos.
 *
 * <p><b>Con más de una instancia solo corta en la que atendió la petición.</b> El riesgo está
 * declarado y acotado en `RF-SP-028` `plan.md` §10: los refresh tokens sí quedan revocados en la
 * base, de modo que la sesión no puede prolongarse en ninguna instancia y la ventana es como mucho
 * la vida del token. La corrección —un canal compartido detrás de {@link
 * AccessRevocationPublisher}— no toca ningún caso de uso, y debe existir antes de desplegar una
 * segunda instancia (<b>D-09</b>).
 */
@Component
public class AccessRevocationRegistry implements AccessRevocationPublisher {

  private static final Logger LOG = LoggerFactory.getLogger(AccessRevocationRegistry.class);

  private final Map<UUID, Instant> cortes = new ConcurrentHashMap<>();
  private final JdbcTemplate jdbc;
  private final Clock reloj;
  private final Duration vidaDelToken;

  @Autowired
  public AccessRevocationRegistry(
      JdbcTemplate jdbc,
      @Value("${nexus.security.jwt.access-token-ttl:PT15M}") Duration vidaDelToken) {
    this(jdbc, vidaDelToken, Clock.systemUTC());
  }

  /** Con reloj inyectado, para las pruebas que necesitan mover el tiempo. */
  AccessRevocationRegistry(JdbcTemplate jdbc, Duration vidaDelToken, Clock reloj) {
    this.jdbc = jdbc;
    this.vidaDelToken = vidaDelToken;
    this.reloj = reloj;
  }

  @Override
  public void publicarCorte(UUID userId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      // Sin transacción que esperar, el corte ya es cierto. Llega por este
      // camino la siembra del arranque y cualquier invocación fuera de un caso
      // de uso.
      anotar(userId);
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            anotar(userId);
          }
        });
  }

  /**
   * ¿Este token quedó cortado?
   *
   * @param emitidoEn el {@code iat} del token
   */
  public boolean estaCortado(UUID userId, Instant emitidoEn) {
    Instant corte = corteVigente(userId);
    return corte != null && emitidoEn.isBefore(corte);
  }

  /**
   * El corte vivo de esa persona, o {@code null} si no tiene ninguno que siga sirviendo.
   *
   * <p>Lo consulta {@link AccessTokenIssuer} al emitir, además de {@link AccessRevocationValidator}
   * al validar. Ver {@link #anotar} para por qué hacen falta los dos.
   */
  Instant corteVigente(UUID userId) {
    Instant corte = cortes.get(userId);
    if (corte == null) {
      return null;
    }
    if (corte.isBefore(Instant.now(reloj).minus(vidaDelToken))) {
      // Caducó: ningún token anterior a ese corte sigue siendo válido por firma.
      // Se retira al consultarlo, que es lo que mantiene el registro acotado sin
      // una tarea de limpieza.
      cortes.remove(userId, corte);
      return null;
    }
    return corte;
  }

  /**
   * Anota el corte en el instante presente, <b>redondeado hacia arriba al segundo</b>.
   *
   * <p><b>Por qué hacia arriba, y qué problema deja abierto.</b> El {@code iat} de un JWT va en
   * <b>segundos enteros</b>. Con el corte en el instante exacto, un token emitido en ese mismo
   * segundo llevaría un {@code iat} truncado hacia abajo y la comparación lo dejaría pasar aunque
   * naciera antes de la revocación. Redondeando hacia arriba, todo token de ese segundo cae del
   * lado cortado — que es lo correcto para el que nació antes, y <b>lo incorrecto para el que nació
   * después</b>. Con el {@code iat} en segundos los dos son indistinguibles.
   *
   * <p><b>Y por eso el emisor consulta el corte.</b> Ese segundo ambiguo no se resuelve eligiendo a
   * qué lado caen los empates —cerrar mata tokens legítimos, abrir deja vivos quince minutos los
   * que debían morir—, sino <b>quitando la ambigüedad</b>: {@link AccessTokenIssuer} sella el token
   * que emite con {@code iat} igual al corte cuando este es posterior al reloj, de modo que un
   * token recién emitido nunca queda por debajo. Ahí la comparación de {@link #estaCortado} deja de
   * depender de en qué milisegundo cayó la petición, que es la clase de detalle que produce fallos
   * que solo aparecen a veces.
   */
  private void anotar(UUID userId) {
    Instant corte = Instant.now(reloj).truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
    cortes.merge(userId, corte, (anterior, nuevo) -> nuevo.isAfter(anterior) ? nuevo : anterior);
  }

  /**
   * Repuebla el registro al arrancar.
   *
   * <p><b>Sin esto, un reinicio devuelve la validez a los tokens que se acababan de cortar</b>, y
   * es un agujero que ninguna prueba funcional detecta: solo aparece reiniciando el proceso entre
   * el corte y la expiración del token, que no es algo que nadie haga a propósito.
   *
   * <p>Se siembra con las cuentas que dejaron de estar activas —o quedaron eliminadas— dentro de la
   * ventana de vida del token. Más allá de esa ventana no hay nada que cortar: sus tokens ya no
   * valen por firma.
   *
   * <p><b>El corte sembrado es el instante del cambio y no el del arranque.</b> Usar el arranque
   * cortaría además los tokens emitidos después del cambio, que son legítimos — los de alguien que
   * volvió a activarse, por ejemplo.
   */
  @EventListener(ApplicationReadyEvent.class)
  void sembrar() {
    OffsetDateTime desde =
        OffsetDateTime.ofInstant(Instant.now(reloj).minus(vidaDelToken), ZoneOffset.UTC);

    try {
      // Con `RowMapper` y `getObject(..., Class)` y NO con `queryForList`: este
      // devuelve lo que el driver decida —un `Timestamp` para `timestamptz`, no
      // un `OffsetDateTime`—, y el molde moriría con un `ClassCastException` que
      // el `catch` de abajo convierte en un registro vacío. Es decir: la siembra
      // no sembraría nada y nadie se enteraría, que es exactamente el fallo
      // silencioso que esta siembra existe para evitar.
      jdbc.query(
              """
              SELECT id,
                     GREATEST(updated_at, COALESCE(deleted_at, updated_at)) AS corte
                FROM users
               WHERE (status <> 'ACTIVO' OR deleted_at IS NOT NULL)
                 AND GREATEST(updated_at, COALESCE(deleted_at, updated_at)) > ?
              """,
              (fila, numero) ->
                  Map.entry(
                      fila.getObject("id", UUID.class),
                      fila.getObject("corte", OffsetDateTime.class).toInstant()),
              desde)
          .forEach(entrada -> cortes.put(entrada.getKey(), entrada.getValue()));

      if (!cortes.isEmpty()) {
        LOG.info("Registro de cortes de acceso sembrado con {} cuentas", cortes.size());
      }
    } catch (RuntimeException fallo) {
      // No se propaga: un fallo aquí no debe impedir que la aplicación arranque.
      // Sí se registra, porque la consecuencia —una ventana de quince minutos en
      // la que tokens cortados vuelven a valer— es invisible de cualquier otro
      // modo.
      LOG.error(
          "No se pudo sembrar el registro de cortes de acceso. Los tokens cortados justo antes"
              + " de este arranque vuelven a admitirse hasta que expiren",
          fallo);
    }
  }

  /** Cuántos cortes vivos hay. Existe para las pruebas y para diagnosticar. */
  public int tamano() {
    return cortes.size();
  }
}
