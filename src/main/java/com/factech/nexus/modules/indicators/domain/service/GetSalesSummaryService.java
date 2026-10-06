package com.factech.nexus.modules.indicators.domain.service;

import com.factech.nexus.modules.indicators.application.IndicatorAmount;
import com.factech.nexus.modules.indicators.application.IndicatorAmount.IndicatorCurrency;
import com.factech.nexus.modules.indicators.application.IndicatorPeriod;
import com.factech.nexus.modules.indicators.application.SalesIndicatorRequest;
import com.factech.nexus.modules.indicators.application.SalesSummaryResponse;
import com.factech.nexus.modules.movements.application.SalesFigures;
import com.factech.nexus.modules.movements.application.SalesFigures.Amount;
import com.factech.nexus.modules.movements.application.SalesFigures.SalesScope;
import com.factech.nexus.modules.movements.application.SalesFigures.Summary;
import com.factech.nexus.modules.movements.application.SalesFigures.Totals;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El resumen de ventas (`RF-IN-001`).
 *
 * <p><b>La suma la hace `MV`</b> por {@link SalesFigures}: `IN` decide qué se pide, para quién y
 * con qué periodo, y no lee ninguna tabla (`requirements/in.md` §1.4). Fuera del alcance responde
 * ceros sin preguntar ({@link SalesScopeResolver}).
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

  @Transactional(readOnly = true)
  public SalesSummaryResponse get(SalesIndicatorRequest peticion) {
    List<FieldError> problemas = new ArrayList<>();
    IndicatorPeriod periodo = periodos.resolve(peticion.from(), peticion.to(), problemas);
    if (!problemas.isEmpty()) {
      throw new ValidationException(
          problemas.get(0).code(), "La consulta solicitada no es válida.", problemas);
    }

    Optional<SalesScope> alcance = alcances.resolve(actor.id(), peticion.sellerId());
    Summary resumen =
        alcance
            .map(a -> cifras.summary(a, periodos.interval(periodo), peticion.currencyId()))
            .orElseGet(Summary::empty);

    Totals confirmadas = resumen.confirmed();
    Totals pendientes = resumen.pending();
    Totals anuladas = resumen.voided();
    // Los tres estados no se solapan: cada venta está en uno solo, y el total es
    // su suma (`CA-IN-038`).
    return new SalesSummaryResponse(
        periodo,
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
