package com.factech.nexus.modules.indicators.domain.service;

import com.factech.nexus.modules.indicators.application.IndicatorAmount.IndicatorCurrency;
import com.factech.nexus.modules.indicators.application.IndicatorPeriod;
import com.factech.nexus.modules.indicators.application.PointsSummaryResponse;
import com.factech.nexus.modules.indicators.application.SalesIndicatorRequest;
import com.factech.nexus.modules.movements.application.PointsFigures;
import com.factech.nexus.modules.movements.application.PointsFigures.Balance;
import com.factech.nexus.modules.movements.application.PointsFigures.BucketFlow;
import com.factech.nexus.modules.movements.application.PointsFigures.Flow;
import com.factech.nexus.modules.movements.application.SalesFigures.Granularity;
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
 * <p>Las lecturas van en la misma transacción para que describan el mismo instante: es lo que deja
 * cuadrar el saldo con la suma de las clases (`CA-IN-044`). <b>Con tramo</b> (`RN-IN-010`), cada
 * tramo lleva las mismas monedas que el total, en su orden, con ceros donde no hubo movimiento; el
 * saldo no se parte.
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

  /** Los tipos de la lista de los movimientos de puntos (`RF-MV-056`). */
  private static final List<String> TIPOS =
      List.of("COMPRA_PUNTOS", "AJUSTE_PUNTOS", "GASTO_PUNTOS");

  /** Los estados de la lista. */
  private static final List<String> ESTADOS = List.of("PENDIENTE", "CONFIRMADA", "RECHAZADA");

  /**
   * @param peticion el periodo, la moneda y, en {@code sellerId}, la persona titular por la que se
   *     acota
   * @param type uno de {@link #TIPOS}, o nulo
   * @param status uno de {@link #ESTADOS}, o nulo
   * @param granularity {@code DAY}, {@code WEEK} o {@code MONTH}; nulo es sin tramos
   */
  @Transactional(readOnly = true)
  public PointsSummaryResponse get(
      SalesIndicatorRequest peticion, String type, String status, String granularity) {
    List<FieldError> problemas = new ArrayList<>();
    IndicatorPeriod periodo = periodos.resolve(peticion.from(), peticion.to(), problemas);
    Granularity tramo = SalesPeriodResolver.granularity(granularity, null, problemas);
    String tipo = deLaLista(type, TIPOS, "type", "VAL-006", "El tipo", problemas);
    String estado = deLaLista(status, ESTADOS, "status", "VAL-007", "El estado", problemas);
    if (!problemas.isEmpty()) {
      throw new ValidationException(
          problemas.get(0).code(), "La consulta solicitada no es válida.", problemas);
    }
    String nombreDelTramo = tramo == null ? null : tramo.name();

    Optional<SalesScope> alcance = alcances.resolve(actor.id(), peticion.sellerId());
    if (alcance.isEmpty()) {
      List<PointsSummaryResponse.Bucket> vacios =
          tramo == null
              ? null
              : SalesPeriodResolver.starts(periodo, tramo, List.of()).stream()
                  .map(inicio -> new PointsSummaryResponse.Bucket(inicio, List.of()))
                  .toList();
      return new PointsSummaryResponse(periodo, List.of(), nombreDelTramo, vacios);
    }
    Set<UUID> titulares = alcance.get().isEverything() ? null : alcance.get().sellers();
    List<Flow> flujos =
        cifras.flows(titulares, periodos.interval(periodo), peticion.currencyId(), tipo, estado);
    // El saldo es el de hoy: no lo acotan ni el periodo, ni el tipo, ni el estado (`CA-IN-071`).
    List<Balance> saldos = cifras.balances(titulares, peticion.currencyId());

    // Una moneda aparece si hay algo que contar: un movimiento en el periodo o
    // un saldo distinto de cero hoy.
    Map<UUID, String> codigos = new HashMap<>();
    Map<UUID, Cifras> porMoneda = new HashMap<>();
    for (Flow f : flujos) {
      codigos.put(f.currencyId(), f.currencyCode());
      porMoneda.computeIfAbsent(f.currencyId(), k -> new Cifras()).sumar(f);
    }
    Map<UUID, BigDecimal> saldoDe = new HashMap<>();
    for (Balance s : saldos) {
      saldoDe.put(s.currencyId(), s.points());
      if (s.points().signum() != 0) {
        codigos.put(s.currencyId(), s.currencyCode());
      }
    }
    List<IndicatorCurrency> ordenadas =
        codigos.entrySet().stream()
            .map(e -> new IndicatorCurrency(e.getKey(), e.getValue()))
            .sorted(Comparator.comparing(IndicatorCurrency::code))
            .toList();

    List<PointsSummaryResponse.Currency> monedas = new ArrayList<>();
    for (IndicatorCurrency m : ordenadas) {
      Cifras c = porMoneda.getOrDefault(m.id(), new Cifras());
      monedas.add(
          new PointsSummaryResponse.Currency(
              m, c.compras(), c.gastos(), c.ajustes(), saldoDe.getOrDefault(m.id(), CERO)));
    }

    List<PointsSummaryResponse.Bucket> tramos = null;
    if (tramo != null) {
      Map<LocalDate, Map<UUID, Cifras>> porTramo = new HashMap<>();
      for (BucketFlow b :
          cifras.flowsByBucket(
              titulares,
              periodos.interval(periodo),
              peticion.currencyId(),
              tipo,
              estado,
              tramo,
              ZoneId.of(periodo.zone()))) {
        porTramo
            .computeIfAbsent(b.start(), k -> new HashMap<>())
            .computeIfAbsent(b.flow().currencyId(), k -> new Cifras())
            .sumar(b.flow());
      }
      tramos = new ArrayList<>();
      for (LocalDate inicio : SalesPeriodResolver.starts(periodo, tramo, porTramo.keySet())) {
        Map<UUID, Cifras> deEste = porTramo.getOrDefault(inicio, Map.of());
        List<PointsSummaryResponse.BucketCurrency> deCadaMoneda = new ArrayList<>();
        for (IndicatorCurrency m : ordenadas) {
          Cifras c = deEste.getOrDefault(m.id(), new Cifras());
          deCadaMoneda.add(
              new PointsSummaryResponse.BucketCurrency(m, c.compras(), c.gastos(), c.ajustes()));
        }
        tramos.add(new PointsSummaryResponse.Bucket(inicio, deCadaMoneda));
      }
    }
    return new PointsSummaryResponse(periodo, monedas, nombreDelTramo, tramos);
  }

  /** Un valor de una lista cerrada, sin distinguir mayúsculas; nulo si no viene. */
  private static String deLaLista(
      String valor,
      List<String> admitidos,
      String campo,
      String codigo,
      String nombre,
      List<FieldError> problemas) {
    if (valor == null || valor.isBlank()) {
      return null;
    }
    String normalizado = valor.trim().toUpperCase();
    if (admitidos.contains(normalizado)) {
      return normalizado;
    }
    problemas.add(
        new FieldError(
            campo,
            codigo,
            nombre + " '" + valor + "' no existe. Valores admitidos: " + admitidos + "."));
    return null;
  }

  /** Lo de una moneda, en un periodo o un tramo. */
  private static final class Cifras {
    private final Map<String, PointsSummaryResponse.Purchase> compras = new HashMap<>();
    private PointsSummaryResponse.Flow gastado = vacio();
    private PointsSummaryResponse.Flow sumado = vacio();
    private PointsSummaryResponse.Flow restado = vacio();

    void sumar(Flow f) {
      PointsSummaryResponse.Flow flujo = new PointsSummaryResponse.Flow(f.count(), f.points());
      switch (f.kind()) {
        case PURCHASE ->
            compras.merge(
                f.status(),
                new PointsSummaryResponse.Purchase(f.count(), f.points(), f.amount()),
                (a, b) ->
                    new PointsSummaryResponse.Purchase(
                        a.count() + b.count(),
                        a.points().add(b.points()),
                        a.amount().add(b.amount())));
        case SPENT -> gastado = mas(gastado, flujo);
        case ADDED -> sumado = mas(sumado, flujo);
        case REMOVED -> restado = mas(restado, flujo);
      }
    }

    PointsSummaryResponse.Purchases compras() {
      PointsSummaryResponse.Purchase ninguna = new PointsSummaryResponse.Purchase(0, CERO, CERO);
      return new PointsSummaryResponse.Purchases(
          compras.getOrDefault("CONFIRMADA", ninguna),
          compras.getOrDefault("PENDIENTE", ninguna),
          compras.getOrDefault("RECHAZADA", ninguna));
    }

    PointsSummaryResponse.Flow gastos() {
      return gastado;
    }

    PointsSummaryResponse.Adjustments ajustes() {
      return new PointsSummaryResponse.Adjustments(sumado, restado);
    }

    private static PointsSummaryResponse.Flow vacio() {
      return new PointsSummaryResponse.Flow(0, CERO);
    }

    private static PointsSummaryResponse.Flow mas(
        PointsSummaryResponse.Flow a, PointsSummaryResponse.Flow b) {
      return new PointsSummaryResponse.Flow(a.count() + b.count(), a.points().add(b.points()));
    }
  }
}
