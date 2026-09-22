package com.factech.nexus.modules.system.audit.domain.repository;

import com.factech.nexus.modules.system.audit.application.AuditActor;
import com.factech.nexus.modules.system.audit.application.ChangeAuditItem;
import com.factech.nexus.modules.system.audit.application.DeletionAuditItem;
import com.factech.nexus.modules.system.audit.application.ErrorAuditItem;
import com.factech.nexus.modules.system.audit.application.ListChangeAuditRequest;
import com.factech.nexus.modules.system.audit.application.ListDeletionAuditRequest;
import com.factech.nexus.modules.system.audit.application.ListErrorAuditRequest;
import com.factech.nexus.modules.system.audit.application.ListSecurityAuditRequest;
import com.factech.nexus.modules.system.audit.application.SecurityAuditItem;
import com.factech.nexus.shared.pagination.BoundedCount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de lectura de los cuatro registros de auditoría.
 *
 * <p><b>Los cuatro comparten mecánica y por eso comparten clase.</b> Lo único que cambia entre
 * ellos son las columnas que se proyectan y los filtros propios; el resto —el rango de fechas
 * semiabierto, el orden, la página y el conteo acotado— está escrito <b>una vez</b> en {@link
 * #pagina} y {@link #conteo}. Cuatro adaptadores con la misma forma serían cuatro sitios donde el
 * predicado del rango podría divergir, y una divergencia ahí no se ve: devuelve datos plausibles.
 *
 * <p><b>El predicado de la página y el del conteo salen del MISMO método.</b> Un conteo que
 * aplicara un filtro distinto al de los datos produciría un total que no corresponde a lo devuelto,
 * y es un fallo que ninguna prueba de la página detecta.
 *
 * <p><b>El orden es fijo: {@code occurred_at DESC, id DESC}.</b> No lo elige el cliente porque es
 * parte del significado del recurso — un registro cronológico ordenado por módulo respondería otra
 * pregunta—, y el desempate por {@code id} no es cosmético: dos eventos pueden compartir instante,
 * y sin él las páginas consecutivas repetirían filas y omitirían otras. Al ser {@code id} un UUID
 * v7 —marca temporal en los bits altos—, ese desempate sigue siendo orden cronológico.
 */
@Repository
public class JpaAuditQueryRepository implements AuditQueryRepository {

  /**
   * El actor, resuelto en la MISMA sentencia (`RN-SP-016`).
   *
   * <p><b>Es un {@code LEFT JOIN} y no una consulta por fila</b>, por lo mismo que el destino de un
   * producto: resolver cien actores de una página son cien consultas, que es el problema de las
   * {@code N+1} con otro nombre. El {@code LEFT} no es opcional — con un {@code JOIN} corriente
   * desaparecerían de la página los eventos <b>del sistema</b>, que son justo los que no tienen
   * actor.
   *
   * <p><b>NO filtra por {@code deleted_at}</b>, y es deliberado: `RF-SP-029` elimina de forma
   * lógica, la fila permanece, y una auditoría que dejara de decir quién hizo algo porque esa
   * persona fue dada de baja perdería su valor justo en el caso en que más se consulta.
   */
  private static final String ACTOR_JOIN = " LEFT JOIN users ua ON ua.id = a.actor_id";

  private static final String ACTOR_COLS =
      ", ua.username AS actor_username, ua.first_name AS actor_nombre,"
          + " ua.last_name AS actor_apellido";

  /**
   * La persona SOBRE la que recayó el evento, solo en el registro de seguridad.
   *
   * <p>Es un segundo {@code LEFT JOIN} a la misma tabla y con otro alias, porque es <b>otra</b>
   * persona: un bloqueo lo ejecuta un administrador y recae sobre otro. Sin resolverlo, la mitad de
   * la frase seguiría siendo un identificador justo en el registro donde más importa.
   */
  private static final String OBJETIVO_JOIN = " LEFT JOIN users ut ON ut.id = a.target_user_id";

  private static final String OBJETIVO_COLS =
      ", ut.username AS objetivo_username, ut.first_name AS objetivo_nombre,"
          + " ut.last_name AS objetivo_apellido";

  private final EntityManager em;
  private final ObjectMapper json;

  public JpaAuditQueryRepository(EntityManager em, ObjectMapper json) {
    this.em = em;
    this.json = json;
  }

  // ---------------------------------------------------------------------------
  // RF-SP-011 — cambios
  // ---------------------------------------------------------------------------

  @Override
  @Transactional(readOnly = true)
  public List<ChangeAuditItem> changes(ListChangeAuditRequest f, int offset, int limit) {
    return pagina(
        "audit_change_log a" + ACTOR_JOIN,
        deLaTabla(
                "id, occurred_at, actor_id, module, entity, entity_id, action, changes,"
                    + " correlation_id, ip_address, user_agent")
            + ACTOR_COLS,
        predicadoDeCambios(f),
        offset,
        limit,
        fila ->
            new ChangeAuditItem(
                (UUID) fila.get("id"),
                momento(fila.get("occurred_at")),
                (UUID) fila.get("actor_id"),
                actorDe(fila),
                (String) fila.get("module"),
                (String) fila.get("entity"),
                (UUID) fila.get("entity_id"),
                (String) fila.get("action"),
                comoJson(fila.get("changes")),
                (UUID) fila.get("correlation_id"),
                texto(fila.get("ip_address")),
                (String) fila.get("user_agent")));
  }

  @Override
  @Transactional(readOnly = true)
  public BoundedCount countChanges(ListChangeAuditRequest f, int techo) {
    return conteo("audit_change_log a", predicadoDeCambios(f), techo);
  }

  private static Filtro predicadoDeCambios(ListChangeAuditRequest f) {
    Filtro filtro = comun(f);
    filtro.igual("a.module", "modulo", f.module());
    filtro.igual("a.entity", "entidad", f.entity());
    filtro.igual("a.entity_id", "registro", f.entityId());
    filtro.igual("a.actor_id", "actor", f.actorId());
    filtro.igual("a.action", "accion", f.action() == null ? null : f.action().toUpperCase());
    return filtro;
  }

  // ---------------------------------------------------------------------------
  // RF-SP-012 — eliminaciones
  // ---------------------------------------------------------------------------

  @Override
  @Transactional(readOnly = true)
  public List<DeletionAuditItem> deletions(ListDeletionAuditRequest f, int offset, int limit) {
    return pagina(
        "audit_deletion_log a" + ACTOR_JOIN,
        deLaTabla(
                "id, occurred_at, actor_id, module, entity, entity_id, deletion_type, reason,"
                    + " snapshot, correlation_id, ip_address, user_agent")
            + ACTOR_COLS,
        predicadoDeEliminaciones(f),
        offset,
        limit,
        fila ->
            new DeletionAuditItem(
                (UUID) fila.get("id"),
                momento(fila.get("occurred_at")),
                (UUID) fila.get("actor_id"),
                actorDe(fila),
                (String) fila.get("module"),
                (String) fila.get("entity"),
                (UUID) fila.get("entity_id"),
                (String) fila.get("deletion_type"),
                (String) fila.get("reason"),
                comoJson(fila.get("snapshot")),
                (UUID) fila.get("correlation_id"),
                texto(fila.get("ip_address")),
                (String) fila.get("user_agent")));
  }

  @Override
  @Transactional(readOnly = true)
  public BoundedCount countDeletions(ListDeletionAuditRequest f, int techo) {
    return conteo("audit_deletion_log a", predicadoDeEliminaciones(f), techo);
  }

  private static Filtro predicadoDeEliminaciones(ListDeletionAuditRequest f) {
    Filtro filtro = comun(f);
    filtro.igual("a.module", "modulo", f.module());
    filtro.igual("a.entity", "entidad", f.entity());
    filtro.igual("a.entity_id", "registro", f.entityId());
    filtro.igual("a.actor_id", "actor", f.actorId());
    filtro.igual(
        "a.deletion_type",
        "tipo",
        f.deletionType() == null ? null : f.deletionType().toUpperCase());

    if (f.reason() != null) {
      // La normalización la hace LA BASE DE DATOS con la misma función que
      // alimenta el índice parcial: normalizar en Java produce un resultado
      // parecido y no idéntico al del diccionario `unaccent`, y la divergencia
      // se manifiesta como una fila indexada que no aparece en su búsqueda.
      filtro.condicion(
          "f_unaccent(lower(a.reason)) LIKE f_unaccent(lower(:motivo)) ESCAPE '\\'",
          "motivo",
          "%" + escapar(f.reason()) + "%");
    }
    return filtro;
  }

  // ---------------------------------------------------------------------------
  // RF-SP-013 — errores
  // ---------------------------------------------------------------------------

  @Override
  @Transactional(readOnly = true)
  public List<ErrorAuditItem> errors(ListErrorAuditRequest f, int offset, int limit) {
    return pagina(
        "audit_error_log a" + ACTOR_JOIN,
        deLaTabla(
                "id, occurred_at, actor_id, resource, entity_id, operation, error_code,"
                    + " error_type, http_status, severity, message, correlation_id, ip_address,"
                    + " user_agent")
            + ACTOR_COLS,
        predicadoDeErrores(f),
        offset,
        limit,
        fila ->
            new ErrorAuditItem(
                (UUID) fila.get("id"),
                momento(fila.get("occurred_at")),
                (UUID) fila.get("actor_id"),
                actorDe(fila),
                (String) fila.get("resource"),
                (UUID) fila.get("entity_id"),
                (String) fila.get("operation"),
                (String) fila.get("error_code"),
                (String) fila.get("error_type"),
                ((Number) fila.get("http_status")).intValue(),
                (String) fila.get("severity"),
                (String) fila.get("message"),
                (UUID) fila.get("correlation_id"),
                texto(fila.get("ip_address")),
                (String) fila.get("user_agent")));
  }

  @Override
  @Transactional(readOnly = true)
  public BoundedCount countErrors(ListErrorAuditRequest f, int techo) {
    return conteo("audit_error_log a", predicadoDeErrores(f), techo);
  }

  private static Filtro predicadoDeErrores(ListErrorAuditRequest f) {
    Filtro filtro = comun(f);
    filtro.igual(
        "a.error_type", "tipo", f.errorType() == null ? null : f.errorType().toUpperCase());
    filtro.igual(
        "a.severity", "severidad", f.severity() == null ? null : f.severity().toUpperCase());
    filtro.igual("a.error_code", "codigo", f.errorCode());
    filtro.igual("a.resource", "recurso", f.resource());
    filtro.igual("a.actor_id", "actor", f.actorId());
    return filtro;
  }

  // ---------------------------------------------------------------------------
  // RF-SP-014 — seguridad
  // ---------------------------------------------------------------------------

  @Override
  @Transactional(readOnly = true)
  public List<SecurityAuditItem> security(ListSecurityAuditRequest f, int offset, int limit) {
    return pagina(
        "audit_security_log a" + ACTOR_JOIN + OBJETIVO_JOIN,
        deLaTabla(
                "id, occurred_at, actor_id, event_type, severity, outcome, target_user_id,"
                    + " detail, correlation_id, ip_address, user_agent")
            + ACTOR_COLS
            + OBJETIVO_COLS,
        predicadoDeSeguridad(f),
        offset,
        limit,
        fila ->
            new SecurityAuditItem(
                (UUID) fila.get("id"),
                momento(fila.get("occurred_at")),
                (UUID) fila.get("actor_id"),
                actorDe(fila),
                (String) fila.get("event_type"),
                (String) fila.get("severity"),
                (String) fila.get("outcome"),
                (UUID) fila.get("target_user_id"),
                objetivoDe(fila),
                comoJson(fila.get("detail")),
                (UUID) fila.get("correlation_id"),
                texto(fila.get("ip_address")),
                (String) fila.get("user_agent")));
  }

  @Override
  @Transactional(readOnly = true)
  public BoundedCount countSecurity(ListSecurityAuditRequest f, int techo) {
    return conteo("audit_security_log a", predicadoDeSeguridad(f), techo);
  }

  private static Filtro predicadoDeSeguridad(ListSecurityAuditRequest f) {
    Filtro filtro = comun(f);
    filtro.igual(
        "a.event_type", "evento", f.eventType() == null ? null : f.eventType().toUpperCase());
    filtro.igual(
        "a.severity", "severidad", f.severity() == null ? null : f.severity().toUpperCase());
    filtro.igual("a.outcome", "resultado", f.outcome() == null ? null : f.outcome().toUpperCase());
    filtro.igual("a.actor_id", "actor", f.actorId());
    filtro.igual("a.target_user_id", "afectado", f.targetUserId());

    if (f.ipAddress() != null) {
      // La columna es `inet`: el parámetro se convierte de forma explícita en
      // lugar de comparar textos, porque `inet` normaliza —`010.1.1.1` y
      // `10.1.1.1` son la misma dirección— y la comparación textual no.
      filtro.condicion("a.ip_address = cast(:origen AS inet)", "origen", f.ipAddress());
    }
    return filtro;
  }

  // ---------------------------------------------------------------------------
  // La mecánica común: rango, página y conteo acotado
  // ---------------------------------------------------------------------------

  /**
   * El rango de fechas y la correlación, que los cuatro registros comparten.
   *
   * <p>El rango es <b>semiabierto</b>: dos rangos consecutivos —agosto y septiembre— no devuelven
   * dos veces el evento de la medianoche del 1 de septiembre, de modo que quien recorre la línea de
   * tiempo mes a mes no cuenta de más.
   */
  private static Filtro comun(com.factech.nexus.modules.system.audit.application.AuditFilters f) {

    Filtro filtro = new Filtro();
    if (f.from() != null) {
      filtro.condicion("a.occurred_at >= :desde", "desde", f.from());
    }
    if (f.to() != null) {
      filtro.condicion("a.occurred_at < :hasta", "hasta", f.to());
    }
    filtro.igual("a.correlation_id", "correlacion", f.correlationId());
    return filtro;
  }

  private <T> List<T> pagina(
      String tabla,
      String columnas,
      Filtro filtro,
      int offset,
      int limit,
      Function<Tuple, T> comoItem) {

    Query consulta =
        em.createNativeQuery(
            "SELECT "
                + columnas
                + " FROM "
                + tabla
                + " WHERE "
                + filtro.sql()
                + " ORDER BY a.occurred_at DESC, a.id DESC"
                + " OFFSET :salto LIMIT :tope",
            Tuple.class);

    filtro.enlazar(consulta);
    consulta.setParameter("salto", offset).setParameter("tope", limit);

    List<Tuple> filas = consulta.getResultList();
    List<T> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(comoItem.apply(fila));
    }
    return resultado;
  }

  /**
   * El conteo, acotado por construcción.
   *
   * <p>La subconsulta lleva {@code LIMIT techo + 1}, de modo que la sentencia <b>nunca examina más
   * de esas filas</b>, tenga la tabla mil o cien millones. Si vuelve con el techo superado, el
   * total publicado es el techo y la respuesta lo declara inexacto.
   */
  private BoundedCount conteo(String tabla, Filtro filtro, int techo) {
    Query consulta =
        em.createNativeQuery(
            "SELECT count(*) FROM (SELECT 1 FROM "
                + tabla
                + " WHERE "
                + filtro.sql()
                + " LIMIT :techo) t");

    filtro.enlazar(consulta);
    consulta.setParameter("techo", (long) techo + 1);

    return BoundedCount.de(((Number) consulta.getSingleResult()).longValue(), techo);
  }

  /**
   * Cualifica con el alias de la tabla auditada y conserva el nombre como etiqueta.
   *
   * <p>Existe desde que hay un {@code JOIN}: {@code id} lo tienen las dos tablas, y sin cualificar
   * la sentencia no compila —ni el {@code SELECT} ni el {@code ORDER BY}—. Escribirlo así deja las
   * listas de columnas de cada consulta tal como estaban, legibles y sin repetir el alias catorce
   * veces.
   */
  private static String deLaTabla(String columnas) {
    return java.util.Arrays.stream(columnas.split(","))
        .map(String::trim)
        .map(columna -> "a." + columna + " AS " + columna)
        .collect(java.util.stream.Collectors.joining(", "));
  }

  /** El actor de la fila, o nulo si el {@code LEFT JOIN} no encontró a nadie. */
  private static AuditActor actorDe(Tuple fila) {
    return AuditActor.de(
        (String) fila.get("actor_username"),
        (String) fila.get("actor_nombre"),
        (String) fila.get("actor_apellido"));
  }

  /** La persona sobre la que recayó el evento de seguridad, con el mismo trato. */
  private static AuditActor objetivoDe(Tuple fila) {
    return AuditActor.de(
        (String) fila.get("objetivo_username"),
        (String) fila.get("objetivo_nombre"),
        (String) fila.get("objetivo_apellido"));
  }

  /**
   * Predicado y parámetros, construidos a la vez.
   *
   * <p>Van juntos a propósito: escribir la condición en un sitio y su parámetro en otro es la forma
   * habitual de que una se quede sin el otro, y el síntoma es una excepción de parámetro no
   * enlazado en tiempo de ejecución.
   */
  private static final class Filtro {

    private final StringBuilder donde = new StringBuilder("1 = 1");
    private final Map<String, Object> parametros = new LinkedHashMap<>();

    void condicion(String sql, String nombre, Object valor) {
      donde.append(" AND ").append(sql);
      parametros.put(nombre, valor);
    }

    /** Igualdad simple. Un valor nulo significa «sin filtro» y no añade nada. */
    void igual(String columna, String nombre, Object valor) {
      if (valor != null) {
        condicion(columna + " = :" + nombre, nombre, valor);
      }
    }

    String sql() {
      return donde.toString();
    }

    void enlazar(Query consulta) {
      parametros.forEach(consulta::setParameter);
    }
  }

  /**
   * Escapa lo que {@code LIKE} interpreta.
   *
   * <p>El valor va enlazado, de modo que esto no es defensa contra inyección: es que sin escapar,
   * buscar {@code 100%} devolvería el registro entero y buscar {@code _} coincidiría con cualquier
   * carácter.
   */
  private static String escapar(String termino) {
    return termino.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private JsonNode comoJson(Object valor) {
    if (valor == null) {
      return null;
    }
    try {
      // La columna es `jsonb` y el controlador JDBC la entrega como texto: se
      // devuelve como ÁRBOL y no como cadena, para que el cliente no tenga que
      // analizarla una segunda vez ni tratar como opaco algo que no lo es.
      return json.readTree(valor.toString());
    } catch (com.fasterxml.jackson.core.JsonProcessingException noEsJson) {
      throw new IllegalStateException(
          "Una columna jsonb de auditoría no contiene JSON válido", noEsJson);
    }
  }

  private static String texto(Object valor) {
    return valor == null ? null : valor.toString();
  }

  private static OffsetDateTime momento(Object valor) {
    return switch (valor) {
      case null -> null;
      case OffsetDateTime instante -> instante;
      case Instant instante -> instante.atOffset(ZoneOffset.UTC);
      case Timestamp marca -> marca.toInstant().atOffset(ZoneOffset.UTC);
      default ->
          throw new IllegalStateException(
              "Tipo temporal inesperado en la proyección: " + valor.getClass());
    };
  }
}
