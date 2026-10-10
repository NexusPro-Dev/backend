package com.factech.nexus.modules.system.brokers.domain.repository;

import com.factech.nexus.modules.system.brokers.application.BrokerAccountFigures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las cuentas de broker agregadas para `IN` (`RF-IN-009` · `T-02`, `RN-IN-015`).
 *
 * <p><b>Tres consultas planas</b> y el recorrido del árbol en `IN`, como hacía `RF-SP-058`: una
 * recursiva por nodo sería un {@code N + 1} de consultas caras que ninguna prueba detectaría.
 *
 * <p><b>El vendedor de una cuenta es el dueño de su origen</b> si es fuerza comercial ({@code
 * f.id}); si no tiene origen, o su dueño no lo es, {@code f.id} es nulo: lo no atribuido.
 */
@Repository
public class JpaBrokerAccountFigures implements BrokerAccountFigures {

  /** Quien porta un rol {@code VENDEDOR} y no está eliminado. */
  private static final String FUERZA =
      """
      WITH fuerza AS (
          SELECT DISTINCT u.id
            FROM users u
            JOIN user_roles ur ON ur.user_id = u.id AND ur.role_type = 'VENDEDOR'
           WHERE u.deleted_at IS NULL
      )
      """;

  /**
   * Las cuentas {@code CONSUMIDOR} con su vendedor de origen. La de un titular eliminado no cuenta;
   * la que no tiene titular, sí (`RN-SP-072`).
   */
  private static final String DESDE =
      """
        FROM user_brokers ub
        LEFT JOIN users u ON u.id = ub.user_id
        LEFT JOIN user_brokers r ON r.id = ub.referrer_account_id
        LEFT JOIN fuerza f ON f.id = r.user_id
       WHERE ub.kind = 'CONSUMIDOR'
         AND (u.id IS NULL OR u.deleted_at IS NULL)
      """;

  private final EntityManager em;

