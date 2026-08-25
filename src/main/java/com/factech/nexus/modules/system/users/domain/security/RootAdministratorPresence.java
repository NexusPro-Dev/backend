package com.factech.nexus.modules.system.users.domain.security;

import com.factech.nexus.modules.system.users.domain.repository.RootRoleHolderRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * `RN-SP-001`: el sistema <b>nunca</b> se queda sin superadministrador activo.
 *
 * <p>Es una de las reglas del módulo cuyo incumplimiento no se puede deshacer desde la propia API:
 * un sistema sin superadministrador no tiene a nadie capaz de nombrar uno, y solo se sale de ahí
 * escribiendo en la base a mano. Por eso la comprobación es pesimista.
 *
 * <p><b>El bloqueo va sobre el conjunto de portadores, no sobre la fila del usuario.</b> Es la
 * distinción que decide si la regla funciona, y es contraintuitiva. Con dos superadministradores
 * activos y dos retiros simultáneos —uno sobre cada uno— bloquear la fila de <i>users</i> no
 * serializa nada: las dos transacciones bloquean filas distintas, las dos cuentan dos portadores,
 * las dos concluyen que sobra uno, y el sistema termina sin ninguno <b>sin que nada falle</b>.
 *
 * <p>Bloquear las filas de {@code user_roles} sí lo impide. La segunda transacción espera a la
 * primera sobre la fila que ambas tocan y, al soltarse, PostgreSQL <b>reevalúa</b> la condición del
 * {@code SELECT … FOR UPDATE} sobre la versión confirmada: la fila que la primera acaba de borrar
 * desaparece del resultado, el conteo baja a uno, y el segundo retiro se rechaza con {@code 409}.
 * La prueba concurrente de `RF-SP-031` · `T-01` es exactamente esa, y sin el bloqueo termina
 * dejando el sistema sin administración.
 */
@Component
public class RootAdministratorPresence {

  private final RootRoleHolderRepository portadores;

  public RootAdministratorPresence(RootRoleHolderRepository portadores) {
    this.portadores = portadores;
  }

  /**
   * ¿Sobrevive la regla si {@code usuario} deja de portar el rol raíz?
   *
   * <p>Se llama <b>siempre</b> que el retiro alcance el rol raíz, y no solo cuando «parece» el
   * último: quién es el último es justamente lo que no se puede saber sin el bloqueo.
   *
   * @return {@code true} si tras el retiro queda al menos un superadministrador activo
   */
  public boolean sobreviveSinEl(UUID usuario) {
    Set<UUID> activos = portadores.lockActiveRootHolders();
    return activos.stream().anyMatch(portador -> !portador.equals(usuario));
  }
}
