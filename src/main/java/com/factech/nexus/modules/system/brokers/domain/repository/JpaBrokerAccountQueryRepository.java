package com.factech.nexus.modules.system.brokers.domain.repository;

import com.factech.nexus.modules.system.brokers.application.BrokerAccountItem;
import com.factech.nexus.modules.system.brokers.application.BrokerAccountItem.BrokerRef;
import com.factech.nexus.modules.system.brokers.application.TeamBrokerAccountItem;
import com.factech.nexus.modules.system.brokers.application.TeamBrokerAccountItem.Holder;
import com.factech.nexus.modules.system.brokers.domain.models.UserBrokerStatus;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerAccountQueryRepository.BrokerAccountFilters;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de lectura de {@code user_brokers} (`RF-SP-055` · `T-04`, `RF-SP-056` · `T-01`).
 *
 * <p><b>Cruza tablas de dos submódulos de `SP`</b> —{@code user_brokers} y {@code brokers} son de
 * brokers; {@code users} y {@code user_supervisors} son de usuarios— y eso está admitido porque
 * <b>el módulo es el mismo</b>: `SP` es dueño de las cuatro. Lo que `D-25` prohíbe es que `PM` o
 * `MV` entren aquí, y para eso están los puertos publicados.
 *
 * <p><b>La alternativa —resolver el equipo con {@code UserRepository.findTeam} y consultar las
 * cuentas de cada miembro— es un {@code N + 1} que ninguna prueba detectaría</b>: la respuesta
 * sería correcta y solo lenta. Es la lección que `RF-SP-042` dejó escrita al resolver los roles de
 * toda la respuesta en una sola consulta.
 */
@Repository
public class JpaBrokerAccountQueryRepository implements BrokerAccountQueryRepository {

  private final EntityManager em;

