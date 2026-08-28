package com.factech.nexus.modules.system.users.domain.repository;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Portadores activos del rol raíz, contados detrás de una <b>compuerta</b>.
 *
 * <p><b>El bloqueo sobre {@code user_roles} no bastaba, y la corrección del 26-08-2026 dice por
 * qué.</b> Hasta esa fecha esta consulta era un solo {@code SELECT … FOR UPDATE OF ur}, con el
 * razonamiento de que la segunda transacción esperaría y, al soltarse, PostgreSQL reevaluaría la
 * condición sobre la versión ya confirmada. Eso es cierto <b>solo cuando la primera modifica la
 * fila bloqueada</b>:
 *
 * <ul>
 *   <li>`RF-SP-029` y `RF-SP-031` <b>borran</b> la asignación, de modo que la reevaluación la ve
 *       desaparecer y el conteo baja. Ahí funcionaba.
 *   <li>`RF-SP-028` solo cambia {@code users.status} y <b>no toca {@code user_roles}</b>. La
 *       reevaluación reexamina la fila bloqueada —que no cambió— y el {@code users} del {@code
 *       JOIN} lo lee de <b>su propia instantánea</b>, donde la cuenta sigue activa. Las dos
 *       transacciones contaban dos portadores, las dos concluían que sobraba uno, y <b>el sistema
 *       se quedaba sin ninguno</b>.
 * </ul>
 *
 * <p>Lo destapó la prueba concurrente de `RF-SP-028` · `T-15`, que existe exactamente para esto y
 * que su propio texto describe como «la que distingue bloquear el conjunto de bloquear la fila».
 *
 * <p><b>La compuerta es la fila del rol raíz en {@code roles}.</b> Toda operación que pueda reducir
 * el conjunto la toma primero, y como es <b>una sola fila conocida de antemano</b> no hay orden que
 * acordar ni abrazo mortal posible — a diferencia de bloquear también las filas de {@code users},
 * que las trabaría entre sí: cada transacción ya tiene bloqueada la de su objetivo y pediría la del
 * objetivo de la otra.
 *
 * <p>Y el conteo va en una <b>sentencia aparte</b>, que es lo que lo hace funcionar: en {@code READ
 * COMMITTED} cada sentencia toma su propia instantánea, de modo que la que se ejecuta ya con la
 * compuerta en la mano ve lo que la transacción anterior confirmó.
 *
 * <p>El {@code FOR UPDATE OF ur} se conserva: sigue siendo lo que impide que una asignación
 * desaparezca entre el conteo y la escritura.
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
    // 1. La compuerta. Ver el javadoc de la clase: es lo que serializa a los
    //    aspirantes ANTES de contar, y sin ella el conteo se hace sobre una
    //    instantánea que no ve lo que la otra transacción acaba de confirmar.
    em.createNativeQuery("SELECT id FROM roles WHERE code = :raiz FOR UPDATE")
        .setParameter("raiz", ROL_RAIZ)
        .getSingleResult();

    // 2. El conteo, en una sentencia APARTE. En `READ COMMITTED` cada sentencia
    //    toma su propia instantánea, y esta se toma ya con la compuerta en la
    //    mano: por eso ve el estado que la transacción anterior confirmó.
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
