package com.factech.nexus.modules.system.users.domain.repository;

import com.factech.nexus.shared.security.EffectivePermissions;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve los permisos efectivos recorriendo {@code user_roles → role_permissions → permissions}.
 *
 * <p><b>Solo cuentan los roles que sirven.</b> Un rol inactivo o eliminado deja de conceder: es lo
 * que hace que retirar el acceso sea inmediato, y la diferencia con leerlos del token —donde el rol
 * retirado sigue concediendo hasta que el token expire—.
 *
 * <p><b>Una sola sentencia.</b> Se ejecuta en cada operación que verifica `RN-SEG-010`, que son
 * pocas pero decisivas; resolverla por pasos multiplicaría las consultas sin ganar nada.
 */
@Repository
public class JpaEffectivePermissions implements EffectivePermissions {

  private final EntityManager em;

  public JpaEffectivePermissions(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Set<String>> forUser(UUID userId) {
    if (userId == null) {
      return Optional.empty();
    }

    // Primero: ¿existe la persona? Sin esta comprobación no se podría
    // distinguir «no tiene permisos» de «no existe», y quien pregunta necesita
    // separarlas.
    boolean existe =
        !em.createNativeQuery("SELECT 1 FROM users WHERE id = :usuario AND deleted_at IS NULL")
            .setParameter("usuario", userId)
            .getResultList()
            .isEmpty();
    if (!existe) {
      return Optional.empty();
    }
    List<?> filas =
        em.createNativeQuery(
                """
                SELECT DISTINCT p.code
                  FROM user_roles ur
                  JOIN roles r       ON r.id = ur.role_id
                  JOIN role_permissions rp ON rp.role_id = r.id
                  JOIN permissions p ON p.id = rp.permission_id
                  JOIN users u       ON u.id = ur.user_id
                 WHERE ur.user_id = :usuario
                   AND u.deleted_at IS NULL
                   AND r.deleted_at IS NULL
                   AND r.status = 'ACTIVO'
                """)
            .setParameter("usuario", userId)
            .getResultList();

    Set<String> codigos = new LinkedHashSet<>();
    filas.forEach(fila -> codigos.add((String) fila));
    return Optional.of(codigos);
  }
}
