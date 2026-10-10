package com.factech.nexus.modules.indicators.domain.service;

import com.factech.nexus.modules.indicators.application.IndicatorAmount;
import com.factech.nexus.modules.indicators.application.IndicatorAmount.IndicatorCurrency;
import com.factech.nexus.modules.indicators.application.IndicatorPeriod;
import com.factech.nexus.modules.indicators.application.SalesIndicatorRequest;
import com.factech.nexus.modules.indicators.application.SalesSeriesResponse;
import com.factech.nexus.modules.movements.application.SalesFigures;
import com.factech.nexus.modules.movements.application.SalesFigures.Bucket;
import com.factech.nexus.modules.movements.application.SalesFigures.Granularity;
import com.factech.nexus.modules.movements.application.SalesFigures.LineFilter;
import com.factech.nexus.modules.movements.application.SalesFigures.SalesScope;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.persistence.MinorUnits;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La evolución de las ventas (`RF-IN-002`).
 *
 * <p><b>Agrupa la base; rellena `IN`</b> (`plan.md` §1): `MV` devuelve solo los tramos con ventas,
 * y este servicio los cruza con el calendario completo de {@link SalesCalendar} y completa cada
 * tramo con todas las monedas del periodo. Rellenar en SQL metería en `MV` una regla de
 * presentación que es de aquí. Fuera del alcance, el calendario se genera igual, con ceros.
 */
@Service
public class GetSalesSeriesService {

  private static final BigDecimal CERO = MinorUnits.fromMinor(0L);

  private final SalesFigures cifras;
  private final SalesPeriodResolver periodos;
  private final SalesScopeResolver alcances;
  private final AuthenticatedActor actor;

  public GetSalesSeriesService(
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
   *     es {@code DAY}
   */
  @Transactional(readOnly = true)
  public SalesSeriesResponse get(SalesIndicatorRequest peticion, String granularity) {
    // Los problemas juntos: el periodo y el tramo (`VAL-005`).
    List<FieldError> problemas = new ArrayList<>();
    IndicatorPeriod periodo = periodos.resolve(peticion.from(), peticion.to(), problemas);
    Granularity tramo = SalesPeriodResolver.granularity(granularity, Granularity.DAY, problemas);
    if (!problemas.isEmpty()) {
      throw new ValidationException(
          problemas.get(0).code(), "La consulta solicitada no es válida.", problemas);
    }

    Optional<SalesScope> alcance = alcances.resolve(actor.id(), peticion.sellerId());
    List<Bucket> conVentas =
        alcance
            .map(
                a ->
                    cifras.confirmedByBucket(
                        a,
                        periodos.interval(periodo),
                        peticion.currencyId(),
                        tramo,
                        ZoneId.of(periodo.zone()),
                        LineFilter.ofTeam(peticion.teamId())))
            .orElseGet(List::of);

    // Las monedas del periodo, por código: el orden de `amounts` en todos los tramos.
    Map<UUID, String> codigos = new HashMap<>();
    for (Bucket b : conVentas) {
      codigos.put(b.currencyId(), b.currencyCode());
    }
    List<IndicatorCurrency> ordenadas =
        codigos.entrySet().stream()
            .map(e -> new IndicatorCurrency(e.getKey(), e.getValue()))
            .sorted(Comparator.comparing(IndicatorCurrency::code))
            .toList();

    Map<LocalDate, List<Bucket>> porTramo = new HashMap<>();
    for (Bucket b : conVentas) {
      porTramo.computeIfAbsent(b.start(), k -> new ArrayList<>()).add(b);
    }

    List<SalesSeriesResponse.Bucket> tramos = new ArrayList<>();
    // Sin «desde», desde el tramo de la primera venta (`RN-IN-010`).
    for (LocalDate inicio : SalesPeriodResolver.starts(periodo, tramo, porTramo.keySet())) {
      List<Bucket> deEste = porTramo.getOrDefault(inicio, List.of());
      long ventas = 0;
      long lineas = 0;
      long unidades = 0;
      Map<UUID, BigDecimal> importe = new HashMap<>();
      for (Bucket b : deEste) {
        ventas += b.sales();
        lineas += b.lines();
        unidades += b.units();
        importe.put(b.currencyId(), b.amount());
      }
      List<IndicatorAmount> importes =
          ordenadas.stream()
              .map(m -> new IndicatorAmount(m, importe.getOrDefault(m.id(), CERO)))
              .toList();
      tramos.add(new SalesSeriesResponse.Bucket(inicio, ventas, lineas, unidades, importes));
    }
    return new SalesSeriesResponse(periodo, tramo.name(), ordenadas, tramos);
  }
}
