package com.factech.nexus.modules.system.users.domain.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * El delta de una asignación o de un retiro de roles.
 *
 * <p><b>Aditiva e idempotente</b> en un sentido, <b>sustractiva e idempotente</b> en el otro. Que
 * las dos operaciones devuelvan <b>qué cambió de verdad</b> y no lo que se pidió es lo que hace
 * correcta la auditoría: `RF-SP-030` · `T-09` y `RF-SP-031` · `T-10` exigen que no quede <b>ninguna
 * fila</b> cuando la petición no cambió nada, y sin este cálculo cada petición repetida dejaría un
 * evento describiendo una asignación que ya existía.
 *
 * <p>Los duplicados de la entrada se colapsan solos: el resultado es un conjunto.
 *
 * <p><b>Por qué no vive en el agregado {@code User}.</b> `RF-SP-030` · `T-04` pedía un {@code
 * User.assignRoles(...)} que mutara la colección de roles del agregado. No se hizo así, y el motivo
 * es concreto: {@code User.roleIds} es una {@code @ElementCollection} sobre {@code user_roles}, de
 * modo que mutarla hace que Hibernate emita un {@code INSERT} corriente al confirmar — y ese {@code
 * INSERT} es justamente el que `RF-SP-030` §2 sustituye por {@code INSERT … ON CONFLICT DO NOTHING}
 * para que dos peticiones simultáneas con el mismo rol terminen ambas en {@code 200} en lugar de
 * una en {@code 500}. Tocar el agregado <b>deshace</b> la garantía que la migración y el adaptador
 * construyen. El cálculo se queda aquí, puro y probado sin Spring, y la escritura baja a sentencia
 * nativa.
 */
public final class RoleAssignment {

  private RoleAssignment() {}

  /**
   * Los roles que hay que <b>agregar</b>: los pedidos que la persona todavía no tiene.
   *
   * <p>Vacío significa que la operación no cambia nada, y eso <b>no es un error</b> (`FA-001` de
   * ambos requerimientos): significa que no hay que escribir ni auditar.
   */
  public static Set<UUID> aAgregar(Collection<UUID> actuales, Collection<UUID> pedidos) {
    Set<UUID> tiene = Set.copyOf(actuales);
    Set<UUID> nuevos = new LinkedHashSet<>();
    for (UUID pedido : pedidos) {
      if (!tiene.contains(pedido)) {
        nuevos.add(pedido);
      }
    }
    return nuevos;
  }

  /** Los roles que hay que <b>retirar</b>: los pedidos que la persona sí tiene. */
  public static Set<UUID> aRetirar(Collection<UUID> actuales, Collection<UUID> pedidos) {
    Set<UUID> tiene = Set.copyOf(actuales);
    Set<UUID> retirables = new LinkedHashSet<>();
    for (UUID pedido : pedidos) {
      if (tiene.contains(pedido)) {
        retirables.add(pedido);
      }
    }
    return retirables;
  }

  /** El conjunto resultante tras aplicar el delta, que es lo que la respuesta debe describir. */
  public static Set<UUID> resultado(Collection<UUID> actuales, Collection<UUID> agregados) {
    Set<UUID> resultado = new LinkedHashSet<>(actuales);
    resultado.addAll(agregados);
    return resultado;
  }

  /** El conjunto resultante tras un retiro. */
  public static Set<UUID> resultadoTrasRetirar(
      Collection<UUID> actuales, Collection<UUID> retirados) {
    Set<UUID> resultado = new LinkedHashSet<>(actuales);
    resultado.removeAll(Set.copyOf(retirados));
    return resultado;
  }
}
