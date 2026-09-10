package com.factech.nexus.modules.system.brokers.domain.repository;

import com.factech.nexus.modules.system.brokers.application.BrokerAccountItem;
import com.factech.nexus.modules.system.brokers.application.TeamBrokerAccountItem;
import com.factech.nexus.modules.system.brokers.domain.models.UserBrokerStatus;
import java.util.List;
import java.util.UUID;

/**
 * La primera lectura de {@code user_brokers} (`RF-SP-055` · `T-04`, `RF-SP-056` · `T-01`).
 *
 * <p><b>La tabla llevaba un día escribiéndose y nadie la había leído nunca.</b> La escribe el
 * registro por enlace desde el 09-09-2026 (`RF-SP-045` · `T-19`), y hasta hoy quien declaraba su
 * cuenta la mandaba al sistema y no volvía a verla.
 *
 * <p><b>Devuelve DTOs de {@code application} y no filas propias</b>, como {@link
 * BrokerQueryRepository}: lo que se lee es una proyección de un puñado de campos, y una capa de
 * registros intermedios que se copian uno a uno al DTO no añade nada que este comentario no diga.
 *
 * <p><b>`RF-SP-052` decidió NO crear la entidad JPA de esta tabla</b> —«no hay caso de uso que la
 * lea ni que la escriba, y una entidad sin uso es código que hay que mantener»— y ese argumento
 * <b>caduca hoy</b>. Lo que no caduca es la conclusión: se sigue leyendo con consulta nativa,
 * porque lo que hace falta es una proyección que cruza tres tablas y no un agregado que cargar.
 */
public interface BrokerAccountQueryRepository {

  /**
   * Las cuentas de una persona, ordenadas por nombre de broker e identificador de cuenta.
   *
   * <p><b>El orden es determinista hasta el último desempate</b>: una persona con dos cuentas en el
   * mismo broker las vería bailar entre llamadas si el orden lo eligiera el motor.
   *
   * <p><b>No comprueba autorización ni existencia</b>: eso es del servicio. Aquí, una persona que
   * no existe y una que no declaró nada devuelven lo mismo —la lista vacía—, y distinguirlas es
   * justo lo que este puerto no debe hacer.
   */
  List<BrokerAccountItem> findByUser(UUID userId);

  /**
   * Las cuentas del equipo <b>vigente</b> de una persona, filtradas y paginadas.
   *
   * <p><b>Un solo nivel</b> (`RN-SP-046`): quienes reportan directamente. El árbol descendente
   * publicaría la estructura entera de la empresa por una lectura de cuentas de broker.
   *
   * <p><b>Los dos filtros admiten nulo, y el nulo es «sin filtrar»</b> — al revés que el nulo de
   * {@code broker_username}, que sí significa algo. Se combinan con Y.
   */
  List<TeamBrokerAccountItem> findByTeamOf(
      UUID supervisorId, UserBrokerStatus estado, UUID brokerId, int offset, int limit);

  /**
   * Cuántas cuentas cumplen el mismo filtro.
   *
   * <p><b>Dos consultas y no una ventana {@code COUNT(*) OVER ()}</b>: esa devuelve el total
   * repetido en cada fila y se rompe justo en el caso que aquí es normal —la página vacía no trae
   * ninguna fila donde leerlo—.
   */
  int countByTeamOf(UUID supervisorId, UserBrokerStatus estado, UUID brokerId);

  /**
   * Todas las cuentas del sistema, filtradas y paginadas (`RF-SP-057`).
   *
   * <p><b>Sin ningún filtro devuelve el sistema entero</b>, y eso es lo que el permiso significa:
   * quien llega aquí trae `broker-accounts:read`, que ya alcanza a todo el mundo.
   *
   * <p><b>{@code supervisorId} es LA RED ENTERA, en profundidad</b> (`RN-SP-047`) — al revés que
   * {@link #findByTeamOf}, que es de un nivel. La asimetría no es un descuido: aquel lo autoriza la
   * <b>estructura</b> y devolver la rama completa publicaría la empresa a quien solo lleva un
   * equipo; este lo autoriza el <b>permiso</b>, de modo que la profundidad <b>no concede nada</b> —
   * ahorra recorrer el árbol.
   *
   * <p>El estado ya viene resuelto a enumerado: traducir el texto es del servicio, porque un valor
   * inválido es un {@code 400} y este puerto no sabe de códigos de error.
   */
  List<TeamBrokerAccountItem> findAll(BrokerAccountFilters filtros, int offset, int limit);

