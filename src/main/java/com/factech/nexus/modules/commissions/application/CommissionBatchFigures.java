package com.factech.nexus.modules.commissions.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * <b>Las cifras de los lotes de comisiones</b>, publicadas para `IN` (`RF-IN-007` · `T-02`; D-25).
 *
 * <p>Es la forma de {@code SalesFigures} trasladada al dueño de los lotes: `CM` sabe qué es un
 * lote, en qué estado está y cuánto vale, y <b>no sabe qué es un indicador</b>. Devuelve sumas,
 * nunca filas.
 */
public interface CommissionBatchFigures {

  /**
   * Los lotes <b>como están ahora</b>, por estado y moneda: cuántos son y la suma de su total. Una
   * fila por estado y moneda con lotes; los vacíos no vienen.
   *
   * <p><b>No recibe alcance</b>, a propósito: el indicador es de administración (`RN-IN-011`), y
   * que la firma no lo admita impide aplicarlo por descuido. El filtro lo elige quien pregunta.
   */
  List<StatusTotals> byStatus(BatchFilter filter);

  /**
   * Qué lotes se cuentan (`RF-IN-007` 0.2.0). Todo es opcional: nulo es sin filtro. <b>El estado es
   * siempre el de hoy</b> (`RN-IN-012`): las fechas eligen lotes, no reconstruyen el pasado.
   *
   * @param currencyId solo los lotes de esa moneda
   * @param userId solo los lotes de esa persona
   * @param from solo los lotes cuyo periodo no había terminado en este instante
   * @param to solo los lotes cuyo periodo empezó antes de este instante, excluido
   */
  record BatchFilter(UUID currencyId, UUID userId, OffsetDateTime from, OffsetDateTime to) {

    /** Todos los lotes. */
    public static BatchFilter none() {
      return new BatchFilter(null, null, null, null);
    }
  }

  /**
   * Los lotes de un estado en una moneda.
   *
   * @param status el código del estado —{@code ABIERTO}, {@code PENDIENTE}, {@code PAGADO}—, como
   *     texto: el {@code enum} es del dominio de `CM`
   * @param amount la suma de {@code total_amount}, en decimales
   */
  record StatusTotals(
      String status, UUID currencyId, String currencyCode, long batches, BigDecimal amount) {}

  /**
   * Las comisiones <b>de una persona</b>, por el estado del lote donde están hoy y su moneda —la
   * del lote—: cuántas son y la suma de su importe (`RF-IN-008`). Una fila por estado y moneda con
   * comisiones; los vacíos no vienen.
   */
  List<CommissionTotals> commissionsByStatus(CommissionFilter filter);

  /**
   * Qué comisiones se cuentan (`RF-IN-008`). <b>La persona es obligatoria</b>: una lectura sin ella
   * contaría las de todos. Lo demás es opcional.
   *
   * @param userId la persona dueña de las comisiones
   * @param currencyId solo las de lotes de esa moneda
   * @param from solo las nacidas ({@code accrued_at}) desde este instante, incluido
   * @param to solo las nacidas antes de este instante, excluido
   */
  record CommissionFilter(UUID userId, UUID currencyId, OffsetDateTime from, OffsetDateTime to) {
    public CommissionFilter {
      Objects.requireNonNull(userId, "La persona es obligatoria");
    }
  }

  /**
   * Las comisiones de una persona en un estado de lote y una moneda.
   *
   * @param status el código del estado del lote, como texto
   * @param commissions cuántas comisiones
   * @param amount la suma de {@code commission_amount}, en decimales
   */
  record CommissionTotals(
      String status, UUID currencyId, String currencyCode, long commissions, BigDecimal amount) {}
}