  public JpaBrokerAccountQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<BrokerAccountItem> findByUser(UUID userId) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT ub.id AS id, ub.external_id AS external_id,
                       ub.broker_username AS broker_username, ub.status AS status,
                       ub.created_at AS created_at,
                       b.id AS broker_id, b.name AS broker_name
                  FROM user_brokers ub
                  JOIN brokers b ON b.id = ub.broker_id
                 WHERE ub.user_id = CAST(:persona AS uuid)
                 ORDER BY b.name, ub.external_id
                """,
                Tuple.class)
            .setParameter("persona", userId)
            .getResultList();

    List<BrokerAccountItem> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(
          new BrokerAccountItem(
              (UUID) fila.get("id"),
              new BrokerRef((UUID) fila.get("broker_id"), (String) fila.get("broker_name")),
              (String) fila.get("external_id"),
              (String) fila.get("broker_username"),
              estado(fila),
              momento(fila.get("created_at"))));
    }
    return resultado;
  }

  @Override
  @Transactional(readOnly = true)
  public List<TeamBrokerAccountItem> findByTeamOf(
      UUID supervisorId, UserBrokerStatus estado, UUID brokerId, int offset, int limit) {

    Query consulta =
        em.createNativeQuery(
            """
            SELECT ub.id AS id, ub.external_id AS external_id,
                   ub.broker_username AS broker_username, ub.status AS status,
                   ub.created_at AS created_at,
                   b.id AS broker_id, b.name AS broker_name,
                   u.id AS user_id, u.username AS username,
                   u.first_name AS first_name, u.last_name AS last_name
              FROM user_supervisors us
              JOIN users u ON u.id = us.user_id
              JOIN user_brokers ub ON ub.user_id = u.id
              JOIN brokers b ON b.id = ub.broker_id
            """
                + DONDE
                // Tres desempates, y el último es único: sin él, dos páginas
                // consecutivas pueden repetir una fila y omitir otra sin que
                // nada falle. Es la lección de `findTeam` en `RF-SP-042`.
                + """
                 ORDER BY u.username, b.name, ub.external_id
                 OFFSET :salto LIMIT :tope
                """,
            Tuple.class);
    enlazar(consulta, supervisorId, estado, brokerId);
    consulta.setParameter("salto", offset);
    consulta.setParameter("tope", limit);

    @SuppressWarnings("unchecked")
    List<Tuple> filas = consulta.getResultList();

    return conFilas(filas);
  }

  @Override
  @Transactional(readOnly = true)
  public int countByTeamOf(UUID supervisorId, UserBrokerStatus estado, UUID brokerId) {
    Query consulta =
        em.createNativeQuery(
            """
            SELECT count(*)
              FROM user_supervisors us
              JOIN users u ON u.id = us.user_id
              JOIN user_brokers ub ON ub.user_id = u.id
              JOIN brokers b ON b.id = ub.broker_id
            """
                + DONDE);
    enlazar(consulta, supervisorId, estado, brokerId);

    return ((Number) consulta.getSingleResult()).intValue();
  }

  // ---------------------------------------------------------------------------
  // `RF-SP-057` — todas las cuentas, filtradas
  // ---------------------------------------------------------------------------

  @Override
  @Transactional(readOnly = true)
  public List<TeamBrokerAccountItem> findAll(BrokerAccountFilters filtros, int offset, int limit) {

    Filtro filtro = predicado(filtros);
    Query consulta =
        em.createNativeQuery(
            recursivaSiHace(filtros)
                + COLUMNAS
                + DESDE_GLOBAL
                + " WHERE "
                + filtro.sql()
                // Los mismos tres desempates que el listado del equipo, y el
                // último es único: sin él dos páginas seguidas pueden repetir
                // una fila y omitir otra sin que nada falle.
                + """
                 ORDER BY u.username, b.name, ub.external_id
                 OFFSET :salto LIMIT :tope
                """,
            Tuple.class);
    filtro.enlazar(consulta);
    consulta.setParameter("salto", offset);
    consulta.setParameter("tope", limit);

    @SuppressWarnings("unchecked")
    List<Tuple> filas = consulta.getResultList();

    return conFilas(filas);
  }

  @Override
  @Transactional(readOnly = true)
  public int countAll(BrokerAccountFilters filtros) {
    Filtro filtro = predicado(filtros);
    Query consulta =
        em.createNativeQuery(
            recursivaSiHace(filtros)
                + " SELECT count(*) "
                + DESDE_GLOBAL
                + " WHERE "
                + filtro.sql());
    filtro.enlazar(consulta);

    return ((Number) consulta.getSingleResult()).intValue();
  }

  /**
   * <b>La red de una persona, en profundidad.</b> La única consulta recursiva del sistema.
   *
   * <p>Cuatro decisiones, y ninguna es de estilo:
   *
   * <ul>
   *   <li><b>{@code UNION} y no {@code UNION ALL}.</b> Es lo que hace que la consulta <b>no pueda
   *       colgarse</b> aunque los datos tuvieran un ciclo: quien ya se vio no se reexpande. Que el
   *       ciclo no deba existir —`RN-SP-020` ata esta cadena a la de roles, que es acíclica— es un
   *       argumento sobre los datos, y <b>la terminación no debería depender de un argumento</b>.
   *   <li><b>{@code ended_at IS NULL} en LOS DOS brazos.</b> Omitirlo en el recursivo haría
   *       descender por la estructura <b>de ayer</b> sin que nada fallara — el error que no rompe
   *       nada y devuelve de más. Además es lo que deja usar {@code
   *       ix_user_supervisors_supervisor_vigente}, que es parcial sobre ese predicado.
   *   <li><b>La raíz NO se siembra.</b> El brazo base son <b>sus subordinados</b>, no ella: por eso
   *       su red la excluye. Quien quiera además las suyas las pide con {@code userId}.
   *   <li><b>{@code deleted_at} NO se filtra aquí</b>, sino fuera, al unir con {@code users}.
   *       Dentro <b>cortaría la rama</b>: una persona eliminada dejaría de expandirse y sus
   *       subordinados desaparecerían del resultado aunque sigan vivos y colgando de ella.
   * </ul>
   */
  private static String recursivaSiHace(BrokerAccountFilters filtros) {
    if (filtros.supervisorId() == null) {
      return "";
    }
    return """
        WITH RECURSIVE red AS (
            SELECT us.user_id
              FROM user_supervisors us
             WHERE us.supervisor_id = CAST(:raiz AS uuid)
               AND us.ended_at IS NULL
            UNION
            SELECT us.user_id
              FROM user_supervisors us
              JOIN red r ON us.supervisor_id = r.user_id
             WHERE us.ended_at IS NULL
        )
        """;
  }

  private static final String COLUMNAS =
      """
      SELECT ub.id AS id, ub.external_id AS external_id,
             ub.broker_username AS broker_username, ub.status AS status,
             ub.created_at AS created_at,
             b.id AS broker_id, b.name AS broker_name,
             u.id AS user_id, u.username AS username,
             u.first_name AS first_name, u.last_name AS last_name
      """;

  private static final String DESDE_GLOBAL =
      """
        FROM user_brokers ub
        JOIN users u ON u.id = ub.user_id
        JOIN brokers b ON b.id = ub.broker_id
      """;

  /**
   * Los siete filtros, escritos UNA vez y compartidos por la página y el conteo.
   *
   * <p>Dos copias son lo que hace que un día el total cuente una cosa y la página muestre otra, y
   * que nadie lo note hasta que un cliente lo reporte.
   */
  private static Filtro predicado(BrokerAccountFilters f) {
    Filtro filtro = new Filtro();

    // FUERA de la recursiva a propósito: ver el javadoc de `recursivaSiHace`.
    filtro.condicion("u.deleted_at IS NULL");

    if (f.supervisorId() != null) {
      filtro.condicion("ub.user_id IN (SELECT user_id FROM red)", "raiz", f.supervisorId());
    }
    filtro.igual("ub.user_id", "persona", f.userId());
    filtro.igual("ub.broker_id", "broker", f.brokerId());
    if (f.status() != null) {
      filtro.condicion("ub.status = :estado", "estado", f.status().name());
    }
    if (f.from() != null) {
      filtro.condicion("ub.created_at >= :desde", "desde", f.from());
    }
    if (f.to() != null) {
      // SEMIABIERTO —incluye `from`, excluye `to`—, como los cuatro listados de
      // auditoría. Con `<=`, pedir dos rangos consecutivos contaría dos veces
      // lo que cayera justo en el borde.
      filtro.condicion("ub.created_at < :hasta", "hasta", f.to());
    }
    if (f.search() != null) {
      // Las expresiones son LAS DEL ÍNDICE (`ix_user_brokers_busqueda` y
      // `ix_users_busqueda`): si divergieran, los índices existirían y el
      // planificador no los usaría nunca — y el defecto saldría como lentitud,
      // no como error.
      filtro.condicion(
          """
          (f_unaccent(lower(ub.external_id)) LIKE f_unaccent(lower(:termino)) ESCAPE '\\'
            OR f_unaccent(lower(u.username)) LIKE f_unaccent(lower(:termino)) ESCAPE '\\'
            OR f_unaccent(lower(u.email)) LIKE f_unaccent(lower(:termino)) ESCAPE '\\'
            OR f_unaccent(lower(u.first_name || ' ' || u.last_name))
               LIKE f_unaccent(lower(:termino)) ESCAPE '\\')
          """,
          "termino",
          "%" + escapar(f.search()) + "%");
    }
    return filtro;
  }

  /**
   * Escapa lo que {@code LIKE} interpreta.
   *
   * <p>El valor va enlazado, de modo que esto no es defensa contra inyección: es que sin escapar,
   * buscar {@code 100%} devolvería la tabla entera y {@code _} coincidiría con cualquier carácter.
   */
  private static String escapar(String termino) {
    return termino.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  /**
   * Predicado y parámetros, construidos a la vez.
   *
   * <p>Van juntos a propósito, como en {@code JpaAuditQueryRepository}: escribir la condición en un
   * sitio y su parámetro en otro es la forma habitual de que una se quede sin el otro, y el síntoma
   * es una excepción de parámetro no enlazado en tiempo de ejecución.
   */
  private static final class Filtro {

    private final StringBuilder donde = new StringBuilder("1 = 1");
    private final java.util.Map<String, Object> parametros = new java.util.LinkedHashMap<>();

    void condicion(String sql) {
      donde.append(" AND ").append(sql);
    }

    void condicion(String sql, String nombre, Object valor) {
      condicion(sql);
      parametros.put(nombre, valor);
    }

    /** Igualdad simple. Un valor nulo significa «sin filtro» y no añade nada. */
    void igual(String columna, String nombre, Object valor) {
      if (valor != null) {
        condicion(columna + " = CAST(:" + nombre + " AS uuid)", nombre, valor);
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
   * La fila, mapeada en UN solo sitio.
   *
   * <p>La comparten el listado del equipo y el de administración, y esa igualdad <b>es
   * contrato</b>: el frontend pinta las dos pantallas con un solo componente.
   */
  private static List<TeamBrokerAccountItem> conFilas(List<Tuple> filas) {
    List<TeamBrokerAccountItem> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(
          new TeamBrokerAccountItem(
              (UUID) fila.get("id"),
              new Holder(
                  (UUID) fila.get("user_id"),
                  (String) fila.get("username"),
                  (String) fila.get("first_name"),
                  (String) fila.get("last_name")),
              new BrokerRef((UUID) fila.get("broker_id"), (String) fila.get("broker_name")),
              (String) fila.get("external_id"),
              (String) fila.get("broker_username"),
              estado(fila),
              momento(fila.get("created_at"))));
    }
    return resultado;
  }

  /**
   * El predicado, escrito UNA vez y compartido por la página y el conteo.
   *
   * <p>Escribirlo dos veces es lo que hace que un día el total cuente una cosa y la página muestre
   * otra, y que nadie lo note hasta que un cliente lo reporte.
   *
   * <p><b>Los dos filtros se anulan con {@code :x IS NULL OR ...}</b>, y aquí sí se admite el
   * patrón que {@code JpaBrokerQueryRepository} evita: allí lo que se ahorraba era un recorrido del
   * catálogo entero, y aquí el filtro selectivo —el equipo vigente— va antes y ya lo aplica el
   * índice parcial de `V28`.
   */
  private static final String DONDE =
      """
       WHERE us.supervisor_id = CAST(:superior AS uuid)
         AND us.ended_at IS NULL
         AND u.deleted_at IS NULL
         AND (CAST(:estado AS varchar) IS NULL OR ub.status = CAST(:estado AS varchar))
         AND (CAST(:broker AS uuid) IS NULL OR ub.broker_id = CAST(:broker AS uuid))
      """;

  private static void enlazar(
      Query consulta, UUID supervisorId, UserBrokerStatus estado, UUID brokerId) {
    consulta.setParameter("superior", supervisorId);
    consulta.setParameter("estado", estado == null ? null : estado.name());
    consulta.setParameter("broker", brokerId);
  }

  /**
   * Una consulta nativa entrega {@link java.time.Instant}, no {@link OffsetDateTime}.
   *
   * <p>Misma conversión que {@code JpaUserRepository.momento} y que el módulo de sesión. Se repite
   * en lugar de compartirse porque llevarla a {@code shared} publicaría como utilidad común un
   * detalle del acceso nativo a datos, que es justo lo que ninguna de las dos capas quiere heredar
   * de la otra.
   */
  private static OffsetDateTime momento(Object valor) {
    return switch (valor) {
      case null -> null;
      case OffsetDateTime instante -> instante;
      case java.time.Instant instante -> instante.atOffset(java.time.ZoneOffset.UTC);
      case java.sql.Timestamp marca -> marca.toInstant().atOffset(java.time.ZoneOffset.UTC);
      default ->
          throw new IllegalStateException(
              "Tipo temporal inesperado en la proyección: " + valor.getClass());
    };
  }

  /**
   * El estado tal como lo guarda el motor.
   *
   * <p><b>Falla si no lo reconoce, y no cae a {@code REGISTER}</b>: el `CHECK` de la tabla no
   * admite otra cosa, de modo que un valor desconocido aquí significa que alguien cambió el esquema
   * sin cambiar este enumerado — y disimularlo devolvería un dato falso en lugar de un fallo.
   */
  private static UserBrokerStatus estado(Tuple fila) {
    String valor = (String) fila.get("status");
    return UserBrokerStatus.de(valor)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "user_brokers.status trae un valor que el dominio no conoce: " + valor));
  }
}
