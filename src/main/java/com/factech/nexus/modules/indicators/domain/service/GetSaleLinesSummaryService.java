package com.factech.nexus.modules.indicators.domain.service;

import com.factech.nexus.modules.indicators.application.IndicatorPeriod;
import com.factech.nexus.modules.indicators.application.SaleLinesSummaryResponse;
import com.factech.nexus.modules.movements.application.SalesFigures;
import com.factech.nexus.modules.movements.application.SalesFigures.BucketSummary;
import com.factech.nexus.modules.movements.application.SalesFigures.BucketTotals;
import com.factech.nexus.modules.movements.application.SalesFigures.BucketTypeTotals;
import com.factech.nexus.modules.movements.application.SalesFigures.Granularity;
import com.factech.nexus.modules.movements.application.SalesFigures.Interval;
import com.factech.nexus.modules.movements.application.SalesFigures.LineFilter;
import com.factech.nexus.modules.movements.application.SalesFigures.Lines;
import com.factech.nexus.modules.movements.application.SalesFigures.SalesScope;
import com.factech.nexus.modules.movements.application.SalesFigures.Totals;
import com.factech.nexus.modules.movements.application.SalesFigures.TypeTotals;
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
 * El resumen de líneas de venta (`RF-IN-006`, enmendado el 07-10-2026).
 *
 * <p><b>Sin alcance</b> (`RN-IN-011`): no pasa por {@link SalesScopeResolver}, y la única puerta es
 * el permiso. <b>Lo vendido</b> es lo confirmado: su total es el de {@link SalesFigures#summary}
 * con todo el libro —el mismo que ve administración en el resumen de ventas (`CA-IN-061`)—. <b>Lo
 * sin vendedor</b> es {@link SalesFigures#unassigned}. Los dos, además, por tipo de producto.
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
   * @param filtro el vendedor, el cliente, el producto y el comprobante que estrechan las líneas
   *     (07-10-2026); no es alcance
   */
  @Transactional(readOnly = true)
  public SaleLinesSummaryResponse get(
      LocalDate from, LocalDate to, UUID currencyId, String granularity, LineFilter filtro) {
    List<FieldError> problemas = new ArrayList<>();
    IndicatorPeriod periodo = periodos.resolve(from, to, problemas);
    Granularity tramo = SalesPeriodResolver.granularity(granularity, null, problemas);
    if (!problemas.isEmpty()) {
      throw new ValidationException(
          problemas.get(0).code(), "La consulta solicitada no es válida.", problemas);
    }
    Interval intervalo = periodos.interval(periodo);

    SaleLinesSummaryResponse.Group vendido =
        grupo(
            cifras.summary(SalesScope.everything(), intervalo, currencyId, filtro).confirmed(),
            cifras.byProductType(Lines.SOLD, intervalo, currencyId, filtro));
    SaleLinesSummaryResponse.Group sinVendedor =
        grupo(
            cifras.unassigned(intervalo, currencyId, filtro),
            cifras.byProductType(Lines.UNASSIGNED, intervalo, currencyId, filtro));

    List<SaleLinesSummaryResponse.Bucket> tramos = null;
    if (tramo != null) {
      ZoneId zona = ZoneId.of(periodo.zone());
      Map<LocalDate, Totals> vendidoPorTramo = new HashMap<>();
      for (BucketSummary b :
          cifras.summaryByBucket(
              SalesScope.everything(), intervalo, currencyId, tramo, zona, filtro)) {
        vendidoPorTramo.put(b.start(), b.summary().confirmed());
      }
      Map<LocalDate, Totals> sinVendedorPorTramo = new HashMap<>();
      for (BucketTotals b : cifras.unassignedByBucket(intervalo, currencyId, tramo, zona, filtro)) {
        sinVendedorPorTramo.put(b.start(), b.totals());
      }
      Map<LocalDate, List<TypeTotals>> vendidoPorTipo =
          porTramo(
              cifras.byProductTypeAndBucket(
                  Lines.SOLD, intervalo, currencyId, tramo, zona, filtro));
      Map<LocalDate, List<TypeTotals>> sinVendedorPorTipo =
          porTramo(
              cifras.byProductTypeAndBucket(
                  Lines.UNASSIGNED, intervalo, currencyId, tramo, zona, filtro));

      Set<LocalDate> conDatos = new HashSet<>();
      // Un tramo con solo pendientes o anuladas no tiene nada que contar aquí.
      vendidoPorTramo.forEach(
          (inicio, t) -> {
            if (t.lines() > 0) {
              conDatos.add(inicio);
            }
          });
      conDatos.addAll(sinVendedorPorTramo.keySet());
      tramos = new ArrayList<>();
      for (LocalDate inicio : SalesPeriodResolver.starts(periodo, tramo, conDatos)) {
        tramos.add(
            new SaleLinesSummaryResponse.Bucket(
                inicio,
                grupo(
                    vendidoPorTramo.getOrDefault(inicio, Totals.empty()),
                    vendidoPorTipo.getOrDefault(inicio, List.of())),
                grupo(
                    sinVendedorPorTramo.getOrDefault(inicio, Totals.empty()),
                    sinVendedorPorTipo.getOrDefault(inicio, List.of()))));
      }
    }

    return new SaleLinesSummaryResponse(
        periodo, vendido, sinVendedor, tramo == null ? null : tramo.name(), tramos);
  }

  private static Map<LocalDate, List<TypeTotals>> porTramo(List<BucketTypeTotals> filas) {
    Map<LocalDate, List<TypeTotals>> porTramo = new HashMap<>();
    for (BucketTypeTotals b : filas) {
      porTramo
          .computeIfAbsent(b.start(), k -> new ArrayList<>())
          .add(new TypeTotals(b.productType(), b.totals()));
    }
    return porTramo;
  }

  private static SaleLinesSummaryResponse.Group grupo(Totals total, List<TypeTotals> porTipo) {
    return new SaleLinesSummaryResponse.Group(
        new SaleLinesSummaryResponse.Block(
            total.sales(),
            total.lines(),
            total.units(),
            GetSalesSummaryService.importes(total.amounts())),
        porTipo.stream()
            .map(
                t ->
                    new SaleLinesSummaryResponse.TypeBlock(
                        t.productType(),
                        t.totals().sales(),
                        t.totals().lines(),
                        t.totals().units(),
                        GetSalesSummaryService.importes(t.totals().amounts())))
            .toList());
  }
}
