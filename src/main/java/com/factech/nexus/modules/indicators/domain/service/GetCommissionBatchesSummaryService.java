package com.factech.nexus.modules.indicators.domain.service;

import com.factech.nexus.modules.commissions.application.CommissionBatchFigures;
import com.factech.nexus.modules.commissions.application.CommissionBatchFigures.StatusTotals;
import com.factech.nexus.modules.indicators.application.CommissionBatchesSummaryResponse;
import com.factech.nexus.modules.indicators.application.CommissionBatchesSummaryResponse.Block;
import com.factech.nexus.modules.indicators.application.IndicatorAmount;
import com.factech.nexus.modules.indicators.application.IndicatorAmount.IndicatorCurrency;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El resumen de lotes de comisiones (`RF-IN-007`).
 *
 * <p><b>Sin alcance</b> (`RN-IN-011`) <b>y sin periodo</b> (`RN-IN-012`): una sola lectura de
 * {@link CommissionBatchFigures}, que este servicio reparte en los tres estados —siempre presentes,
 * en cero si no tienen lotes— y suma en el total por moneda. Los tres estados y el total salen de
 * la misma sentencia, de modo que un cierre a la vez no puede dejar un lote contado en dos.
 *
 * <p><b>Un estado que `IN` no conoce</b> —uno que `CM` añadiera— <b>no se pierde</b>: cuenta en el
 * total y queda en el log (`plan.md` §3).
 */
@Service
public class GetCommissionBatchesSummaryService {

  private static final Logger LOG =
      LoggerFactory.getLogger(GetCommissionBatchesSummaryService.class);

  private final CommissionBatchFigures cifras;

  public GetCommissionBatchesSummaryService(CommissionBatchFigures cifras) {
    this.cifras = cifras;
  }

  @Transactional(readOnly = true)
  public CommissionBatchesSummaryResponse get(UUID currencyId) {
    List<StatusTotals> abiertos = new ArrayList<>();
    List<StatusTotals> pendientes = new ArrayList<>();
    List<StatusTotals> pagados = new ArrayList<>();
    List<StatusTotals> todos = cifras.byStatus(currencyId);
    for (StatusTotals fila : todos) {
      switch (fila.status()) {
        case "ABIERTO" -> abiertos.add(fila);
        case "PENDIENTE" -> pendientes.add(fila);
        case "PAGADO" -> pagados.add(fila);
        default ->
            LOG.warn(
                "Estado de lote desconocido para el indicador: {} ({} lotes); cuenta solo en el"
                    + " total",
                fila.status(),
                fila.batches());
      }
    }
    return new CommissionBatchesSummaryResponse(
        bloque(abiertos), bloque(pendientes), bloque(pagados), bloque(todos));
  }

  /** Cuenta los lotes y suma el valor de cada moneda, ordenadas por código. */
  static Block bloque(List<StatusTotals> filas) {
    long lotes = 0;
    Map<UUID, IndicatorAmount> porMoneda = new LinkedHashMap<>();
    for (StatusTotals f : filas) {
      lotes += f.batches();
      porMoneda.merge(
          f.currencyId(),
          new IndicatorAmount(new IndicatorCurrency(f.currencyId(), f.currencyCode()), f.amount()),
          (a, b) -> new IndicatorAmount(a.currency(), a.amount().add(b.amount())));
    }
    return new Block(
        lotes,
        porMoneda.values().stream()
            .sorted(Comparator.comparing(i -> i.currency().code()))
            .toList());
  }
}
