package com.factech.nexus.modules.system.users.domain.repository;

import com.factech.nexus.modules.system.users.application.PermissionHolderLookup;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PermissionHolderLookup} sobre la misma consulta que resuelve los permisos efectivos
 * (`RF-AC-008` · `T-03`).
 *
 * <p><b>Un {@code EXISTS} sobre {@link JpaEffectivePermissions#ROLES_QUE_CONCEDEN}, acotado al
 * código.</b> No hay una segunda definición de «portar»: la frase es la misma constante, y lo único
 * que este adaptador añade es el {@code p.code = :permiso}. Que sea un {@code EXISTS} y no un
 * recorrido de la lista es lo que hace que la pregunta cueste una sentencia y no lea ningún permiso
 * que no se preguntó.
 *
 * <p><b>Adaptador aparte y no un método más en {@code PublishedUserCatalog}</b>: aquel proyecta
 * datos de la persona; este responde sobre autorización, y conviene que quien busque «quién decide
 * si alguien porta un permiso» encuentre un solo archivo al lado de {@code
 * JpaEffectivePermissions}.
 */
@Repository
public class JpaPermissionHolderLookup implements PermissionHolderLookup {

  private final EntityManager em;

  public JpaPermissionHolderLookup(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean holds(UUID userId, String permissionCode) {
    if (userId == null || permissionCode == null || permissionCode.isBlank()) {
      return false;
    }
    return !em.createNativeQuery(
            "SELECT 1 "
                + JpaEffectivePermissions.ROLES_QUE_CONCEDEN
                + "   AND p.code = :permiso LIMIT 1")
        .setParameter("usuario", userId)
        .setParameter("permiso", permissionCode.trim())
        .getResultList()
        .isEmpty();
  }
}
