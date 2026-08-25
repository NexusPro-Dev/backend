package com.factech.nexus.modules.system.roles.domain.repository;

import com.factech.nexus.modules.system.roles.domain.models.Role;
import com.factech.nexus.modules.system.roles.domain.models.RoleCode;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Adaptador JPA de {@link RoleRepository} (`RF-SP-001` · `T-13`).
 *
 * <p><b>La traducción del duplicado se decide por el NOMBRE de la restricción</b>, nunca por el
 * texto del mensaje del driver. El mensaje cambia entre versiones de PostgreSQL y del controlador
 * JDBC, y una comparación de cadenas sobre él es un defecto que aparece el día de una actualización
 * de infraestructura, lejos del código que lo causa.
 */
@Repository
public class JpaRoleRepository implements RoleRepository {

  private static final String UQ_CODIGO = "uq_roles_code";
  private static final String UQ_NOMBRE = "uq_roles_name";

  private final EntityManager em;

  public JpaRoleRepository(EntityManager em) {
    this.em = em;
  }

  /**
   * Persiste y <b>fuerza el volcado</b>.
   *
   * <p>El {@code flush} explícito no es decorativo: sin él, la violación del índice único saldría
   * al confirmar la transacción, fuera de este método y del {@code try}, y llegaría al manejador
   * global como un fallo no controlado — es decir, {@code 500} en lugar de {@code 409}. El caso
   * límite de `spec.md` §13 exige justo lo contrario: el alta concurrente produce un {@code 201} y
   * un {@code 409}, nunca un {@code 500}.
   */
  @Override
  public Role save(Role rol) {
    try {
      em.persist(rol);
      em.flush();
      return rol;
    } catch (PersistenceException fallo) {
      throw traducir(fallo, rol);
    }
  }

  @Override
  public Optional<Role> findById(UUID id) {
    return Optional.ofNullable(em.find(Role.class, id));
  }

  /**
   * {@code deletedAt IS NULL} en la condición, igual que en el índice parcial que la respalda.
   *
   * <p>Sin ese filtro, un rol eliminado bloquearía su código para siempre y `CA-SP-006` —reutilizar
   * el código de un rol eliminado— sería imposible.
   */
  @Override
  public boolean existsActiveCode(RoleCode code) {
    return hayAlguno(
        em.createQuery(
                "SELECT 1 FROM Role r WHERE r.code = :code AND r.deletedAt IS NULL", Integer.class)
            .setParameter("code", code));
  }

  @Override
  public boolean existsActiveName(String name) {
    return hayAlguno(
        em.createQuery(
                "SELECT 1 FROM Role r WHERE r.name = :name AND r.deletedAt IS NULL", Integer.class)
            .setParameter("name", name));
  }

