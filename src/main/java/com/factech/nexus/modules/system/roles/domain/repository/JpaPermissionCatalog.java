package com.factech.nexus.modules.system.roles.domain.repository;

import com.factech.nexus.modules.system.permissions.application.PermissionItem;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de {@link PermissionCatalog} sobre la tabla {@code permissions} (`RF-SP-001` · `T-14`).
 *
 * <p><b>Proyecta directamente sobre el modelo de lectura</b> y no instancia la entidad {@code
 * Permission}: la consulta solo necesita seis columnas y devolver entidades gestionadas obligaría
 * al contexto de persistencia a seguirlas sin que nadie las modifique.
 *
 * <p><b>Una sola consulta para todo el conjunto.</b> Resolver los permisos de a uno produciría
 * tantas consultas como permisos declare el alta, que es el patrón N+1 en su forma más evitable.
 */
@Repository
public class JpaPermissionCatalog implements PermissionCatalog {

  private final EntityManager em;

  public JpaPermissionCatalog(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<PermissionItem> findAllById(Set<UUID> ids) {
    if (ids.isEmpty()) {
      // Un IN vacío es SQL inválido en varios motores y una consulta inútil en
      // todos. El alta sin permisos (`FA-001`) pasa por aquí en cada petición.
      return List.of();
    }
    return em.createQuery(
            """
            SELECT new com.factech.nexus.modules.system.permissions.application.PermissionItem(
                       p.id, p.code, p.resource, p.action, p.name, p.description)
              FROM Permission p
             WHERE p.id IN :ids
             ORDER BY p.code
            """,
            PermissionItem.class)
        .setParameter("ids", ids)
        .getResultList();
  }
}
