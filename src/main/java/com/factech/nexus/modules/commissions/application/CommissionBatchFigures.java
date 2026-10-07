package com.factech.nexus.modules.commissions.application;

import java.math.BigDecimal;
import java.util.List;
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
   * <p><b>No recibe alcance ni intervalo</b>, a propósito: el indicador es de administración y una
   * foto de hoy (`RN-IN-011`, `RN-IN-012`), y que la firma no los admita impide aplicarlos por
   * descuido.
   *
   * @param currencyId si no es nulo, solo los lotes de esa moneda
   */
  List<StatusTotals> byStatus(UUID currencyId);

  /**
   * Los lotes de un estado en una moneda.
   *
   * @param status el código del estado —{@code ABIERTO}, {@code PENDIENTE}, {@code PAGADO}—, como
   *     texto: el {@code enum} es del dominio de `CM`
   * @param amount la suma de {@code total_amount}, en decimales
   */
  record StatusTotals(
      String status, UUID currencyId, String currencyCode, long batches, BigDecimal amount) {}
}
