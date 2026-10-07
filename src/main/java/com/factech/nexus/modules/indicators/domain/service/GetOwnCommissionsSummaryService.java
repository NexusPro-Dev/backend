package com.factech.nexus.modules.indicators.domain.service;

import com.factech.nexus.modules.commissions.application.CommissionBatchFigures;
import com.factech.nexus.modules.commissions.application.CommissionBatchFigures.CommissionFilter;
import com.factech.nexus.modules.commissions.application.CommissionBatchFigures.CommissionTotals;
import com.factech.nexus.modules.indicators.application.IndicatorAmount;
import com.factech.nexus.modules.indicators.application.IndicatorAmount.IndicatorCurrency;
import com.factech.nexus.modules.indicators.application.IndicatorPeriod;
import com.factech.nexus.modules.indicators.application.OwnCommissionsSummaryResponse;
import com.factech.nexus.modules.indicators.application.OwnCommissionsSummaryResponse.Block;
import com.factech.nexus.modules.movements.application.SalesFigures.Interval;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import java.time.LocalDate;
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
 * El resumen de mis comisiones (`RF-IN-008`).
 *
 * <p><b>Solo lo propio</b> (`RN-IN-013`): la persona es la del token, y no pasa por {@link
 * SalesScopeResolver} —no hay red que sumar: cada nivel de la cadena tiene su propia comisión—.
 * <b>El periodo se resuelve como en los demás indicadores</b> y elige las comisiones por su
 * nacimiento; <b>el estado es el de su lote hoy</b> (`RN-IN-012`).
 *
 * <p>El reparto es el de {@link GetCommissionBatchesSummaryService}: los tres estados siempre, y
 * uno que `IN` no conozca va solo al total y al log.
 */
@Service
public class GetOwnCommissionsSummaryService {

  private static final Logger LOG = LoggerFactory.getLogger(GetOwnCommissionsSummaryService.class);

  private final CommissionBatchFigures cifras;
  private final SalesPeriodResolver periodos;
  private final AuthenticatedActor actor;

  public GetOwnCommissionsSummaryService(
      CommissionBatchFigures cifras, SalesPeriodResolver periodos, AuthenticatedActor actor) {
    this.cifras = cifras;
    this.periodos = periodos;
    this.actor = actor;
  }

  @Transactional(readOnly = true)
  public OwnCommissionsSummaryResponse get(
      LocalDate from, LocalDate to, UUID currencyId, UUID clientId) {
    List<FieldError> problemas = new ArrayList<>();
    IndicatorPeriod periodo = periodos.resolve(from, to, problemas);
    if (!problemas.isEmpty()) {
      throw new ValidationException(
          problemas.get(0).code(), "La consulta solicitada no es válida.", problemas);
    }
    Interval intervalo = periodos.interval(periodo);

    List<CommissionTotals> abiertas = new ArrayList<>();
    List<CommissionTotals> pendientes = new ArrayList<>();
    List<CommissionTotals> pagadas = new ArrayList<>();
    List<CommissionTotals> todas =
        cifras.commissionsByStatus(
            new CommissionFilter(
                actor.id(), currencyId, clientId, intervalo.from(), intervalo.to()));
    for (CommissionTotals fila : todas) {
      switch (fila.status()) {
        case "ABIERTO" -> abiertas.add(fila);
        case "PENDIENTE" -> pendientes.add(fila);
        case "PAGADO" -> pagadas.add(fila);
        default ->
            LOG.warn(
                "Estado de lote desconocido para el indicador: {} ({} comisiones); cuenta solo en"
                    + " el total",
                fila.status(),
                fila.commissions());
      }
    }
    return new OwnCommissionsSummaryResponse(
        periodo, bloque(abiertas), bloque(pendientes), bloque(pagadas), bloque(todas));
  }

  /** Cuenta las comisiones y suma el valor de cada moneda, ordenadas por código. */
  static Block bloque(List<CommissionTotals> filas) {
    long comisiones = 0;
    Map<UUID, IndicatorAmount> porMoneda = new LinkedHashMap<>();
    for (CommissionTotals f : filas) {
      comisiones += f.commissions();
      porMoneda.merge(
          f.currencyId(),
          new IndicatorAmount(new IndicatorCurrency(f.currencyId(), f.currencyCode()), f.amount()),
          (a, b) -> new IndicatorAmount(a.currency(), a.amount().add(b.amount())));
    }
    return new Block(
        comisiones,
        porMoneda.values().stream()
            .sorted(Comparator.comparing(i -> i.currency().code()))
            .toList());
  }
}
