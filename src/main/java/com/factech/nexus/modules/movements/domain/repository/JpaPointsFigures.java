package com.factech.nexus.modules.movements.domain.repository;

import com.factech.nexus.modules.movements.application.PointsFigures;
import com.factech.nexus.modules.movements.application.SalesFigures.Granularity;
import com.factech.nexus.modules.movements.application.SalesFigures.Interval;
import com.factech.nexus.shared.persistence.MinorUnits;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PointsFigures} sobre sentencias nativas (`RF-IN-005` · `T-02`, `plan.md` §4.4).
 *
 * <p><b>Un evento que no se espera es un fallo y no se ignora.</b> Si mañana algo nuevo mueve
 * puntos, este indicador tiene que enterarse: una cifra que se calla un movimiento no falla,
 * miente, y {@code CA-IN-044} —el saldo como suma de las clases— dejaría de cuadrar sin que nadie
 * supiera por qué.
 */
@Repository
public class JpaPointsFigures implements PointsFigures {

  private static final String DE_LAS_CUENTAS = " a.kind = 'PUNTOS' AND a.user_id IS NOT NULL";

  private static final String DE_LOS_ASIENTOS =
      " FROM movement_entries e"
          + " JOIN accounts a ON a.id = e.account_id AND"
          + DE_LAS_CUENTAS
          + " JOIN currencies c ON c.id = a.currency_id";

  private final EntityManager em;

  public JpaPointsFigures(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Flow> flows(Set<UUID> holders, Interval interval, UUID currencyId) {
    String sql =
        "SELECT a.currency_id, c.code, e.event, e.amount > 0, sum(e.amount),"
            + " count(DISTINCT e.movement_id)"
            + DE_LOS_ASIENTOS
            + periodo(interval)
            + filtros(holders, currencyId)
            + " GROUP BY 1, 2, 3, 4";
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        enlazar(em.createNativeQuery(sql), holders, interval, currencyId).getResultList();
    List<Flow> flujos = new ArrayList<>(filas.size());
    for (Object[] f : filas) {
      flujos.add(flujo(f, 0));
    }
    return flujos;
  }

  /**
   * {@link #flows} con el tramo delante (`RN-IN-010`): el mismo predicado y el mismo mapeo, y el
   * tramo sobre la hora de la zona recibida de {@code e.created_at}, que es cuándo se movieron los
   * puntos.
   */
  @Override
  @Transactional(readOnly = true)
  public List<BucketFlow> flowsByBucket(
      Set<UUID> holders, Interval interval, UUID currencyId, Granularity granularity, ZoneId zone) {
    String sql =
        "SELECT CAST(date_trunc(:unidad, e.created_at AT TIME ZONE :zona) AS date),"
            + " a.currency_id, c.code, e.event, e.amount > 0, sum(e.amount),"
            + " count(DISTINCT e.movement_id)"
            + DE_LOS_ASIENTOS
            + periodo(interval)
            + filtros(holders, currencyId)
            + " GROUP BY 1, 2, 3, 4, 5 ORDER BY 1";
    Query consulta =
        enlazar(em.createNativeQuery(sql), holders, interval, currencyId)
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

  private static Flow flujo(Object[] f, int de) {
    return new Flow(
        (UUID) f[de],
        (String) f[de + 1],
        clase((String) f[de + 2], (Boolean) f[de + 3]),
        MinorUnits.fromMinor(Math.abs(((Number) f[de + 4]).longValue())),
        ((Number) f[de + 5]).longValue());
  }

  /** Hasta, siempre; desde, si lo hay —sin él es toda la historia, `RN-IN-010`—. */
  private static String periodo(Interval interval) {
    return " WHERE e.created_at < :hasta"
        + (interval.from() != null ? " AND e.created_at >= :desde" : "");
  }

  @Override
  @Transactional(readOnly = true)
  public List<Balance> balances(Set<UUID> holders, Interval interval, UUID currencyId) {
    // Los mismos asientos que las cuatro clases, hasta el fin del intervalo: el saldo de cualquier
    // cierre, y sin `from` cuadra por construcción con ellas (`CA-IN-044`). `accounts.balance`
    // solo sabría el de hoy.
    String sql =
        "SELECT a.currency_id, c.code, sum(e.amount)"
            + DE_LOS_ASIENTOS
            + " WHERE e.created_at < :hasta"
            + filtros(holders, currencyId)
            + " GROUP BY 1, 2";
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        enlazar(em.createNativeQuery(sql).setParameter("hasta", interval.to()), holders, currencyId)
            .getResultList();
    List<Balance> saldos = new ArrayList<>(filas.size());
    for (Object[] f : filas) {
      saldos.add(new Balance((UUID) f[0], (String) f[1], MinorUnits.fromMinor(f[2])));
    }
    return saldos;
  }

  /** El evento y el signo del asiento dicen la clase (`RN-IN-009`). */
  private static Kind clase(String evento, boolean entra) {
    if ("ABONO".equals(evento) && entra) {
      return Kind.PURCHASED;
    }
    if ("PAGO".equals(evento) && !entra) {
      return Kind.REDEEMED;
    }
    if ("AJUSTE".equals(evento)) {
      return entra ? Kind.ADDED : Kind.REMOVED;
    }
    throw new IllegalStateException(
        "Un asiento de puntos que el indicador no sabe clasificar: evento "
            + evento
            + (entra ? ", entrada" : ", salida")
            + ". RN-IN-009 tiene que decir qué es.");
  }

  private static String filtros(Set<UUID> holders, UUID currencyId) {
    StringBuilder sql = new StringBuilder();
    if (holders != null) {
      sql.append(" AND a.user_id IN (:titulares)");
    }
    if (currencyId != null) {
      sql.append(" AND a.currency_id = :moneda");
    }
    return sql.toString();
  }

  private static Query enlazar(
      Query consulta, Set<UUID> holders, Interval interval, UUID currencyId) {
    consulta.setParameter("hasta", interval.to());
    if (interval.from() != null) {
      consulta.setParameter("desde", interval.from());
    }
    return enlazar(consulta, holders, currencyId);
  }

  private static Query enlazar(Query consulta, Set<UUID> holders, UUID currencyId) {
    if (holders != null) {
      consulta.setParameter("titulares", holders);
    }
    if (currencyId != null) {
      consulta.setParameter("moneda", currencyId);
    }
    return consulta;
  }
}
