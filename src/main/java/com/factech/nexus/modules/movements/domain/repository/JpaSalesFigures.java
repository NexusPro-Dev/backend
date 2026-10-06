package com.factech.nexus.modules.movements.domain.repository;

import com.factech.nexus.modules.movements.application.SalesFigures;
import com.factech.nexus.shared.persistence.MinorUnits;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SalesFigures} sobre sentencias nativas (`RF-IN-001` · `T-03`).
 *
 * <p><b>El {@code JOIN} es a las líneas y el filtro de vendedor va sobre la línea</b>, que es
 * `RN-IN-003` entero: la venta cuenta una vez ({@code count(DISTINCT m.id)}) y el importe es la
 * suma de <b>sus líneas del alcance</b>. Con todo el libro no hay predicado de vendedor, de modo
 * que las líneas sin vendedor entran — y solo entonces.
 *
 * <p>Las centésimas se suman en la base y se convierten <b>una vez, al mapear</b> ({@link
 * MinorUnits}): nunca se divide en SQL.
 */
@Repository
public class JpaSalesFigures implements SalesFigures {

  private static final String RESUMEN =
      """
      SELECT m.status, m.currency_id, c.code,
             count(DISTINCT m.id), count(*), sum(d.quantity), sum(d.line_amount)
        FROM movements m
        JOIN movement_types t ON t.id = m.movement_type_id AND t.code = 'VENTA'
        JOIN movement_details d ON d.movement_id = m.id
        JOIN currencies c ON c.id = m.currency_id
       WHERE m.occurred_at >= :desde AND m.occurred_at < :hasta
         AND m.status IN ('CONFIRMADA', 'PENDIENTE', 'ANULADA')
      """;

  private final EntityManager em;

  public JpaSalesFigures(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Summary summary(SalesScope scope, Interval interval, UUID currencyId) {
    StringBuilder sql = new StringBuilder(RESUMEN);
    if (!scope.isEverything()) {
      sql.append(" AND d.seller_id IN (:vendedores)");
    }
    if (currencyId != null) {
      sql.append(" AND m.currency_id = :moneda");
    }
    sql.append(" GROUP BY m.status, m.currency_id, c.code");

    Query consulta =
        em.createNativeQuery(sql.toString())
            .setParameter("desde", interval.from())
            .setParameter("hasta", interval.to());
    if (!scope.isEverything()) {
      consulta.setParameter("vendedores", scope.sellers());
    }
    if (currencyId != null) {
      consulta.setParameter("moneda", currencyId);
    }

    @SuppressWarnings("unchecked")
    List<Object[]> filas = consulta.getResultList();
    Map<String, Acumulado> porEstado = new LinkedHashMap<>();
    for (Object[] f : filas) {
      porEstado
          .computeIfAbsent((String) f[0], estado -> new Acumulado())
          .sumar(
              new Amount((UUID) f[1], (String) f[2], MinorUnits.fromMinor(f[6])),
              ((Number) f[3]).longValue(),
              ((Number) f[4]).longValue(),
              ((Number) f[5]).longValue());
    }
    return new Summary(
        totales(porEstado.get("CONFIRMADA")),
        totales(porEstado.get("PENDIENTE")),
        totales(porEstado.get("ANULADA")));
  }

  private static Totals totales(Acumulado acumulado) {
    return acumulado == null ? Totals.empty() : acumulado.totales();
  }

  /**
   * Lo de una situación, moneda a moneda. <b>Las cantidades se suman entre monedas y los importes
   * no</b> (`RN-IN-004`): una venta está en una sola moneda, de modo que sumar sus cuentas por
   * moneda no la cuenta dos veces.
   */
  private static final class Acumulado {
    private long ventas;
    private long lineas;
    private long unidades;
    private final List<Amount> importes = new ArrayList<>();

    void sumar(Amount importe, long ventas, long lineas, long unidades) {
      this.ventas += ventas;
      this.lineas += lineas;
      this.unidades += unidades;
      importes.add(importe);
    }

    Totals totales() {
      importes.sort(Comparator.comparing(Amount::currencyCode));
      return new Totals(ventas, lineas, unidades, importes);
    }
  }
}
