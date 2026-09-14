package com.factech.nexus.modules.system.brokers.domain.repository;

import com.factech.nexus.modules.system.brokers.application.BrokerItem;
import java.util.List;

/**
 * La lectura del catálogo de brokers (`RF-SP-052`).
 *
 * <p><b>Solo lectura, y no por omisión sino por regla</b>: `RN-SP-039` deja el catálogo fuera de la
 * API para todo lo que no sea consultarlo. No hay repositorio de escritura porque no hay escritura
 * — las altas son migraciones.
 */
public interface BrokerQueryRepository {

  /**
   * El catálogo, <b>ordenado por nombre</b>.
   *
   * @param incluirInactivos si se <b>añaden</b> los apagados a los activos. No los sustituye: un
   *     filtro que ocultara los activos respondería una pregunta que nadie hace
   */
  List<BrokerItem> findAll(boolean incluirInactivos);
}
