package com.factech.nexus.modules.system.brokers.domain.service;

import com.factech.nexus.modules.system.brokers.application.NetworkIndicatorsResponse;
import com.factech.nexus.modules.system.brokers.application.NetworkIndicatorsResponse.Holder;
import com.factech.nexus.modules.system.brokers.application.NetworkIndicatorsResponse.Indicators;
import com.factech.nexus.modules.system.brokers.application.NetworkIndicatorsResponse.Node;
import com.factech.nexus.modules.system.brokers.domain.models.UserBrokerStatus;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerAccountQueryRepository;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerAccountQueryRepository.DirectCountRow;
import com.factech.nexus.modules.system.brokers.domain.repository.BrokerAccountQueryRepository.SellerRow;
import com.factech.nexus.shared.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
 * Los indicadores de la red comercial (`RF-SP-058`).
 *
 * <h2>La regla es UNA, aplicada en todos los niveles</h2>
 *
 * <pre>{@code network(nodo) = own(nodo) + Σ network(hijo)}</pre>
 *
 * <p>Es lo que pidió el responsable del proyecto —«a los agentes se les suman los ftds directos, a
 * los directores los propios más los ftds de los agentes, y a los managers los propios más la
 * sumatoria de los directores»— dicho una sola vez. Lo que hay debajo son <b>los cuatro
 * cuidados</b> que esas tres frases no dicen, y cada uno tiene su forma de fallar <b>en
 * silencio</b>.
 *
 * <h2>1. La unidad es la CUENTA, y solo de consumidores</h2>
 *
 * <p>Un cliente con dos cuentas depositadas suma <b>dos</b> en {@code ftd} y <b>una</b> en {@code
 * consumers} (`RN-SP-048`): lo que se mide es el dinero que entró. Y la cuenta personal de un
 * vendedor <b>no cuenta</b> —«solo los roles de tipo consumidores tienen ftds»—, lo que se resuelve
 * <b>por rol</b> en la consulta y no por la posición en el árbol.
 *
 * <h2>2. {@code own} se LEE, nunca se deriva</h2>
 *
 * <p>Un consumidor cuelga de <b>un solo</b> superior ({@code uq_user_supervisors_vigente}), de modo
 * que cada cuenta entra en <b>un único</b> {@code own} — y de ahí que la suma de todos los {@code
 * own} sea el total, sin duplicados. Derivar {@code own} recorriendo el árbol reintroduciría el
 * doble conteo que esa unicidad ya evita.
 *
 * <h2>3. Cada nodo se acumula UNA vez</h2>
 *
 * <p>El recorrido lleva su propio registro de visitados. Que no pueda haber ciclos —`RN-SP-020` ata
 * esta cadena a la de roles, que es acíclica— es un argumento <b>sobre los datos</b>, y una suma no
 * debería depender de un argumento.
 *
 * <h2>4. La conversión se RECALCULA, no se acumula</h2>
 *
 * <p>Promediar las conversiones de los hijos daría un número que no es la conversión de nada: es el
 * error clásico del promedio de promedios. Se recalcula en cada nodo sobre sus propios totales.
 *
 * <h2>Y lo no atribuido es lo que hace que cuadre</h2>
 *
 * <pre>{@code Σ network(raíces) + unassigned == total de RF-SP-057}</pre>
 *
 * <p>Las cuentas de consumidores que no cuelgan de ningún vendedor no entran en ningún nodo. Sin
 * publicarlas, el árbol sumaría <b>menos</b> que el listado global y no habría forma de saber si
 * falta algo o si el cálculo está mal. `CA-SP-664` comprueba esa igualdad, y es el único criterio
 * que detecta un doble conteo o una omisión.
 */
@Service
public class GetNetworkIndicatorsService {

  private final BrokerAccountQueryRepository consultas;

  public GetNetworkIndicatorsService(BrokerAccountQueryRepository consultas) {
    this.consultas = consultas;
  }

