package com.factech.nexus.modules.system.auth.domain.service;

import com.factech.nexus.shared.audit.AuditEnums.Outcome;
import com.factech.nexus.shared.audit.AuditEnums.SecurityEventType;
import com.factech.nexus.shared.audit.AuditEnums.Severity;
import com.factech.nexus.shared.audit.AuditEvents.SecurityEvent;
import com.factech.nexus.shared.audit.AuditWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retira las familias de sesión ya caducadas de {@code refresh_tokens} (issue #25).
 *
 * <p><b>El problema es de crecimiento, no de espacio.</b> `RF-SP-035` rota el token de refresco en
 * <b>cada</b> refresco y revocar es marcar, no eliminar: una sesión de siete días renovando cada
 * quince minutos deja cientos de filas, y ninguna desaparece al cerrarla. La tabla que sostiene la
 * autenticación de todo el sistema crece de forma monótona con el uso, y su índice único de hashes
 * con ella.
 *
 * <p><b>Se purga por familia entera y no por fila, y ese es el punto delicado.</b> `RF-SP-035`
 * detecta el robo cuando alguien presenta un token ya rotado: esa detección vive precisamente en
 * las filas revocadas, de modo que <b>purgar demasiado pronto apaga una alarma</b>. El corte no se
 * cuenta desde la revocación —una rotación revoca su token a los quince minutos de nacer— sino
 * desde el momento en que <b>toda</b> la familia dejó de poder autenticar: el mayor {@code
 * expires_at} del grupo, más el plazo de retención. Mientras la familia siga viva, ninguna de sus
 * filas se toca aunque estén revocadas desde hace meses.
 *
 * <p><b>Por qué hay que anular {@code replaced_by_id} antes de borrar.</b> La cadena de rotación
 * apunta de un token al siguiente con una clave foránea {@code ON DELETE RESTRICT}, que PostgreSQL
 * comprueba <b>fila a fila</b>: un {@code DELETE} que se lleve la familia completa falla igual,
 * porque en el instante de borrar la primera fila la segunda todavía la referencia. Anular los
 * punteros dentro de la misma transacción es lo que lo hace posible, y no pierde nada — esas filas
 * se van acto seguido.
 *
 * <p><b>Un cerrojo de aviso, porque tres réplicas purgan tres veces.</b> {@code
 * pg_try_advisory_xact_lock} es del motor y no del proceso: la instancia que no lo obtiene sale sin
 * hacer nada en lugar de competir por las mismas filas. Se libera solo al terminar la transacción,
 * incluso si el proceso muere.
 *
 * <p><b>Deja constancia</b> (issue #25): una purga que elimina evidencia sin registrar cuánta
 * eliminó no es auditable, y su ausencia sería indistinguible de una que nunca corrió.
 */
@Service
public class PurgeExpiredTokensService {

  private static final Logger LOG = LoggerFactory.getLogger(PurgeExpiredTokensService.class);

  /**
   * Llave del cerrojo de aviso. Es un número cualquiera, pero <b>fijo</b>: dos procesos solo se
   * excluyen si piden la misma, y por eso vive aquí y no en configuración.
   */
  private static final long LLAVE_DEL_CERROJO = 6_252_025_001L;

  private final JdbcTemplate jdbc;
  private final AuditWriter auditoria;
  private final Duration retencion;

  public PurgeExpiredTokensService(
      JdbcTemplate jdbc,
      AuditWriter auditoria,
      @Value("${nexus.security.token-purge.retention}") Duration retencion) {
    this.jdbc = jdbc;
    this.auditoria = auditoria;
    this.retencion = retencion;
  }

  /**
   * Ejecuta la purga y devuelve cuántas filas retiró.
   *
   * <p>Todo en una transacción: el cerrojo, la anulación de los punteros y el borrado. Si algo
   * falla, no queda una familia a medio purgar con la cadena de rotación rota.
   *
   * @return filas eliminadas; {@code 0} también cuando otra instancia tenía el cerrojo
   */
  @Transactional
  public int purgar() {
    Boolean mio =
        jdbc.queryForObject(
            "SELECT pg_try_advisory_xact_lock(?)", Boolean.class, LLAVE_DEL_CERROJO);
    if (!Boolean.TRUE.equals(mio)) {
      // No es un error ni merece alarma: es la otra réplica haciéndolo.
      LOG.debug("Otra instancia está purgando las sesiones caducadas; esta no hace nada");
      return 0;
    }

    Instant corte = Instant.now().minus(retencion);

    // Las familias, primero y aparte. Hacen falta dos veces —para anular y para
    // borrar— y resolverlas dos veces podría dar conjuntos distintos si algo
    // expira entre medias.
    List<UUID> familias =
        jdbc.queryForList(
            """
            SELECT family_id
              FROM refresh_tokens
             GROUP BY family_id
            HAVING max(expires_at) < ?
            """,
            UUID.class,
            java.sql.Timestamp.from(corte));

    if (familias.isEmpty()) {
      return 0;
    }

    String marcadores = String.join(",", java.util.Collections.nCopies(familias.size(), "?"));
    Object[] parametros = familias.toArray();

    // Sin esto, el borrado revienta contra `fk_refresh_tokens_replaced_by`.
    jdbc.update(
        "UPDATE refresh_tokens SET replaced_by_id = NULL WHERE family_id IN (" + marcadores + ")",
        parametros);

    int borradas =
        jdbc.update(
            "DELETE FROM refresh_tokens WHERE family_id IN (" + marcadores + ")", parametros);

    registrar(borradas, familias.size(), corte);
    return borradas;
  }

  /**
   * El apunte de lo que se llevó por delante.
   *
   * <p>Sin actor, porque no lo hizo nadie, y sin identificar a ninguna persona: quién tenía esas
   * sesiones no aporta nada a la pregunta que este evento responde —cuántas se retiraron y hasta
   * qué fecha—, y sí convertiría una tarea de mantenimiento en un rastro de quién usó el sistema.
   *
   * <p>Un fallo al auditar no revierte la purga: las filas ya no autenticaban a nadie, y perder el
   * apunte es preferible a repetir el borrado cada noche sin conseguirlo nunca. Queda en el log de
   * aplicación, que es lo que permite detectarlo.
   */
  private void registrar(int borradas, int familias, Instant corte) {
    LOG.info(
        "Purga de sesiones: {} filas de {} familias caducadas antes de {}",
        borradas,
        familias,
        corte);
    try {
      auditoria.recordSecurity(
          new SecurityEvent(
              SecurityEventType.SESSION_TOKENS_PURGED,
              Severity.INFORMATIVA,
              Outcome.SUCCESS,
              null,
              Map.of(
                  "deletedRows",
                  borradas,
                  "deletedFamilies",
                  familias,
                  "cutoff",
                  corte.toString(),
                  "retention",
                  retencion.toString())));
    } catch (RuntimeException fallo) {
      LOG.error("No se pudo registrar la purga de sesiones caducadas", fallo);
    }
  }
}