  @Override
  public Optional<Role> findNotDeletedByIdForUpdate(UUID id) {
    // `PESSIMISTIC_WRITE` y no una versión optimista: las seis escrituras leen,
    // deciden y escriben, y el rechazo de una carrera debe llegar como espera y
    // no como un 409 que el actor no provocó. `RF-SP-004` §14 acepta que en la
    // edición gane el último, y este bloqueo no lo contradice: serializa las dos
    // ediciones en lugar de mezclarlas.
    return em
        .createQuery("SELECT r FROM Role r WHERE r.id = :id AND r.deletedAt IS NULL", Role.class)
        .setParameter("id", id)
        .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public boolean existsActiveNameForOther(String name, UUID roleId) {
    return hayAlguno(
        em.createQuery(
                "SELECT 1 FROM Role r WHERE r.name = :name AND r.id <> :id"
                    + " AND r.deletedAt IS NULL",
                Integer.class)
            .setParameter("name", name)
            .setParameter("id", roleId));
  }

  /**
   * Lee {@code user_roles} con SQL nativo y no por una asociación.
   *
   * <p>La tabla pertenece a `SP` igual que {@code roles} —el módulo `USR` se retiró—, de modo que
   * leerla desde aquí no cruza ninguna frontera (`architecture.md` §5.3). Lo que sí se evita es
   * mapearla como asociación del agregado: {@code Role} no debe conocer a quién se le asignó, o
   * cualquier lectura del rol arrastraría la lista de sus portadores.
   */
  @Override
  public boolean isAssignedTo(UUID roleId, UUID userId) {
    if (userId == null) {
      // Sin identidad probada no hay rol propio que proteger. Ocurre en
      // migraciones y tareas programadas, que no pasan por la API.
      return false;
    }
    return !em.createNativeQuery(
            "SELECT 1 FROM user_roles WHERE role_id = :rol AND user_id = :persona LIMIT 1")
        .setParameter("rol", roleId)
        .setParameter("persona", userId)
        .getResultList()
        .isEmpty();
  }

  @Override
  public List<String> childCodesOf(UUID roleId) {
    return em
        .createQuery(
            "SELECT r.code FROM Role r WHERE r.parentRoleId = :padre AND r.deletedAt IS NULL"
                + " ORDER BY r.code",
            RoleCode.class)
        .setParameter("padre", roleId)
        .getResultList()
        .stream()
        .map(RoleCode::value)
        .toList();
  }

  @Override
  public long countAssignedUsers(UUID roleId) {
    // DISTINCT aunque la clave primaria de `user_roles` ya deba impedir el
    // duplicado: el conteo no depende de que esa restricción exista.
    Object total =
        em.createNativeQuery(
                """
                SELECT count(DISTINCT ur.user_id)
                  FROM user_roles ur
                  JOIN users u ON u.id = ur.user_id AND u.deleted_at IS NULL
                 WHERE ur.role_id = :rol
                """)
            .setParameter("rol", roleId)
            .getSingleResult();
    return ((Number) total).longValue();
  }

  /**
   * Recorrido de la descendencia con {@code WITH RECURSIVE} y <b>profundidad acotada</b>.
   *
   * <p>El tope no es defensa contra jerarquías profundas —el catálogo tiene cuatro niveles— sino
   * contra una <b>ya corrupta</b>: un ciclo introducido por fuera de la API haría que el recorrido
   * no terminara nunca, y el síntoma sería una petición colgada en lugar de un error. Con el tope,
   * el peor caso es un rechazo.
   */
  @Override
  public boolean isSelfOrDescendant(UUID candidato, UUID roleId) {
    if (candidato.equals(roleId)) {
      return true;
    }
    return !em.createNativeQuery(
            """
            WITH RECURSIVE descendencia(id, nivel) AS (
                SELECT r.id, 1 FROM roles r WHERE r.parent_role_id = :raiz
                UNION ALL
                SELECT h.id, d.nivel + 1
                  FROM roles h
                  JOIN descendencia d ON h.parent_role_id = d.id
                 WHERE d.nivel < 50
            )
            SELECT 1 FROM descendencia WHERE id = :candidato LIMIT 1
            """)
        .setParameter("raiz", roleId)
        .setParameter("candidato", candidato)
        .getResultList()
        .isEmpty();
  }

  @Override
  public List<PermissionHolder> childrenDeclaring(UUID roleId, Set<UUID> permissionIds) {
    if (permissionIds.isEmpty()) {
      return List.of();
    }
    List<jakarta.persistence.Tuple> filas =
        em.createNativeQuery(
                """
                SELECT h.code AS rol, p.code AS permiso
                  FROM roles h
                  JOIN role_permissions rp ON rp.role_id = h.id
                  JOIN permissions p       ON p.id = rp.permission_id
                 WHERE h.parent_role_id = :padre
                   AND h.deleted_at IS NULL
                   AND rp.permission_id IN (:permisos)
                 ORDER BY h.code, p.code
                """,
                jakarta.persistence.Tuple.class)
            .setParameter("padre", roleId)
            .setParameter("permisos", permissionIds)
            .getResultList();

    return filas.stream()
        .map(fila -> new PermissionHolder((String) fila.get("rol"), (String) fila.get("permiso")))
        .toList();
  }

  /** {@code setMaxResults(1)}: la pregunta es si existe alguno, no cuántos hay. */
  private static boolean hayAlguno(jakarta.persistence.TypedQuery<Integer> consulta) {
    return !consulta.setMaxResults(1).getResultList().isEmpty();
  }

  /**
   * Convierte la violación de integridad en el rechazo que la especificación declara.
   *
   * <p>Si no es una violación de restricción reconocida, se relanza tal cual: convertir cualquier
   * fallo de persistencia en un {@code 409} escondería defectos reales detrás de un mensaje de
   * duplicado.
   */
  private static RuntimeException traducir(PersistenceException fallo, Role rol) {
    String restriccion = nombreDeRestriccion(fallo);
    if (UQ_CODIGO.equals(restriccion)) {
      return new BusinessRuleException(
          "RN-SEG-001",
          "Ya existe un rol con ese código.",
          List.of(new FieldError("code", "RN-SEG-001", "Ya existe un rol con ese código.")));
    }
    if (UQ_NOMBRE.equals(restriccion)) {
      return new BusinessRuleException(
          "RN-SEG-001",
          "Ya existe un rol con ese nombre.",
          List.of(new FieldError("name", "RN-SEG-001", "Ya existe un rol con ese nombre.")));
    }
    return fallo;
  }

  private static String nombreDeRestriccion(Throwable fallo) {
    for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
      if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion) {
        return violacion.getConstraintName();
      }
    }
    return null;
  }
}
