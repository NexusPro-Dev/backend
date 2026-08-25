package com.factech.nexus.modules.system.users.domain.repository;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Portadores activos del rol raíz, con bloqueo pesimista sobre la asignación.
 *
 * <p><b>{@code FOR UPDATE OF ur} y no {@code OF u}.</b> Lo que hay que serializar es el conjunto de
 * asignaciones, porque es lo que la operación modifica: bloqueando las filas de {@code users}, dos
 * retiros simultáneos sobre superadministradores <b>distintos</b> tocan filas distintas, no se
 * esperan, ambos ven dos portadores y ambos concluyen que sobra uno.
 *
 * <p>Con las filas de {@code user_roles} bloqueadas, la segunda transacción espera y, al soltarse,
 * PostgreSQL reevalúa la condición sobre la versión ya confirmada: la asignación que la primera
 * borró desaparece del resultado y el conteo baja. Esa reevaluación es la garantía entera.
 *
 * <p>El {@code ORDER BY} no es cosmético: fija el orden en que se toman los bloqueos, y con él dos
 * transacciones que recorren el mismo conjunto no pueden trabarse en abrazo mortal.
 */
@Repository
public class JpaRootRoleHolderRepository implements RootRoleHolderRepository {

  /** El código del rol raíz lo fija `V7__seed_system_roles.sql` y no cambia jamás. */
  private static final String ROL_RAIZ = "SUPERADMIN";

  private final EntityManager em;

  public JpaRootRoleHolderRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public Set<UUID> lockActiveRootHolders() {
    List<?> filas =
        em.createNativeQuery(
                """
                SELECT ur.user_id
                  FROM user_roles ur
                  JOIN roles r ON r.id = ur.role_id
                  JOIN users u ON u.id = ur.user_id
                 WHERE r.code = :raiz
                   AND u.deleted_at IS NULL
                   AND u.status = 'ACTIVO'
                 ORDER BY ur.user_id
                   FOR UPDATE OF ur
                """)
            .setParameter("raiz", ROL_RAIZ)
            .getResultList();

    Set<UUID> portadores = new LinkedHashSet<>();
    filas.forEach(fila -> portadores.add((UUID) fila));
    return portadores;
  }
}
