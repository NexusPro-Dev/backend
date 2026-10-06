package com.factech.nexus.modules.movements.domain.repository;

import com.factech.nexus.modules.movements.application.SalesFigures;
import com.factech.nexus.shared.persistence.MinorUnits;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SalesFigures} sobre sentencias nativas (`RF-IN-001` · `T-03`, `RF-IN-002` · `T-01`).
 *
 * <p><b>El {@code JOIN} es a las líneas y el filtro de vendedor va sobre la línea</b>, que es
 * `RN-IN-003` entero: la venta cuenta una vez ({@code count(DISTINCT m.id)}) y el importe es la
 * suma de <b>sus líneas del alcance</b>. Con todo el libro no hay predicado de vendedor, de modo
 * que las líneas sin vendedor entran — y solo entonces.
 *
 * <p><b>El predicado vive en un solo sitio</b> ({@link #donde}) y lo comparten todas las lecturas:
 * la serie tiene que sumar exactamente lo confirmado del resumen (`CA-IN-015`), y dos copias del
 * filtro divergirían sin fallar.
 *
 * <p>Las centésimas se suman en la base y se convierten <b>una vez, al mapear</b> ({@link
 * MinorUnits}): nunca se divide en SQL.
 */
@Repository
public class JpaSalesFigures implements SalesFigures {

  private static final String DE_LAS_VENTAS =
      """
        FROM movements m
        JOIN movement_types t ON t.id = m.movement_type_id AND t.code = 'VENTA'
        JOIN movement_details d ON d.movement_id = m.id
        JOIN currencies c ON c.id = m.currency_id
       WHERE m.occurred_at >= :desde AND m.occurred_at < :hasta
      """;

  private static final String CIFRAS =
      "count(DISTINCT m.id), count(*), sum(d.quantity), sum(d.line_amount)";

  private final EntityManager em;

  public JpaSalesFigures(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Summary summary(SalesScope scope, Interval interval, UUID currencyId) {
    String sql =
        "SELECT m.status, m.currency_id, c.code, "
            + CIFRAS
            + DE_LAS_VENTAS
            + " AND m.status IN ('CONFIRMADA', 'PENDIENTE', 'ANULADA')"
            + donde(scope, currencyId)
            + " GROUP BY m.status, m.currency_id, c.code";

    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        enlazar(em.createNativeQuery(sql), scope, interval, currencyId).getResultList();
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

  /**
   * Agrupa por {@code date_trunc} sobre la hora de la zona recibida: {@code AT TIME ZONE} pasa el
   * instante a la hora local, y el corte de un día de Bogotá no es un desplazamiento de un día UTC.
   * {@code date_trunc('week', …)} de PostgreSQL empieza en <b>lunes</b>, que es lo que pide
   * `RF-IN-002` §2.2. Se agrupa por posición porque la expresión lleva parámetros.
   */
  @Override
  @Transactional(readOnly = true)
  public List<Bucket> confirmedByBucket(
      SalesScope scope, Interval interval, UUID currencyId, Granularity granularity, ZoneId zone) {
    String sql =
        "SELECT CAST(date_trunc(:unidad, m.occurred_at AT TIME ZONE :zona) AS date),"
            + " m.currency_id, c.code, "
            + CIFRAS
            + DE_LAS_VENTAS
            + " AND m.status = 'CONFIRMADA'"
            + donde(scope, currencyId)
            + " GROUP BY 1, 2, 3 ORDER BY 1, 3";

    Query consulta =
        enlazar(em.createNativeQuery(sql), scope, interval, currencyId)
            .setParameter("unidad", unidad(granularity))
            .setParameter("zona", zone.getId());
    @SuppressWarnings("unchecked")
    List<Object[]> filas = consulta.getResultList();
    List<Bucket> tramos = new ArrayList<>(filas.size());
    for (Object[] f : filas) {
      tramos.add(
          new Bucket(
              dia(f[0]),
              (UUID) f[1],
              (String) f[2],
              ((Number) f[3]).longValue(),
              ((Number) f[4]).longValue(),
              ((Number) f[5]).longValue(),
              MinorUnits.fromMinor(f[6])));
    }
    return tramos;
  }

  /** El alcance y la moneda: lo que comparten todas las lecturas. */
  private static String donde(SalesScope scope, UUID currencyId) {
    StringBuilder sql = new StringBuilder();
    if (!scope.isEverything()) {
      sql.append(" AND d.seller_id IN (:vendedores)");
    }
    if (currencyId != null) {
      sql.append(" AND m.currency_id = :moneda");
    }
    return sql.toString();
  }

  private static Query enlazar(
      Query consulta, SalesScope scope, Interval interval, UUID currencyId) {
    consulta.setParameter("desde", interval.from()).setParameter("hasta", interval.to());
    if (!scope.isEverything()) {
      consulta.setParameter("vendedores", scope.sellers());
    }
    if (currencyId != null) {
      consulta.setParameter("moneda", currencyId);
    }
    return consulta;
  }

  private static String unidad(Granularity granularity) {
    return switch (granularity) {
      case DAY -> "day";
      case WEEK -> "week";
      case MONTH -> "month";
    };
  }

  private static LocalDate dia(Object valor) {
    if (valor instanceof LocalDate d) {
      return d;
    }
    if (valor instanceof java.sql.Date d) {
      return d.toLocalDate();
    }
    throw new IllegalStateException("Tipo de fecha inesperado: " + valor.getClass());
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
