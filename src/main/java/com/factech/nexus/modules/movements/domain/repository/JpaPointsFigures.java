package com.factech.nexus.modules.movements.domain.repository;

import com.factech.nexus.modules.movements.application.PointsFigures;
import com.factech.nexus.modules.movements.application.SalesFigures.Interval;
import com.factech.nexus.shared.persistence.MinorUnits;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
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
            + " FROM movement_entries e"
            + " JOIN accounts a ON a.id = e.account_id AND"
            + DE_LAS_CUENTAS
            + " JOIN currencies c ON c.id = a.currency_id"
            + " WHERE e.created_at >= :desde AND e.created_at < :hasta"
            + filtros(holders, currencyId)
            + " GROUP BY 1, 2, 3, 4";
    Query consulta =
        enlazar(em.createNativeQuery(sql), holders, currencyId)
            .setParameter("desde", interval.from())
            .setParameter("hasta", interval.to());

    @SuppressWarnings("unchecked")
    List<Object[]> filas = consulta.getResultList();
    List<Flow> flujos = new ArrayList<>(filas.size());
    for (Object[] f : filas) {
      String evento = (String) f[2];
      boolean entra = (Boolean) f[3];
      flujos.add(
          new Flow(
              (UUID) f[0],
              (String) f[1],
              clase(evento, entra),
              MinorUnits.fromMinor(Math.abs(((Number) f[4]).longValue())),
              ((Number) f[5]).longValue()));
    }
    return flujos;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Balance> balances(Set<UUID> holders, UUID currencyId) {
    String sql =
        "SELECT a.currency_id, c.code, sum(a.balance)"
            + " FROM accounts a JOIN currencies c ON c.id = a.currency_id"
            + " WHERE"
            + DE_LAS_CUENTAS
            + filtros(holders, currencyId)
            + " GROUP BY 1, 2";
    @SuppressWarnings("unchecked")
    List<Object[]> filas = enlazar(em.createNativeQuery(sql), holders, currencyId).getResultList();
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
