package com.factech.nexus.modules.system.roles.domain.repository;

import com.factech.nexus.modules.system.roles.application.RoleHierarchyLock;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

/**
 * Adaptador de {@link RoleHierarchyLock} sobre el bloqueo consultivo de PostgreSQL (`RF-SP-008` ·
 * `T-04`).
 *
 * <p><b>Por qué vive en {@code domain/repository}.</b> Por lo mismo que {@link
 * SecurityContextActor}: {@code application} no puede depender de nada y esta es la única capa del
 * módulo que la disposición admite para un adaptador. Este además sí habla con la base de datos.
 *
 * <p><b>{@code pg_try_advisory_xact_lock} y no {@code pg_advisory_xact_lock}.</b> La primera
 * devuelve {@code false} en el acto si el bloqueo está tomado; la segunda espera. Se eligió la
 * primera (`plan.md` §5) porque una espera indefinida encadena peticiones colgadas ocupando cada
 * una su conexión del pool, y porque con espera la prueba de `CA-SP-161` dependería de la
 * temporización de dos transacciones.
 *
 * <p><b>{@code _xact_} y no de sesión.</b> El bloqueo muere con la transacción, también si esta
 * revierte. Uno de sesión habría que liberarlo a mano, y una excepción no prevista dejaría la
 * jerarquía inmovilizada hasta que alguien reiniciara el proceso.
 *
 * <p><b>Es del motor, no del proceso.</b> Con varias instancias sigue siendo uno solo, que es lo
 * que hace que la garantía sobreviva a la segunda réplica — al contrario que los registros en
 * memoria de `AccessRevocationRegistry` o `RateLimitLedger`.
 */
@Component
public class AdvisoryRoleHierarchyLock implements RoleHierarchyLock {

  /**
   * Clave fija y arbitraria: identifica «la jerarquía de roles» y nada más.
   *
   * <p>Debe ser <b>distinta</b> de la de cualquier otro bloqueo consultivo del sistema, porque el
   * espacio de claves es único para toda la base de datos: repetirla haría que dos operaciones sin
   * relación se serializaran entre sí y que un interbloqueo apareciera entre módulos que no se
   * conocen. Las tomadas hoy son {@code 716_016} —la cadena de membresías— y {@code 6_252_025_001}
   * —la purga de sesiones—.
   */
  private static final long CANDADO_DE_LA_JERARQUIA = 716_008L;

  private final EntityManager em;

  public AdvisoryRoleHierarchyLock(EntityManager em) {
    this.em = em;
  }

  @Override
  public boolean tryAcquire() {
    // Nativa: no hay equivalente en JPQL para una función del motor. El
    // resultado es un booleano y nunca es nulo.
    return (Boolean)
        em.createNativeQuery("SELECT pg_try_advisory_xact_lock(:clave)")
            .setParameter("clave", CANDADO_DE_LA_JERARQUIA)
            .getSingleResult();
  }
}
