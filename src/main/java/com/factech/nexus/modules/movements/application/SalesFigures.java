package com.factech.nexus.modules.movements.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * <b>Las cifras de lo vendido</b>, publicadas para `IN` (`RF-IN-001` · `T-02`; D-25).
 *
 * <p>Es la forma de {@link CommissionableLines}: `MV` sabe qué se vendió, a quién se atribuye y
 * cuándo, y <b>no sabe qué es un indicador</b>. Quien pregunta le da el alcance ya resuelto, el
 * intervalo y los filtros, y recibe sumas, nunca filas.
 *
 * <p><b>Lo que cuenta, en todos los métodos</b> (`RN-IN-003`, `RN-IN-005`):
 *
 * <ul>
 *   <li>Solo movimientos de tipo {@code VENTA}: la compra de puntos no vende un producto.
 *   <li><b>Por línea</b>: el importe es la suma de las líneas cuyo vendedor está en el alcance, y
 *       una venta cuenta <b>una vez</b> si alguna de sus líneas está dentro.
 *   <li>Las líneas <b>sin vendedor</b> solo entran con {@link SalesScope#everything()}: con un
 *       conjunto de vendedores no hay vendedor que las ponga dentro.
 *   <li>El intervalo es sobre cuándo <b>ocurrió</b> la venta, semiabierto.
 *   <li>Los importes, <b>por moneda</b> y nunca sumados entre monedas (`RN-IN-004`), en decimales.
 * </ul>
 */
public interface SalesFigures {

  /**
   * Lo confirmado, lo pendiente y lo anulado del intervalo, en el alcance (`RF-IN-001`).
   *
   * @param currencyId si no es nulo, solo lo vendido en esa moneda
   */
  Summary summary(SalesScope scope, Interval interval, UUID currencyId);

  /**
   * Lo confirmado del intervalo partido en tramos de calendario de {@code zone} (`RF-IN-002`): una
   * fila por tramo <b>y moneda</b>, solo de los tramos con ventas. Rellenar los vacíos es de quien
   * pregunta, que es quien decide que se dibujan.
   *
   * <p>La semana empieza el <b>lunes</b> y el mes el día uno. El primer y el último tramo salen ya
   * recortados por el intervalo, porque es el intervalo el que filtra.
   *
   * @param zone en qué zona se corta cada día; `MV` no la decide, la recibe
   */
  List<Bucket> confirmedByBucket(
      SalesScope scope, Interval interval, UUID currencyId, Granularity granularity, ZoneId zone);

  /**
   * El resumen de {@link #summary} partido en tramos de calendario de {@code zone} (`RF-IN-001`,
   * `RN-IN-010`): uno por tramo <b>con ventas</b>, con su inicio. Los vacíos los rellena quien
   * pregunta, como en {@link #confirmedByBucket}.
   */
  List<BucketSummary> summaryByBucket(
      SalesScope scope, Interval interval, UUID currencyId, Granularity granularity, ZoneId zone);

  /** El resumen de un tramo. */
  record BucketSummary(LocalDate start, Summary summary) {}

  /** El tamaño de un tramo. */
  enum Granularity {
    DAY,
    WEEK,
    MONTH
  }

  /**
   * Lo confirmado de un tramo en una moneda.
   *
   * @param start el día en que empieza el tramo en el calendario, aunque el intervalo lo recorte
   */
  record Bucket(
      LocalDate start,
      UUID currencyId,
      String currencyCode,
      long sales,
      long lines,
      long units,
      BigDecimal amount) {}

  /**
   * A qué vendedores se acota la suma. <b>No tiene «ninguno»</b>: el corte fuera del alcance es una
   * regla de quien pregunta, y nunca llega aquí.
   *
   * @param sellers nulo es todo el libro, con las líneas sin vendedor; si no, esos vendedores
   */
  record SalesScope(Set<UUID> sellers) {
    public SalesScope {
      if (sellers != null) {
        if (sellers.isEmpty()) {
          throw new IllegalArgumentException("Un alcance sin vendedores se corta antes de sumar");
        }
        sellers = Set.copyOf(sellers);
      }
    }

    public static SalesScope everything() {
      return new SalesScope(null);
    }

    public static SalesScope sellers(Set<UUID> sellers) {
      return new SalesScope(sellers);
    }

    public boolean isEverything() {
      return sellers == null;
    }
  }

  /**
   * De {@code from} incluido a {@code to} excluido. `MV` no sabe de días ni de zonas.
   *
   * @param from nulo es <b>sin límite inferior</b>: toda la historia hasta {@code to} (`RN-IN-010`)
   */
  record Interval(OffsetDateTime from, OffsetDateTime to) {
    public Interval {
      if (from != null && !from.isBefore(to)) {
        throw new IllegalArgumentException("El intervalo debe empezar antes de acabar");
      }
    }
  }

  /** Un importe en una moneda. */
  record Amount(UUID currencyId, String currencyCode, BigDecimal amount) {}

  /**
   * Las cifras de una situación de la venta.
   *
   * @param free cuántas de esas ventas fueron <b>gratuitas</b>: su importe a pagar, el de la venta
   *     entera, es cero (`RN-IN-008`, 06-10-2026). Siguen contando en {@code sales}
   */
  record Totals(long sales, long lines, long units, long free, List<Amount> amounts) {
    public Totals {
      amounts = List.copyOf(amounts);
    }

    public static Totals empty() {
      return new Totals(0, 0, 0, 0, List.of());
    }
  }

  /** Lo confirmado, lo pendiente y lo anulado. */
  record Summary(Totals confirmed, Totals pending, Totals voided) {
    public static Summary empty() {
      return new Summary(Totals.empty(), Totals.empty(), Totals.empty());
    }
  }
}
