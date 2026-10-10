package com.factech.nexus.modules.indicators.domain.service;

import com.factech.nexus.modules.indicators.application.BrokerNetworkIndicatorsResponse;
import com.factech.nexus.modules.indicators.application.BrokerNetworkIndicatorsResponse.BrokerRef;
import com.factech.nexus.modules.indicators.application.BrokerNetworkIndicatorsResponse.Bucket;
import com.factech.nexus.modules.indicators.application.BrokerNetworkIndicatorsResponse.ByBroker;
import com.factech.nexus.modules.indicators.application.BrokerNetworkIndicatorsResponse.Figures;
import com.factech.nexus.modules.indicators.application.BrokerNetworkIndicatorsResponse.Node;
import com.factech.nexus.modules.indicators.application.BrokerNetworkIndicatorsResponse.User;
import com.factech.nexus.modules.indicators.application.IndicatorPeriod;
import com.factech.nexus.modules.movements.application.SalesFigures.Granularity;
import com.factech.nexus.modules.movements.application.SalesFigures.Interval;
import com.factech.nexus.modules.system.brokers.application.BrokerAccountFigures;
import com.factech.nexus.modules.system.brokers.application.BrokerAccountFigures.Broker;
import com.factech.nexus.modules.system.brokers.application.BrokerAccountFigures.BucketFigures;
import com.factech.nexus.modules.system.brokers.application.BrokerAccountFigures.Seller;
import com.factech.nexus.modules.system.brokers.application.BrokerAccountFigures.SellerBrokerFigures;
import com.factech.nexus.modules.system.brokers.application.BrokerAccountFigures.SellerConsumers;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.users.application.CommercialReach;
import com.factech.nexus.modules.system.users.application.CommercialReach.Reach;
import com.factech.nexus.shared.error.FieldError;
import com.factech.nexus.shared.error.ValidationException;
import com.factech.nexus.shared.time.BusinessCalendar;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los indicadores de cuentas de broker de la red (`RF-IN-009`, `RN-IN-015`).
 *
 * <p><b>El recorrido es el de `RF-SP-058`, mudado</b>: la fuerza comercial con su superior vigente,
 * la raíz de cada rama —sin superior, o con un superior que no es fuerza comercial—, y un
 * post-orden que arma cada padre con lo que ya traen sus hijos. <b>Lo que cambió es de dónde salen
 * los números</b>: del dueño del {@code afftrack} de cada cuenta y no del principal de su titular,
 * y cada cifra con su fecha.
 *
 * <p><b>El alcance</b> es el de `RN-IN-002`: el alcance entero ve el bosque y lo no atribuido; un
 * vendedor, su rama; {@code sellerId} enraíza el árbol en otro vendedor del alcance, y fuera de él
 * la respuesta va vacía.
 */
@Service
public class GetBrokerNetworkIndicatorsService {

  private final BrokerAccountFigures cifras;
  private final SalesPeriodResolver periodos;
  private final CommercialReach alcance;
  private final AuthenticatedActor actor;
  private final BusinessCalendar calendario;

  public GetBrokerNetworkIndicatorsService(
      BrokerAccountFigures cifras,
      SalesPeriodResolver periodos,
      CommercialReach alcance,
      AuthenticatedActor actor,
      BusinessCalendar calendario) {
    this.cifras = cifras;
    this.periodos = periodos;
    this.alcance = alcance;
    this.actor = actor;
    this.calendario = calendario;
  }

