package com.factech.nexus.modules.indicators.domain.service;

import com.factech.nexus.modules.indicators.application.BrokerAccountsSummaryResponse;
import com.factech.nexus.modules.indicators.application.BrokerAccountsSummaryResponse.Person;
import com.factech.nexus.modules.indicators.application.BrokerAccountsSummaryResponse.RoleGroup;
import com.factech.nexus.modules.indicators.application.BrokerAccountsSummaryResponse.Totals;
import com.factech.nexus.modules.indicators.application.BrokerAccountsSummaryResponse.User;
import com.factech.nexus.modules.system.brokers.application.BrokerAccountFigures;
import com.factech.nexus.modules.system.brokers.application.BrokerAccountFigures.Seller;
import com.factech.nexus.modules.system.brokers.application.BrokerAccountFigures.SellerBrokerFigures;
import com.factech.nexus.modules.system.roles.application.AuthenticatedActor;
import com.factech.nexus.modules.system.users.application.CommercialReach;
import com.factech.nexus.modules.system.users.application.CommercialReach.Reach;
import com.factech.nexus.shared.time.BusinessCalendar;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El resumen de cuentas de broker y de mi personal (`RF-IN-010`).
 *
 * <p><b>Sin consultas propias</b>: las cifras salen de {@link BrokerAccountFigures}, el puerto de
 * `RF-IN-009`, pedido dos veces —la historia entera y hoy—, cada cifra con su fecha (`RN-IN-015`).
 * Cada persona cuenta lo que originó ella; el alcance es el de `RN-IN-002`.
 */
@Service
public class GetBrokerAccountsSummaryService {

  private final BrokerAccountFigures cifras;
  private final CommercialReach alcance;
  private final AuthenticatedActor actor;
  private final BusinessCalendar calendario;

  public GetBrokerAccountsSummaryService(
      BrokerAccountFigures cifras,
      CommercialReach alcance,
      AuthenticatedActor actor,
      BusinessCalendar calendario) {
    this.cifras = cifras;
    this.alcance = alcance;
    this.actor = actor;
    this.calendario = calendario;
  }

  @Transactional(readOnly = true)
  public BrokerAccountsSummaryResponse get() {
    LocalDate hoy = calendario.hoy();
    OffsetDateTime desde = hoy.atStartOfDay(calendario.zona()).toOffsetDateTime();
    OffsetDateTime hasta = hoy.plusDays(1).atStartOfDay(calendario.zona()).toOffsetDateTime();

    List<Seller> fuerza = cifras.commercialForce();
    Map<UUID, Seller> porId = new LinkedHashMap<>();
    fuerza.forEach(v -> porId.put(v.id(), v));

    Reach hastaDonde = alcance.reachOf(actor.id());
    boolean todo = hastaDonde.kind() == CommercialReach.Kind.EVERYTHING;
    Set<UUID> delAlcance =
        switch (hastaDonde.kind()) {
          case EVERYTHING -> porId.keySet();
          case NETWORK -> hastaDonde.sellers();
          case OWN -> Set.of(actor.id());
        };

    // 0 cuentas, 1 ftd, 2 sin titular, 3 creadas hoy, 4 ftd hoy — por vendedor.
    Map<UUID, long[]> porVendedor = new HashMap<>();
    long[] sinVendedor = new long[5];
    for (SellerBrokerFigures f : cifras.bySellerAndBroker(null, null, true)) {
      long[] c = destino(f.sellerId(), porId, porVendedor, sinVendedor);
      c[0] += f.accounts();
      c[1] += f.ftd();
      c[2] += f.withoutHolder();
    }
    for (SellerBrokerFigures f : cifras.bySellerAndBroker(desde, hasta, false)) {
      long[] c = destino(f.sellerId(), porId, porVendedor, sinVendedor);
      c[3] += f.accounts();
      c[4] += f.ftd();
    }

    long[] total = new long[5];
    Map<String, List<Person>> porRol = new LinkedHashMap<>();
    for (Seller vendedor : preorden(fuerza, porId)) {
      if (!delAlcance.contains(vendedor.id())) {
        continue;
      }
      long[] c = porVendedor.getOrDefault(vendedor.id(), new long[5]);
      sumar(total, c);
      porRol
          .computeIfAbsent(vendedor.roleCode(), rol -> new ArrayList<>())
          .add(
              new Person(
                  new User(
                      vendedor.id(),
                      vendedor.username(),
                      vendedor.firstName(),
                      vendedor.lastName()),
                  c[0],
                  c[1],
                  c[3],
                  c[4]));
    }
    if (todo) {
      sumar(total, sinVendedor);
    }

    List<RoleGroup> grupos = new ArrayList<>(porRol.size());
    porRol.forEach(
        (rol, personas) -> {
          List<Person> ordenadas =
              personas.stream().sorted(Comparator.comparing(p -> p.user().username())).toList();
          grupos.add(
              new RoleGroup(
                  rol,
                  ordenadas.stream().mapToLong(Person::accounts).sum(),
                  ordenadas.stream().mapToLong(Person::ftd).sum(),
                  ordenadas.stream().mapToLong(Person::accountsToday).sum(),
                  ordenadas.stream().mapToLong(Person::ftdToday).sum(),
                  ordenadas));
        });

    return new BrokerAccountsSummaryResponse(
        hoy,
        new Totals(total[0], total[1], total[2], todo ? sinVendedor[0] : null, total[3], total[4]),
        List.copyOf(grupos));
  }

  /** Lo no atribuido —sin origen o con origen fuera de la fuerza comercial— va aparte. */
  private static long[] destino(
      UUID vendedor, Map<UUID, Seller> porId, Map<UUID, long[]> porVendedor, long[] sinVendedor) {
    return vendedor == null || !porId.containsKey(vendedor)
        ? sinVendedor
        : porVendedor.computeIfAbsent(vendedor, id -> new long[5]);
  }

  private static void sumar(long[] a, long[] b) {
    for (int i = 0; i < a.length; i++) {
      a[i] += b[i];
    }
  }

  /**
   * La fuerza comercial de la cúspide hacia abajo: el orden en que aparece cada rol es el de la
   * jerarquía, sin leer el catálogo de roles.
   */
  private static List<Seller> preorden(List<Seller> fuerza, Map<UUID, Seller> porId) {
    Map<UUID, List<Seller>> hijos = new HashMap<>();
    List<Seller> raices = new ArrayList<>();
    for (Seller v : fuerza) {
      if (v.supervisorId() == null || !porId.containsKey(v.supervisorId())) {
        raices.add(v);
      } else {
        hijos.computeIfAbsent(v.supervisorId(), p -> new ArrayList<>()).add(v);
      }
    }
    List<Seller> orden = new ArrayList<>(fuerza.size());
    Set<UUID> vistos = new HashSet<>();
    List<Seller> pila = new ArrayList<>(raices.reversed());
    while (!pila.isEmpty()) {
      Seller v = pila.removeLast();
      // Un ciclo en los datos no repite a nadie.
      if (!vistos.add(v.id())) {
        continue;
      }
      orden.add(v);
      pila.addAll(hijos.getOrDefault(v.id(), List.of()).reversed());
    }
    return orden;
  }
}
