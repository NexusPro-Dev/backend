package com.factech.nexus.modules.indicators.domain.service;

import com.factech.nexus.modules.indicators.application.IndicatorPeriod;
import com.factech.nexus.modules.indicators.application.SaleLinesSummaryResponse;
import com.factech.nexus.modules.movements.application.SalesFigures;
import com.factech.nexus.modules.movements.application.SalesFigures.BucketSummary;
import com.factech.nexus.modules.movements.application.SalesFigures.BucketTotals;
import com.factech.nexus.modules.movements.application.SalesFigures.Granularity;
import com.factech.nexus.modules.movements.application.SalesFigures.Interval;
import com.factech.nexus.modules.movements.application.SalesFigures.SalesScope;
import com.factech.nexus.modules.movements.application.SalesFigures.Summary;
import com.factech.nexus.modules.movements.application.SalesFigures.Totals;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El resumen de líneas de venta (`RF-IN-006`).
 *
 * <p><b>Sin alcance</b> (`RN-IN-011`): no pasa por {@link SalesScopeResolver}, y la única puerta es
 * el permiso. Las cifras por estado son las de {@link SalesFigures#summary} con todo el libro —las
 * mismas que ve administración en el resumen de ventas (`CA-IN-061`)—, y lo sin vendedor es la
 * lectura propia de este indicador.
 */
@Service
public class GetSaleLinesSummaryService {

  private final SalesFigures cifras;
  private final SalesPeriodResolver periodos;

  public GetSaleLinesSummaryService(SalesFigures cifras, SalesPeriodResolver periodos) {
    this.cifras = cifras;
    this.periodos = periodos;
  }

  /**
   * @param granularity {@code DAY}, {@code WEEK} o {@code MONTH}; nulo es sin tramos
   */
  @Transactional(readOnly = true)
  public SaleLinesSummaryResponse get(
      LocalDate from, LocalDate to, UUID currencyId, String granularity) {
    List<FieldError> problemas = new ArrayList<>();
    IndicatorPeriod periodo = periodos.resolve(from, to, problemas);
    Granularity tramo = SalesPeriodResolver.granularity(granularity, null, problemas);
    if (!problemas.isEmpty()) {
      throw new ValidationException(
          problemas.get(0).code(), "La consulta solicitada no es válida.", problemas);
    }
    Interval intervalo = periodos.interval(periodo);

    Bloques total =
        bloques(
            cifras.summary(SalesScope.everything(), intervalo, currencyId),
            cifras.unassigned(intervalo, currencyId));

    List<SaleLinesSummaryResponse.Bucket> tramos = null;
    if (tramo != null) {
      ZoneId zona = ZoneId.of(periodo.zone());
      Map<LocalDate, Summary> resumenes = new HashMap<>();
      for (BucketSummary b :
          cifras.summaryByBucket(SalesScope.everything(), intervalo, currencyId, tramo, zona)) {
        resumenes.put(b.start(), b.summary());
      }
      Map<LocalDate, Totals> sinVendedor = new HashMap<>();
      for (BucketTotals b : cifras.unassignedByBucket(intervalo, currencyId, tramo, zona)) {
        sinVendedor.put(b.start(), b.totals());
      }
      Set<LocalDate> conDatos = new HashSet<>(resumenes.keySet());
      conDatos.addAll(sinVendedor.keySet());
      tramos = new ArrayList<>();
      for (LocalDate inicio : SalesPeriodResolver.starts(periodo, tramo, conDatos)) {
        Bloques b =
            bloques(
                resumenes.getOrDefault(inicio, Summary.empty()),
                sinVendedor.getOrDefault(inicio, Totals.empty()));
        tramos.add(
            new SaleLinesSummaryResponse.Bucket(
                inicio, b.total(), b.confirmed(), b.pending(), b.voided(), b.unassigned()));
      }
    }

    return new SaleLinesSummaryResponse(
        periodo,
        total.total(),
        total.confirmed(),
        total.pending(),
        total.voided(),
        total.unassigned(),
        tramo == null ? null : tramo.name(),
        tramos);
  }

  /** Los cinco bloques del periodo o de un tramo. */
  private record Bloques(
      SaleLinesSummaryResponse.Total total,
      SaleLinesSummaryResponse.Block confirmed,
      SaleLinesSummaryResponse.Block pending,
      SaleLinesSummaryResponse.Block voided,
      SaleLinesSummaryResponse.Block unassigned) {}

  private static Bloques bloques(Summary resumen, Totals sinVendedor) {
    Totals c = resumen.confirmed();
    Totals p = resumen.pending();
    Totals v = resumen.voided();
    // Los tres estados no se solapan: cada venta está en uno solo.
    return new Bloques(
        new SaleLinesSummaryResponse.Total(
            c.sales() + p.sales() + v.sales(),
            c.lines() + p.lines() + v.lines(),
            c.units() + p.units() + v.units()),
        bloque(c),
        bloque(p),
        bloque(v),
        bloque(sinVendedor));
  }

  private static SaleLinesSummaryResponse.Block bloque(Totals t) {
    return new SaleLinesSummaryResponse.Block(
        t.sales(), t.lines(), t.units(), GetSalesSummaryService.importes(t.amounts()));
  }
}
