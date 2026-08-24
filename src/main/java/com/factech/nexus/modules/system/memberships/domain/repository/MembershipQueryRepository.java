package com.factech.nexus.modules.system.memberships.domain.repository;

import com.factech.nexus.modules.system.memberships.application.MembershipDetailItem;
import com.factech.nexus.modules.system.memberships.application.MembershipItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de lectura de la cadena (`RF-SP-017`, `RF-SP-018`).
 *
 * <p>Separado del puerto de escritura, igual que en el módulo de permisos: las dos consultas no
 * necesitan ninguna de las cinco escrituras, y un puerto único obligaría a que cualquier doble de
 * prueba implementara métodos que el caso de uso no llama.
 */
public interface MembershipQueryRepository {

  /**
   * Cadena completa, de la cima al extremo inferior.
   *
   * @param search término sobre código y nombre; nulo o en blanco equivale a ausente
   */
  List<MembershipItem> findChain(String search);

  /** Detalle con los dos vecinos inmediatos, o vacío si no existe. */
  Optional<MembershipDetailItem> findDetail(UUID id);
}
