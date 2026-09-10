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
}