  @Transactional(readOnly = true)
  public BrokerNetworkIndicatorsResponse get(
      LocalDate from, LocalDate to, UUID sellerId, String granularity) {
    List<FieldError> problemas = new ArrayList<>();
    IndicatorPeriod periodo = periodos.resolve(from, to, problemas);
    Granularity tramo = SalesPeriodResolver.granularity(granularity, null, problemas);
    if (!problemas.isEmpty()) {
      throw new ValidationException(
          problemas.get(0).code(), "La consulta solicitada no es válida.", problemas);
    }
    Interval intervalo = periodos.interval(periodo);
    // `RN-IN-015` (2): sin fechas, los FTD son todas las cuentas en FIRST_DEPOSIT.
    boolean todaLaHistoria = from == null && to == null;

    List<Broker> catalogo = cifras.brokers();
    List<Seller> vendedores = cifras.commercialForce();
    Map<UUID, Seller> porId = new LinkedHashMap<>();
    vendedores.forEach(v -> porId.put(v.id(), v));

    Reach hastaDonde = alcance.reachOf(actor.id());
    Optional<List<Seller>> raices = raices(hastaDonde, sellerId, actor.id(), vendedores, porId);
    boolean conLoNoAtribuido =
        hastaDonde.kind() == CommercialReach.Kind.EVERYTHING && sellerId == null;

    Map<UUID, Suma> propios = new HashMap<>();
    Suma sinAtribuir = new Suma();
    for (SellerBrokerFigures fila :
        cifras.bySellerAndBroker(intervalo.from(), intervalo.to(), todaLaHistoria)) {
      Suma destino =
          fila.sellerId() == null || !porId.containsKey(fila.sellerId())
              ? sinAtribuir
              : propios.computeIfAbsent(fila.sellerId(), id -> new Suma());
      destino.sumar(fila);
    }
    for (SellerConsumers fila : cifras.consumersBySeller(intervalo.from(), intervalo.to())) {
      Suma destino =
          fila.sellerId() == null || !porId.containsKey(fila.sellerId())
              ? sinAtribuir
              : propios.computeIfAbsent(fila.sellerId(), id -> new Suma());
      destino.consumidores += fila.consumers();
    }

    Map<UUID, List<Seller>> hijos = new LinkedHashMap<>();
    for (Seller vendedor : vendedores) {
      UUID padre =
          vendedor.supervisorId() != null && porId.containsKey(vendedor.supervisorId())
              ? vendedor.supervisorId()
              : null;
      hijos.computeIfAbsent(padre, sinPadre -> new ArrayList<>()).add(vendedor);
    }

    Set<UUID> enElArbol = new HashSet<>();
    List<Construido> nodos =
        construir(raices.orElse(List.of()), hijos, propios, enElArbol, catalogo);
    Suma total = new Suma();
    nodos.forEach(n -> total.mas(n.red()));

    String nombreDelTramo = tramo == null ? null : tramo.name();
    List<Bucket> tramos = null;
    if (tramo != null) {
      List<BucketFigures> conDatos =
          enElArbol.isEmpty()
              ? List.of()
              : cifras.byBucket(
                  enElArbol,
                  intervalo.from(),
                  intervalo.to(),
                  switch (tramo) {
                    case DAY -> "day";
                    case WEEK -> "week";
                    case MONTH -> "month";
                  },
                  calendario.zona());
      Map<LocalDate, BucketFigures> porDia = new HashMap<>();
      conDatos.forEach(b -> porDia.put(b.start(), b));
      tramos =
          SalesPeriodResolver.starts(periodo, tramo, porDia.keySet()).stream()
              .map(
                  inicio -> {
                    BucketFigures b = porDia.get(inicio);
                    return b == null
                        ? new Bucket(inicio, 0, 0)
                        : new Bucket(inicio, b.accounts(), b.ftd());
                  })
              .toList();
    }

    return new BrokerNetworkIndicatorsResponse(
        periodo,
        nodos.stream().map(Construido::nodo).toList(),
        total.figuras(catalogo),
        conLoNoAtribuido ? sinAtribuir.figuras(catalogo) : null,
        nombreDelTramo,
        tramos);
  }

  /**
   * Las raíces del árbol que ve el actor (`RN-IN-002`), o vacío si lo pedido queda fuera de su
   * alcance: entonces los nodos van vacíos y las cifras en cero, nunca `403` ni `404`.
   */
  private static Optional<List<Seller>> raices(
      Reach hastaDonde, UUID sellerId, UUID yo, List<Seller> vendedores, Map<UUID, Seller> porId) {
    return switch (hastaDonde.kind()) {
      case EVERYTHING ->
          sellerId == null
              // El bosque entero: raíz es quien no tiene superior en la fuerza
              // comercial. El segundo caso —un manager que cuelga de un
              // administrador— se olvida con facilidad, y su rama desaparecería
              // sin que nada fallara.
              ? Optional.of(
                  vendedores.stream()
                      .filter(v -> v.supervisorId() == null || !porId.containsKey(v.supervisorId()))
                      .toList())
              : una(sellerId, porId);
      case NETWORK -> {
        // La red de un vendedor lo incluye a él: sin sellerId, su propia rama.
        UUID raiz = sellerId == null ? yo : sellerId;
        yield hastaDonde.sellers().contains(raiz) ? una(raiz, porId) : Optional.empty();
      }
      case OWN -> sellerId == null || sellerId.equals(yo) ? una(yo, porId) : Optional.empty();
    };
  }