  /** Cuántas cuentas cumplen el mismo filtro. Dos consultas y no una ventana, como arriba. */
  int countAll(BrokerAccountFilters filtros);

  /**
   * Los filtros del listado de administración, ya validados.
   *
   * <p><b>Un registro y no ocho argumentos</b>: con este número, una llamada posicional deja pasar
   * sin ruido el día que alguien intercambie {@code supervisorId} y {@code userId} — dos {@code
   * UUID} seguidos que significan cosas opuestas.
   *
   * <p><b>Todo nulo significa «sin filtrar»</b>, y se combinan con Y.
   */
  /**
   * La fuerza comercial vigente: una fila por vendedor, con su superior (`RF-SP-058` · `T-01`).
   *
   * <p><b>Se identifica por {@code user_roles.role_type} y no uniendo con {@code roles}</b>: la
   * columna es una <b>copia</b> del tipo en la propia fila (`V52`, `RN-SP-025`), y la unicidad de
   * rol vendedor por persona —del mismo `RN-SP-025`— garantiza que <b>no salgan filas
   * repetidas</b>. Sin esa garantía, una persona con dos roles vendedores se duplicaría en el árbol
   * y sus números se contarían dos veces.
   */
  List<SellerRow> findCommercialForce();

  /**
   * Cuántas cuentas de <b>consumidores</b> cuelgan directamente de cada persona, por estado.
   *
   * <p><b>El consumidor se comprueba con {@code EXISTS} y no con un {@code JOIN}</b>: el {@code
   * JOIN} multiplicaría la fila de la cuenta por cada rol de consumidor que la persona porte, y
   * cada cuenta se contaría tantas veces como roles. Es el defecto que no falla — devuelve de más y
   * parece un buen mes.
   *
   * <p><b>Solo consumidores</b> (`RN-SP-048`): la cuenta personal de un vendedor no es una
   * captación.
   */
  List<DirectCountRow> countDirectAccountsBySupervisor();

  /** Cuántos consumidores distintos cuelgan directamente de cada persona. */
  List<DirectCountRow> countDirectConsumersBySupervisor();

  /**
   * Las cuentas de consumidores que <b>no cuelgan de ningún vendedor</b>, por estado.
   *
   * <p>Sin superior vigente, o colgando de quien no es fuerza comercial. <b>Es lo que hace que los
   * números cuadren</b>: sin esto, el árbol suma menos que el listado global y nadie puede saber si
   * falta algo o si el cálculo está mal.
   */
  List<DirectCountRow> countUnassignedAccounts();

  /** Cuántos consumidores distintos quedan sin atribuir. */
  int countUnassignedConsumers();

  /** Un vendedor y su superior vigente, que puede no tenerlo. */
  record SellerRow(
      UUID id,
      String username,
      String firstName,
      String lastName,
      String roleCode,
      UUID supervisorId) {}

  /**
   * Un conteo agrupado.
   *
   * <p>{@code status} va nulo en los conteos de personas, donde el estado no agrupa: una persona
   * con una cuenta en cada estado es <b>una</b> persona, y repartirla entre los dos grupos la
   * contaría dos veces.
   */
  record DirectCountRow(UUID supervisorId, String status, int total) {}

  record BrokerAccountFilters(
      UUID supervisorId,
      UUID userId,
      UserBrokerStatus status,
      UUID brokerId,
      String search,
      java.time.OffsetDateTime from,
      java.time.OffsetDateTime to) {}
}