  /**
   * Tres consultas planas y un recorrido, <b>en la misma transacción de solo lectura</b>.
   *
   * <p>Leídas por separado, una reasignación simultánea podría dejar un árbol cuyas ramas no
   * corresponden a los conteos.
   */
  @Transactional(readOnly = true)
  public NetworkIndicatorsResponse of(UUID rootId) {
    List<SellerRow> vendedores = consultas.findCommercialForce();

    Map<UUID, Indicators> propios =
        propiosPorPersona(
            consultas.countDirectAccountsBySupervisor(),
            consultas.countDirectConsumersBySupervisor());

    Map<UUID, List<SellerRow>> hijos = new LinkedHashMap<>();
    Set<UUID> esVendedor = new HashSet<>();
    vendedores.forEach(v -> esVendedor.add(v.id()));

    for (SellerRow vendedor : vendedores) {
      // La raíz de una rama es quien no tiene superior vigente O cuyo superior
      // NO es fuerza comercial —un manager que cuelga de un administrador—. El
      // segundo caso se olvida con facilidad, y su rama entera desaparecería
      // del árbol sin que nada fallara.
      UUID padre =
          vendedor.supervisorId() != null && esVendedor.contains(vendedor.supervisorId())
              ? vendedor.supervisorId()
              : null;
      hijos.computeIfAbsent(padre, sinPadre -> new ArrayList<>()).add(vendedor);
    }

    List<Node> raices;
    if (rootId == null) {
      raices = construir(hijos.getOrDefault(null, List.of()), hijos, propios, new HashSet<>());
    } else {
      SellerRow raiz =
          vendedores.stream()
              .filter(v -> v.id().equals(rootId))
              .findFirst()
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "VAL-002",
                          "No existe una persona de la fuerza comercial con ese identificador."));
      raices = construir(List.of(raiz), hijos, propios, new HashSet<>());
    }

    Indicators totales = sumar(raices.stream().map(Node::network).toList());

    // `unassigned` no significa nada dentro de una rama: allí lo que falta no
    // es «lo de nadie», es todo lo que está fuera de esa rama.
    Indicators sinAtribuir =
        rootId == null
            ? indicadores(
                porEstado(consultas.countUnassignedAccounts()),
                consultas.countUnassignedConsumers())
            : null;

    return new NetworkIndicatorsResponse(raices, totales, sinAtribuir);
  }

  /** Post-orden: primero los hijos, y el padre se arma con lo que ellos ya trajeron. */
  private List<Node> construir(
      List<SellerRow> nivel,
      Map<UUID, List<SellerRow>> hijos,
      Map<UUID, Indicators> propios,
      Set<UUID> visitados) {

    List<Node> nodos = new ArrayList<>(nivel.size());
    for (SellerRow vendedor : nivel) {
      // Cada nodo se acumula UNA vez. Ver el javadoc de la clase, cuidado 3.
      if (!visitados.add(vendedor.id())) {
        continue;
      }

      List<Node> descendientes =
          construir(hijos.getOrDefault(vendedor.id(), List.of()), hijos, propios, visitados);

      Indicators own = propios.getOrDefault(vendedor.id(), VACIO);

      List<Indicators> partes = new ArrayList<>(descendientes.size() + 1);
      partes.add(own);
      descendientes.forEach(hijo -> partes.add(hijo.network()));

      nodos.add(
          new Node(
              new Holder(
                  vendedor.id(), vendedor.username(), vendedor.firstName(), vendedor.lastName()),
              vendedor.roleCode(),
              own,
              sumar(partes),
              descendientes));
    }
    return nodos;
  }

  /**
   * Suma bloques y <b>recalcula</b> la conversión sobre el resultado.
   *
   * <p>Lo segundo es el cuidado 4: la conversión de una suma no es la suma de las conversiones.
   */
  private static Indicators sumar(List<Indicators> partes) {
    int cuentas = 0;
    int ftd = 0;
    int pendientes = 0;
    int consumidores = 0;
    for (Indicators parte : partes) {
      cuentas += parte.accounts();
      ftd += parte.ftd();
      pendientes += parte.pending();
      // Sumar cardinales de conjuntos DISJUNTOS: una persona cuelga de un solo
      // superior, de modo que dos ramas nunca comparten consumidor. El día que
      // alguien pueda colgar de dos, esta línea deja de valer.
      consumidores += parte.consumers();
    }
    return new Indicators(cuentas, ftd, pendientes, conversion(ftd, cuentas), consumidores);
  }

  /**
   * {@code ftd / accounts}, <b>nula cuando no hay cuentas</b>.
   *
   * <p>Cero se leería como «nadie convirtió» y la verdad es «no hay nada que convertir». Cuatro
   * decimales: con más, dos nodos con el mismo cociente se verían distintos por el ruido del
   * binario.
   */
  private static BigDecimal conversion(int ftd, int cuentas) {
    if (cuentas == 0) {
      return null;
    }
    return BigDecimal.valueOf(ftd).divide(BigDecimal.valueOf(cuentas), 4, RoundingMode.HALF_UP);
  }

  private static Map<UUID, Indicators> propiosPorPersona(
      List<DirectCountRow> cuentas, List<DirectCountRow> consumidores) {

    Map<UUID, Map<String, Integer>> porJefe = new HashMap<>();
    for (DirectCountRow fila : cuentas) {
      porJefe
          .computeIfAbsent(fila.supervisorId(), jefe -> new HashMap<>())
          .merge(fila.status(), fila.total(), Integer::sum);
    }

    Map<UUID, Integer> personas = new HashMap<>();
    consumidores.forEach(fila -> personas.merge(fila.supervisorId(), fila.total(), Integer::sum));

    Map<UUID, Indicators> resultado = new HashMap<>();
    Set<UUID> jefes = new HashSet<>(porJefe.keySet());
    jefes.addAll(personas.keySet());
    for (UUID jefe : jefes) {
      resultado.put(
          jefe, indicadores(porJefe.getOrDefault(jefe, Map.of()), personas.getOrDefault(jefe, 0)));
    }
    return resultado;
  }

  private static Map<String, Integer> porEstado(List<DirectCountRow> filas) {
    Map<String, Integer> resultado = new HashMap<>();
    filas.forEach(fila -> resultado.merge(fila.status(), fila.total(), Integer::sum));
    return resultado;
  }

  private static Indicators indicadores(Map<String, Integer> porEstado, int consumidores) {
    int ftd = porEstado.getOrDefault(UserBrokerStatus.FIRST_DEPOSIT.name(), 0);
    int pendientes = porEstado.getOrDefault(UserBrokerStatus.REGISTER.name(), 0);
    int cuentas = ftd + pendientes;
    return new Indicators(cuentas, ftd, pendientes, conversion(ftd, cuentas), consumidores);
  }

  /** Quien no tiene nada colgando: ceros y conversión <b>nula</b>, no cero. */
  private static final Indicators VACIO = new Indicators(0, 0, 0, null, 0);
}
