package com.factech.nexus.shared.observability;

import com.factech.nexus.shared.persistence.UuidV7Generator;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escribe una fila en {@code request_log} (Art. XV.2, issue #23).
 *
 * <p><b>Transacción propia y nunca la de negocio</b> (Art. XV.7). La fila se escribe <b>después</b>
 * de emitida la respuesta, de modo que un fallo al registrar no puede tumbar la operación — y, al
 * revés, una operación revertida sí deja su rastro: que el negocio fallara no significa que la
 * petición no ocurriera. Es exactamente la diferencia con los cuatro registros de auditoría, que se
 * unen a la transacción precisamente para desaparecer con ella.
 *
 * <p><b>Se usa {@code JdbcTemplate} y no una entidad JPA.</b> La tabla no tiene comportamiento ni
 * invariantes que proteger: es un apunte. Mapearla como entidad la metería en el contexto de
 * persistencia de cada petición —con su comprobación de cambios y su vaciado— para escribir una
 * fila que nadie vuelve a leer en esa misma petición.
 */
@Component
public class RequestLogWriter {

  private static final String INSERTAR =
      """
      INSERT INTO request_log (id, occurred_at, correlation_id, actor_id, method, path,
                               query_string, status, duration_ms, ip_address, user_agent)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? AS inet), ?)
      """;

  /** Lo que la columna admite. Se recorta en lugar de fallar: un apunte no rechaza. */
  private static final int LARGO_MAXIMO_RUTA = 2048;

  private final JdbcTemplate jdbc;
  private final UuidV7Generator ids;

  public RequestLogWriter(JdbcTemplate jdbc, UuidV7Generator ids) {
    this.jdbc = jdbc;
    this.ids = ids;
  }

  /**
   * @param actorId nulo significa <b>anónimo</b>, no que se perdiera el dato (Art. XV.2)
   * @param status nulo cuando la petición se abortó sin llegar a responder
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void registrar(
      UUID correlacion,
      UUID actorId,
      String metodo,
      String ruta,
      String parametros,
      Integer status,
      long duracionMs,
      String ip,
      String agente) {

    jdbc.update(
        INSERTAR,
        ids.next(),
        OffsetDateTime.now(),
        correlacion,
        actorId,
        metodo,
        recortar(ruta, LARGO_MAXIMO_RUTA),
        parametros,
        status,
        duracionMs,
        ip,
        agente);
  }

  private static String recortar(String valor, int largo) {
    if (valor == null) {
      return null;
    }
    return valor.length() <= largo ? valor : valor.substring(0, largo);
  }
}