  private static Optional<List<Seller>> una(UUID raiz, Map<UUID, Seller> porId) {
    Seller nodo = porId.get(raiz);
    return nodo == null ? Optional.empty() : Optional.of(List.of(nodo));
  }

  /** Post-orden: primero los hijos, y el padre se arma con lo que ellos ya trajeron. */
  private List<Construido> construir(
      List<Seller> nivel,
      Map<UUID, List<Seller>> hijos,
      Map<UUID, Suma> propios,
      Set<UUID> visitados,
      List<Broker> catalogo) {
    List<Construido> nodos = new ArrayList<>(nivel.size());
    for (Seller vendedor : nivel) {
      // Cada nodo se acumula UNA vez, aunque un ciclo en los datos lo repitiera.
      if (!visitados.add(vendedor.id())) {
        continue;
      }
      List<Construido> descendientes =
          construir(
              hijos.getOrDefault(vendedor.id(), List.of()), hijos, propios, visitados, catalogo);
      Suma propio = propios.getOrDefault(vendedor.id(), new Suma());
      Suma red = new Suma();
      red.mas(propio);
      descendientes.forEach(hijo -> red.mas(hijo.red()));
      nodos.add(
          new Construido(
              new Node(
                  new User(
                      vendedor.id(),
                      vendedor.username(),
                      vendedor.firstName(),
                      vendedor.lastName()),
                  vendedor.roleCode(),
                  propio.figuras(catalogo),
                  red.figuras(catalogo),
                  descendientes.stream().map(Construido::nodo).toList()),
              red));
    }
    return nodos;
  }

  private record Construido(Node nodo, Suma red) {}

  /**
   * Lo que se va sumando, por broker. <b>La conversión no se suma</b>: se recalcula al final sobre
   * los totales, porque la conversión de una suma no es la suma de las conversiones.
   */
  private static final class Suma {
    /** cuentas, convertidas, sin titular, ftd, activas, operaciones — por broker. */
    private final Map<UUID, long[]> porBroker = new HashMap<>();

    /**
     * Personas distintas en lo propio; en la red, la suma de los nodos: quien tiene cuentas
     * originadas por dos vendedores cuenta en cada uno.
     */
    private long consumidores;

    void sumar(SellerBrokerFigures f) {
      long[] c = porBroker.computeIfAbsent(f.brokerId(), b -> new long[6]);
      c[0] += f.accounts();
      c[1] += f.converted();
      c[2] += f.withoutHolder();
      c[3] += f.ftd();
      c[4] += f.activeAccounts();
      c[5] += f.operations();
    }

    void mas(Suma otra) {
      otra.porBroker.forEach(
          (broker, c) -> {
            long[] mio = porBroker.computeIfAbsent(broker, b -> new long[6]);
            for (int i = 0; i < 6; i++) {
              mio[i] += c[i];
            }
          });
      consumidores += otra.consumidores;
    }

    Figures figuras(List<Broker> catalogo) {
      long[] total = new long[6];
      List<ByBroker> desglose = new ArrayList<>(catalogo.size());
      for (Broker broker : catalogo) {
        long[] c = porBroker.getOrDefault(broker.id(), new long[6]);
        for (int i = 0; i < 6; i++) {
          total[i] += c[i];
        }
        desglose.add(
            new ByBroker(
                new BrokerRef(broker.id(), broker.name()),
                c[0],
                c[3],
                c[0] - c[1],
                conversion(c[1], c[0]),
                c[2],
                c[4],
                c[5]));
      }
      return new Figures(
          total[0],
          total[3],
          total[0] - total[1],
          conversion(total[1], total[0]),
          total[2],
          consumidores,
          total[4],
          total[5],
          List.copyOf(desglose));
    }
  }

  /** {@code convertidas / cuentas}, <b>nula sin cuentas</b>: cero diría «nadie convirtió». */
  private static BigDecimal conversion(long convertidas, long cuentas) {
    return cuentas == 0
        ? null
        : BigDecimal.valueOf(convertidas)
            .divide(BigDecimal.valueOf(cuentas), 4, RoundingMode.HALF_UP);
  }
}
