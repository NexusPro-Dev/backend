package com.factech.nexus.modules.system.users.domain.repository;

import com.factech.nexus.modules.system.roles.domain.models.RoleType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador del catálogo de roles para el alta de personas.
 *
 * <p><b>Los permisos de cada rol se traen agregados en la misma sentencia</b>, con un {@code
 * jsonb_agg} sobre la unión. La alternativa —una consulta por rol— convierte la verificación de
 * `RN-SEG-010` en tantas consultas como roles conceda el alta, y el techo de cien roles por
 * petición la haría cara justo en el caso que ese techo existe para acotar.
 */
@Repository
public class JpaRoleCatalog implements RoleCatalog {

  private final EntityManager em;

  public JpaRoleCatalog(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AssignableRole> findAllById(Set<UUID> ids) {
    if (ids.isEmpty()) {
      // Un IN vacío es SQL inválido en varios motores y una consulta inútil en
      // todos. El alta sin roles pasa por aquí en cada petición.
      return List.of();
    }
    return leer("SELECT " + PROYECCION + " FROM roles r WHERE r.id IN (:ids)", "ids", ids);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AssignableRole> findById(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return leer("SELECT " + PROYECCION + " FROM roles r WHERE r.id = :ids", "ids", id).stream()
        .findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public Set<UUID> roleIdsOf(UUID userId) {
    if (userId == null) {
      return Set.of();
    }
    List<?> filas =
        em.createNativeQuery("SELECT role_id FROM user_roles WHERE user_id = :usuario")
            .setParameter("usuario", userId)
            .getResultList();
    Set<UUID> roles = new LinkedHashSet<>();
    filas.forEach(fila -> roles.add((UUID) fila));
    return roles;
  }

  /**
   * Un rol «sirve» cuando no está eliminado y está activo.
   *
   * <p>Los dos casos comparten respuesta a propósito: distinguirlos le diría a quien pregunta qué
   * roles existen y en qué estado están, que es información que este endpoint no tiene por qué
   * revelar.
   */
  private static final String PROYECCION =
      """
      r.id                                        AS id,
      r.code                                      AS code,
      r.name                                      AS name,
      r.role_type                                 AS role_type,
      (r.deleted_at IS NULL AND r.status = 'ACTIVO') AS usable,
      r.parent_role_id                            AS parent_role_id,
      COALESCE(
        (SELECT jsonb_agg(p.code ORDER BY p.code)
           FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
          WHERE rp.role_id = r.id),
        '[]'::jsonb)::text                        AS permisos
      """;

  private List<AssignableRole> leer(String sql, String parametro, Object valor) {
    List<Tuple> filas =
        em.createNativeQuery(sql, Tuple.class).setParameter(parametro, valor).getResultList();

    List<AssignableRole> roles = new ArrayList<>(filas.size());
    for (Tuple fila : filas) {
      roles.add(
          new AssignableRole(
              (UUID) fila.get("id"),
              (String) fila.get("code"),
              (String) fila.get("name"),
              RoleType.valueOf((String) fila.get("role_type")),
              Boolean.TRUE.equals(fila.get("usable")),
              (UUID) fila.get("parent_role_id"),
              codigos((String) fila.get("permisos"))));
    }
    return roles;
  }

  /**
   * Convierte el arreglo JSON de códigos en un conjunto.
   *
   * <p>Se hace a mano y no con Jackson porque la forma es conocida y trivial —una lista de cadenas
   * sin escapes, porque un código de permiso solo admite letras, dígitos, dos puntos y guion— y
   * arrastrar un deserializador para esto añadiría una dependencia al adaptador sin ganar nada.
   */
  private static Set<String> codigos(String json) {
    if (json == null || json.isBlank() || "[]".equals(json)) {
      return Set.of();
    }
    Set<String> codigos = new LinkedHashSet<>();
    for (String bruto : json.substring(1, json.length() - 1).split(",")) {
      String limpio = bruto.trim().replace("\"", "");
      if (!limpio.isEmpty()) {
        codigos.add(limpio);
      }
    }
    return codigos;
  }
}
