package com.factech.nexus.modules.movements.domain.repository;

import com.factech.nexus.modules.movements.application.PointsFigures;
import com.factech.nexus.modules.movements.application.SalesFigures.Granularity;
import com.factech.nexus.modules.movements.application.SalesFigures.Interval;
import com.factech.nexus.shared.persistence.MinorUnits;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PointsFigures} sobre sentencias nativas (`RF-IN-005`, `plan.md` v0.3.0).
 *
 * <p><b>Suma las filas de la lista de los movimientos de puntos</b> (`RF-MV-056`): agrega sobre la
 * misma tabla derivada, {@link JpaPointsMovementQuery#MOVIMIENTOS}, con los mismos nombres de
 * columna y la misma fecha —cuándo ocurrió la compra o el ajuste, cuándo se descontó un gasto—. Hay
 * <b>una</b> definición de «movimiento de puntos», y es la de la lista: si cambia, el indicador la
 * sigue. <b>Los filtros van fuera de la derivada</b>, como en la lista, para que las dos lecturas
 * sigan siendo la misma.
 *
 * <p><b>Un tipo que no se espera es un fallo y no se ignora</b>: una cifra que se calla una fila no
 * falla, miente, y el saldo dejaría de cuadrar sin que nadie supiera por qué.
 */
@Repository
public class JpaPointsFigures implements PointsFigures {

  private static final String CIFRAS =
      " m.currency_id, c.code, m.tipo, m.status, m.points_amount > 0,"
          + " count(*), sum(m.points_amount), sum(m.importe)";

  private static final String DE_LA_LISTA =
      " FROM " + JpaPointsMovementQuery.MOVIMIENTOS + " JOIN currencies c ON c.id = m.currency_id";

  private static final String DE_LAS_CUENTAS = " a.kind = 'PUNTOS' AND a.user_id IS NOT NULL";

  private final EntityManager em;

  public JpaPointsFigures(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Flow> flows(
      Set<UUID> holders, Interval interval, UUID currencyId, String type, String status) {
    String sql =
        "SELECT"
            + CIFRAS
            + DE_LA_LISTA
            + donde(holders, interval, currencyId, type, status)
            + " GROUP BY 1, 2, 3, 4, 5";
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        enlazar(em.createNativeQuery(sql), holders, interval, currencyId, type, status)
            .getResultList();
    List<Flow> flujos = new ArrayList<>(filas.size());
    for (Object[] f : filas) {
      flujos.add(flujo(f, 0));
    }
    return flujos;
  }

  /**
   * {@link #flows} con el tramo delante, sobre la hora de la zona recibida de la fecha de la fila.
   */
  @Override
  @Transactional(readOnly = true)
  public List<BucketFlow> flowsByBucket(
      Set<UUID> holders,
      Interval interval,
      UUID currencyId,
      String type,
      String status,
      Granularity granularity,
      ZoneId zone) {
    String sql =
        "SELECT CAST(date_trunc(:unidad, m.occurred_at AT TIME ZONE :zona) AS date),"
            + CIFRAS
            + DE_LA_LISTA
            + donde(holders, interval, currencyId, type, status)
            + " GROUP BY 1, 2, 3, 4, 5, 6 ORDER BY 1";
    Query consulta =
        enlazar(em.createNativeQuery(sql), holders, interval, currencyId, type, status)
            .setParameter(
                "unidad",
                switch (granularity) {
                  case DAY -> "day";
                  case WEEK -> "week";
                  case MONTH -> "month";
                })
            .setParameter("zona", zone.getId());
    @SuppressWarnings("unchecked")
    List<Object[]> filas = consulta.getResultList();
    List<BucketFlow> tramos = new ArrayList<>(filas.size());
    for (Object[] f : filas) {
      LocalDate inicio = f[0] instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) f[0];
      tramos.add(new BucketFlow(inicio, flujo(f, 1)));
    }
    return tramos;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Balance> balances(Set<UUID> holders, UUID currencyId) {
    StringBuilder sql =
        new StringBuilder(
            "SELECT a.currency_id, c.code, sum(a.balance)"
                + " FROM accounts a JOIN currencies c ON c.id = a.currency_id"
                + " WHERE"
                + DE_LAS_CUENTAS);
    if (holders != null) {
      sql.append(" AND a.user_id IN (:titulares)");
    }
    if (currencyId != null) {
      sql.append(" AND a.currency_id = :moneda");
    }
    sql.append(" GROUP BY 1, 2");
    Query consulta = em.createNativeQuery(sql.toString());
    if (holders != null) {
      consulta.setParameter("titulares", holders);
    }
    if (currencyId != null) {
      consulta.setParameter("moneda", currencyId);
    }
    @SuppressWarnings("unchecked")
    List<Object[]> filas = consulta.getResultList();
    List<Balance> saldos = new ArrayList<>(filas.size());
    for (Object[] f : filas) {
      saldos.add(new Balance((UUID) f[0], (String) f[1], MinorUnits.fromMinor(f[2])));
    }
    return saldos;
  }

  /** Una fila agregada: moneda, tipo, estado, signo, cuántas, puntos y lo pagado. */
  private static Flow flujo(Object[] f, int de) {
    Kind clase = clase((String) f[de + 2], (Boolean) f[de + 4]);
    BigDecimal pagado =
        clase == Kind.PURCHASE ? MinorUnits.fromMinor(f[de + 7]) : MinorUnits.fromMinor(0L);
    return new Flow(
        (UUID) f[de],
        (String) f[de + 1],
        clase,
        (String) f[de + 3],
        ((Number) f[de + 5]).longValue(),
        MinorUnits.fromMinor(Math.abs(((Number) f[de + 6]).longValue())),
        pagado);
  }

  /** El tipo de la fila y, en el ajuste, el signo de sus puntos (`RN-IN-009`). */
  private static Kind clase(String tipo, boolean suma) {
    return switch (tipo) {
      case "COMPRA_PUNTOS" -> Kind.PURCHASE;
      case "GASTO_PUNTOS" -> Kind.SPENT;
      case "AJUSTE_PUNTOS" -> suma ? Kind.ADDED : Kind.REMOVED;
      default ->
          throw new IllegalStateException(
              "Un movimiento de puntos que el indicador no sabe clasificar: "
                  + tipo
                  + ". RN-IN-009 tiene que decir qué es.");
    };
  }

  /** Los filtros, fuera de la derivada y con sus nombres de columna. */
  private static String donde(
      Set<UUID> holders, Interval interval, UUID currencyId, String type, String status) {
    StringBuilder sql = new StringBuilder(" WHERE m.occurred_at < :hasta");
    if (interval.from() != null) {
      sql.append(" AND m.occurred_at >= :desde");
    }
    if (holders != null) {
      sql.append(" AND m.user_id IN (:titulares)");
    }
    if (currencyId != null) {
      sql.append(" AND m.currency_id = :moneda");
    }
    if (type != null) {
      sql.append(" AND m.tipo = :tipo");
    }
    if (status != null) {
      sql.append(" AND m.status = :estado");
    }
    return sql.toString();
  }

  private static Query enlazar(
      Query consulta,
      Set<UUID> holders,
      Interval interval,
      UUID currencyId,
      String type,
      String status) {
    consulta.setParameter("hasta", interval.to());
    if (interval.from() != null) {
      consulta.setParameter("desde", interval.from());
    }
    if (holders != null) {
      consulta.setParameter("titulares", holders);
    }
    if (currencyId != null) {
      consulta.setParameter("moneda", currencyId);
    }
    if (type != null) {
      consulta.setParameter("tipo", type);
    }
    if (status != null) {
      consulta.setParameter("estado", status);
    }
    return consulta;
  }
}
