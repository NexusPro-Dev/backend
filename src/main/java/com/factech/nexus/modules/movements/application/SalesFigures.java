package com.factech.nexus.modules.movements.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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

  /** De {@code from} incluido a {@code to} excluido. `MV` no sabe de días ni de zonas. */
  record Interval(OffsetDateTime from, OffsetDateTime to) {
    public Interval {
      if (!from.isBefore(to)) {
        throw new IllegalArgumentException("El intervalo debe empezar antes de acabar");
      }
    }
  }

  /** Un importe en una moneda. */
  record Amount(UUID currencyId, String currencyCode, BigDecimal amount) {}

  /** Las cifras de una situación de la venta. */
  record Totals(long sales, long lines, long units, List<Amount> amounts) {
    public Totals {
      amounts = List.copyOf(amounts);
    }

    public static Totals empty() {
      return new Totals(0, 0, 0, List.of());
    }
  }

  /** Lo confirmado, lo pendiente y lo anulado. */
  record Summary(Totals confirmed, Totals pending, Totals voided) {
    public static Summary empty() {
      return new Summary(Totals.empty(), Totals.empty(), Totals.empty());
    }
  }
}