  public JpaBrokerAccountFigures(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Broker> brokers() {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery("SELECT id, name FROM brokers ORDER BY name", Tuple.class)
            .getResultList();
    return filas.stream()
        .map(fila -> new Broker((UUID) fila.get("id"), (String) fila.get("name")))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<Seller> commercialForce() {
    // `role_type` en la propia fila de `user_roles`, y un solo rol vendedor por
    // persona (`RN-SP-025`): nadie sale dos veces en el árbol.
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT u.id AS id, u.username AS username,
                       u.first_name AS first_name, u.last_name AS last_name,
                       r.code AS role_code, us.supervisor_id AS supervisor_id
                  FROM users u
                  JOIN user_roles ur ON ur.user_id = u.id AND ur.role_type = 'VENDEDOR'
                  JOIN roles r ON r.id = ur.role_id
                  LEFT JOIN user_supervisors us
                         ON us.user_id = u.id AND us.ended_at IS NULL
                 WHERE u.deleted_at IS NULL
                 ORDER BY u.username
                """,
                Tuple.class)
            .getResultList();

    List<Seller> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(
          new Seller(
              (UUID) fila.get("id"),
              (String) fila.get("username"),
              (String) fila.get("first_name"),
              (String) fila.get("last_name"),
              (String) fila.get("role_code"),
              (UUID) fila.get("supervisor_id")));
    }
    return resultado;
  }

  @Override
  @Transactional(readOnly = true)
  public List<SellerBrokerFigures> bySellerAndBroker(
      OffsetDateTime from, OffsetDateTime to, boolean allTime) {
    String creada = rango("ub.created_at", from, to);
    // `RN-IN-015` (2): cada cifra por su fecha. Sin fechas, los FTD son todas las
    // cuentas en FIRST_DEPOSIT —también las que pasaron sin aviso, sin fecha— y las
    // que operaron, todas las que tienen alguna operación.
    String ftd =
        allTime
            ? "ub.status = 'FIRST_DEPOSIT'"
            : "ub.first_deposit_at IS NOT NULL AND " + rango("ub.first_deposit_at", from, to);
    String activa =
        allTime
            ? "ub.operations_count > 0"
            : "ub.last_operation_at IS NOT NULL AND " + rango("ub.last_operation_at", from, to);

    Query consulta =
        em.createNativeQuery(
            FUERZA
                + "SELECT f.id AS vendedor, ub.broker_id AS broker,"
                + " count(*) FILTER (WHERE "
                + creada
                + ") AS cuentas,"
                + " count(*) FILTER (WHERE "
                + creada
                + " AND ub.status = 'FIRST_DEPOSIT') AS convertidas,"
                + " count(*) FILTER (WHERE "
                + creada
                + " AND ub.user_id IS NULL) AS sin_titular,"
                + " count(*) FILTER (WHERE "
                + ftd
                + ") AS ftd,"
                + " count(*) FILTER (WHERE "
                + activa
                + ") AS activas,"
                + " coalesce(sum(ub.operations_count) FILTER (WHERE "
                + activa
                + "), 0) AS operaciones "
                + DESDE
                + " GROUP BY f.id, ub.broker_id",
            Tuple.class);
    enlazar(consulta, from, to);

    @SuppressWarnings("unchecked")
    List<Tuple> filas = consulta.getResultList();
    List<SellerBrokerFigures> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(
          new SellerBrokerFigures(
              (UUID) fila.get("vendedor"),
              (UUID) fila.get("broker"),
              numero(fila.get("cuentas")),
              numero(fila.get("convertidas")),
              numero(fila.get("sin_titular")),
              numero(fila.get("ftd")),
              numero(fila.get("activas")),
              numero(fila.get("operaciones"))));
    }
    return resultado;
  }

  @Override
  @Transactional(readOnly = true)
  public List<SellerConsumers> consumersBySeller(OffsetDateTime from, OffsetDateTime to) {
    // Aparte, y sin agrupar por broker: una persona con cuentas en dos brokers es
    // UNA persona.
    Query consulta =
        em.createNativeQuery(
            FUERZA
                + "SELECT f.id AS vendedor, count(DISTINCT ub.user_id) AS consumidores "
                + DESDE
                + " AND ub.user_id IS NOT NULL AND "
                + rango("ub.created_at", from, to)
                + " GROUP BY f.id",
            Tuple.class);
    enlazar(consulta, from, to);

    @SuppressWarnings("unchecked")
    List<Tuple> filas = consulta.getResultList();
    List<SellerConsumers> resultado = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      resultado.add(
          new SellerConsumers((UUID) fila.get("vendedor"), numero(fila.get("consumidores"))));
    }
    return resultado;
  }

  @Override
  @Transactional(readOnly = true)
  public List<BucketFigures> byBucket(
      Set<UUID> sellers, OffsetDateTime from, OffsetDateTime to, String unit, ZoneId zone) {
    if (sellers != null && sellers.isEmpty()) {
      return List.of();
    }
    String deQuien = sellers == null ? " AND f.id IS NOT NULL " : " AND f.id IN (:vendedores) ";
    Query consulta =
        em.createNativeQuery(
            FUERZA
                + "SELECT dia, sum(cuentas) AS cuentas, sum(ftd) AS ftd FROM ("
                + " SELECT CAST(date_trunc(:unidad, ub.created_at AT TIME ZONE :zona) AS date)"
                + " AS dia, 1 AS cuentas, 0 AS ftd "
                + DESDE
                + deQuien
                + " AND "
                + rango("ub.created_at", from, to)
                + " UNION ALL"
                + " SELECT CAST(date_trunc(:unidad, ub.first_deposit_at AT TIME ZONE :zona) AS date),"
                + " 0, 1 "
                + DESDE
                + deQuien
                + " AND ub.first_deposit_at IS NOT NULL AND "
                + rango("ub.first_deposit_at", from, to)
                + ") x GROUP BY dia ORDER BY dia",
            Tuple.class);
    enlazar(consulta, from, to);
    consulta.setParameter("unidad", unit).setParameter("zona", zone.getId());
    if (sellers != null) {
      consulta.setParameter("vendedores", sellers);
    }

    @SuppressWarnings("unchecked")
    List<Tuple> filas = consulta.getResultList();
    Map<LocalDate, BucketFigures> porDia = new LinkedHashMap<>();
    for (Tuple fila : filas) {
      LocalDate dia = dia(fila.get("dia"));
      porDia.put(dia, new BucketFigures(dia, numero(fila.get("cuentas")), numero(fila.get("ftd"))));
    }
    return List.copyOf(porDia.values());
  }

  /** {@code columna} dentro de {@code [desde, hasta)}; un límite nulo no acota. */
  private static String rango(String columna, OffsetDateTime from, OffsetDateTime to) {
    List<String> partes = new ArrayList<>(2);
    if (from != null) {
      partes.add(columna + " >= :desde");
    }
    if (to != null) {
      partes.add(columna + " < :hasta");
    }
    return partes.isEmpty() ? "TRUE" : "(" + String.join(" AND ", partes) + ")";
  }

  private static void enlazar(Query consulta, OffsetDateTime from, OffsetDateTime to) {
    if (from != null) {
      consulta.setParameter("desde", from);
    }
    if (to != null) {
      consulta.setParameter("hasta", to);
    }
  }

  private static long numero(Object valor) {
    return valor == null ? 0L : ((Number) valor).longValue();
  }

  private static LocalDate dia(Object valor) {
    return valor instanceof Date fecha ? fecha.toLocalDate() : (LocalDate) valor;
  }
}
