package com.factech.nexus.modules.system.users.domain.repository;

import java.util.Set;
import java.util.UUID;

/**
 * Portadores activos del rol raíz, bajo bloqueo (`RN-SP-001`).
 *
 * <p>Puerto propio y no un método más de {@link UserRepository} porque lo que devuelve <b>no es un
 * dato, es una garantía</b>: la lista solo vale mientras dure la transacción que la pidió, y quien
 * la consuma fuera de una transacción de escritura está leyendo algo que puede haber dejado de ser
 * cierto antes de terminar la frase. Tenerlo aparte hace visible esa condición.
 */
public interface RootRoleHolderRepository {

  /**
   * Bloquea las asignaciones del rol raíz a personas activas y devuelve quiénes son.
   *
   * <p><b>El bloqueo es sobre las filas de la asignación</b>, no sobre las de la persona: es lo
   * único que serializa dos retiros simultáneos sobre portadores distintos. La justificación
   * completa está en {@code RootAdministratorPresence}.
   */
  Set<UUID> lockActiveRootHolders();
}
