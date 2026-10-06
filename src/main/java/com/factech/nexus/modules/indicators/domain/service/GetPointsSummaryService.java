package com.factech.nexus.modules.indicators.domain.service;

import com.factech.nexus.modules.indicators.application.IndicatorAmount.IndicatorCurrency;
import com.factech.nexus.modules.indicators.application.IndicatorPeriod;
import com.factech.nexus.modules.indicators.application.PointsSummaryResponse;
import com.factech.nexus.modules.indicators.application.SalesIndicatorRequest;
import com.factech.nexus.modules.movements.application.PointsFigures;
import com.factech.nexus.modules.movements.application.PointsFigures.Balance;
import com.factech.nexus.modules.movements.application.PointsFigures.Flow;
import com.factech.nexus.modules.movements.application.PointsFigures.Kind;
import com.factech.nexus.modules.movements.application.SalesFigures.SalesScope;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.persistence.MinorUnits;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El resumen de puntos (`RF-IN-005`).
 *
 * <p><b>El alcance es de titulares</b>: el mismo conjunto que en ventas —todo, o él y su red—, pero
 * aquí son las personas dueñas de los puntos y no las vendedoras de las líneas. Lo resuelve {@link
 * SalesScopeResolver}, que nació con las ventas y no se renombra por una segunda tanda. Fuera del
 * alcance, ceros sin preguntar.
 *
 * <p>Las dos lecturas van en la misma transacción para que describan el mismo instante: es lo que
 * deja cuadrar el saldo con la suma de las clases (`CA-IN-044`).
 */
@Service
public class GetPointsSummaryService {

  private static final BigDecimal CERO = MinorUnits.fromMinor(0L);

  private final PointsFigures cifras;
  private final SalesPeriodResolver periodos;
  private final SalesScopeResolver alcances;
  private final AuthenticatedActor actor;

  public GetPointsSummaryService(
      PointsFigures cifras,
      SalesPeriodResolver periodos,
      SalesScopeResolver alcances,
      AuthenticatedActor actor) {
    this.cifras = cifras;
    this.periodos = periodos;
    this.alcances = alcances;
    this.actor = actor;
  }

  /**
   * @param peticion el periodo, la moneda y, en {@code sellerId}, la persona titular por la que se
   *     acota
   */
  @Transactional(readOnly = true)
  public PointsSummaryResponse get(SalesIndicatorRequest peticion) {
    List<FieldError> problemas = new ArrayList<>();
    IndicatorPeriod periodo = periodos.resolve(peticion.from(), peticion.to(), problemas);
    if (!problemas.isEmpty()) {
      throw new ValidationException(
          problemas.get(0).code(), "La consulta solicitada no es válida.", problemas);
    }

    Optional<SalesScope> alcance = alcances.resolve(actor.id(), peticion.sellerId());
    if (alcance.isEmpty()) {
      return new PointsSummaryResponse(periodo, List.of());
    }
    Set<UUID> titulares = alcance.get().isEverything() ? null : alcance.get().sellers();
    List<Flow> flujos = cifras.flows(titulares, periodos.interval(periodo), peticion.currencyId());
    List<Balance> saldos = cifras.balances(titulares, peticion.currencyId());

    // Una moneda aparece si hay algo que contar: un movimiento en el periodo o
    // un saldo distinto de cero hoy.
    Map<UUID, String> codigos = new HashMap<>();
    Map<UUID, Map<Kind, Flow>> porMoneda = new HashMap<>();
    for (Flow f : flujos) {
      codigos.put(f.currencyId(), f.currencyCode());
      porMoneda.computeIfAbsent(f.currencyId(), k -> new EnumMap<>(Kind.class)).put(f.kind(), f);
    }
    Map<UUID, BigDecimal> saldoDe = new HashMap<>();
    for (Balance s : saldos) {
      saldoDe.put(s.currencyId(), s.points());
      if (s.points().signum() != 0) {
        codigos.put(s.currencyId(), s.currencyCode());
      }
    }

    List<PointsSummaryResponse.Currency> monedas = new ArrayList<>();
    codigos.entrySet().stream()
        .sorted(Map.Entry.comparingByValue(Comparator.naturalOrder()))
        .forEach(
            m -> {
              Map<Kind, Flow> deEsta = porMoneda.getOrDefault(m.getKey(), Map.of());
              monedas.add(
                  new PointsSummaryResponse.Currency(
                      new IndicatorCurrency(m.getKey(), m.getValue()),
                      flujo(deEsta.get(Kind.PURCHASED)),
                      flujo(deEsta.get(Kind.REDEEMED)),
                      flujo(deEsta.get(Kind.ADDED)),
                      flujo(deEsta.get(Kind.REMOVED)),
                      saldoDe.getOrDefault(m.getKey(), CERO)));
            });
    return new PointsSummaryResponse(periodo, monedas);
  }

  private static PointsSummaryResponse.Flow flujo(Flow f) {
    return f == null
        ? new PointsSummaryResponse.Flow(CERO, 0)
        : new PointsSummaryResponse.Flow(f.points(), f.count());
  }
}
