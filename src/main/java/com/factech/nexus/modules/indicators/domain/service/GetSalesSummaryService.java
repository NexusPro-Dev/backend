package com.factech.nexus.modules.indicators.domain.service;

import com.factech.nexus.modules.indicators.application.IndicatorAmount;
import com.factech.nexus.modules.indicators.application.IndicatorAmount.IndicatorCurrency;
import com.factech.nexus.modules.indicators.application.IndicatorPeriod;
import com.factech.nexus.modules.indicators.application.SalesIndicatorRequest;
import com.factech.nexus.modules.indicators.application.SalesSummaryResponse;
import com.factech.nexus.modules.movements.application.SalesFigures;
import com.factech.nexus.modules.movements.application.SalesFigures.Amount;
import com.factech.nexus.modules.movements.application.SalesFigures.BucketSummary;
import com.factech.nexus.modules.movements.application.SalesFigures.Granularity;
import com.factech.nexus.modules.movements.application.SalesFigures.SalesScope;
import com.factech.nexus.modules.movements.application.SalesFigures.Summary;
import com.factech.nexus.modules.movements.application.SalesFigures.Totals;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El resumen de ventas (`RF-IN-001`).
 *
 * <p><b>La suma la hace `MV`</b> por {@link SalesFigures}: `IN` decide qué se pide, para quién y
 * con qué periodo, y no lee ninguna tabla (`requirements/in.md` §1.4). Fuera del alcance responde
 * ceros sin preguntar ({@link SalesScopeResolver}).
 *
 * <p><b>Con tramo</b> (`RN-IN-010`), una segunda lectura da los mismos bloques por tramo, y este
 * servicio los cruza con el calendario completo: todos los tramos presentes, con ceros.
 */
@Service
public class GetSalesSummaryService {

  private final SalesFigures cifras;
  private final SalesPeriodResolver periodos;
  private final SalesScopeResolver alcances;
  private final AuthenticatedActor actor;

  public GetSalesSummaryService(
      SalesFigures cifras,
      SalesPeriodResolver periodos,
      SalesScopeResolver alcances,
      AuthenticatedActor actor) {
    this.cifras = cifras;
    this.periodos = periodos;
    this.alcances = alcances;
    this.actor = actor;
  }

  /**
   * @param granularity {@code DAY}, {@code WEEK} o {@code MONTH}, sin distinguir mayúsculas; nulo
   *     es sin tramos
   */
  @Transactional(readOnly = true)
  public SalesSummaryResponse get(SalesIndicatorRequest peticion, String granularity) {
    List<FieldError> problemas = new ArrayList<>();
    IndicatorPeriod periodo = periodos.resolve(peticion.from(), peticion.to(), problemas);
    Granularity tramo = SalesPeriodResolver.granularity(granularity, null, problemas);
    if (!problemas.isEmpty()) {
      throw new ValidationException(
          problemas.get(0).code(), "La consulta solicitada no es válida.", problemas);
    }

    Optional<SalesScope> alcance = alcances.resolve(actor.id(), peticion.sellerId());
    Summary resumen =
        alcance
            .map(a -> cifras.summary(a, periodos.interval(periodo), peticion.currencyId()))
            .orElseGet(Summary::empty);
    Bloques total = bloques(resumen);

    List<SalesSummaryResponse.Bucket> tramos = null;
    if (tramo != null) {
      List<BucketSummary> conVentas =
          alcance
              .map(
                  a ->
                      cifras.summaryByBucket(
                          a,
                          periodos.interval(periodo),
                          peticion.currencyId(),
                          tramo,
                          ZoneId.of(periodo.zone())))
              .orElseGet(List::of);
      Map<LocalDate, Summary> porTramo = new HashMap<>();
      conVentas.forEach(b -> porTramo.put(b.start(), b.summary()));
      tramos = new ArrayList<>();
      for (LocalDate inicio : SalesPeriodResolver.starts(periodo, tramo, porTramo.keySet())) {
        Bloques b = bloques(porTramo.getOrDefault(inicio, Summary.empty()));
        tramos.add(
            new SalesSummaryResponse.Bucket(
                inicio, b.total(), b.confirmed(), b.pending(), b.voided()));
      }
    }

    return new SalesSummaryResponse(
        periodo,
        total.total(),
        total.confirmed(),
        total.pending(),
        total.voided(),
        tramo == null ? null : tramo.name(),
        tramos);
  }

  /** Los cuatro bloques de un resumen, del periodo o de un tramo. */
  private record Bloques(
      SalesSummaryResponse.Total total,
      SalesSummaryResponse.Confirmed confirmed,
      SalesSummaryResponse.Other pending,
      SalesSummaryResponse.Other voided) {}

  private static Bloques bloques(Summary resumen) {
    Totals confirmadas = resumen.confirmed();
    Totals pendientes = resumen.pending();
    Totals anuladas = resumen.voided();
    // Los tres estados no se solapan: cada venta está en uno solo, y el total es
    // su suma (`CA-IN-038`).
    return new Bloques(
        new SalesSummaryResponse.Total(
            confirmadas.sales() + pendientes.sales() + anuladas.sales(),
            confirmadas.free() + pendientes.free() + anuladas.free()),
        new SalesSummaryResponse.Confirmed(
            confirmadas.sales(),
            confirmadas.lines(),
            confirmadas.units(),
            confirmadas.free(),
            importes(confirmadas.amounts())),
        new SalesSummaryResponse.Other(
            pendientes.sales(), pendientes.free(), importes(pendientes.amounts())),
        new SalesSummaryResponse.Other(
            anuladas.sales(), anuladas.free(), importes(anuladas.amounts())));
  }

  static List<IndicatorAmount> importes(List<Amount> importes) {
    return importes.stream()
        .map(
            i ->
                new IndicatorAmount(
                    new IndicatorCurrency(i.currencyId(), i.currencyCode()), i.amount()))
        .toList();
  }
}
