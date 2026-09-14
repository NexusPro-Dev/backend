package com.factech.nexus.modules.system.users.domain.repository;

import com.factech.nexus.modules.system.users.domain.models.DocumentIdentity;
import com.factech.nexus.modules.system.users.domain.models.Email;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.models.UserStatus;
import com.factech.nexus.modules.system.users.domain.models.Username;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Adaptador de persistencia de las personas y de su estructura. */
@Repository
public class JpaUserRepository implements UserRepository {

  private static final String UQ_USERNAME = "uq_users_username";
  private static final String UQ_EMAIL = "uq_users_email";

  private final EntityManager em;

  public JpaUserRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public User save(User usuario) {
    try {
      em.persist(usuario);
      em.flush();
      return usuario;
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public void flushChanges() {
    try {
      em.flush();
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public boolean existsUsername(Username username) {
    return !em.createQuery(
            "SELECT 1 FROM User u WHERE lower(u.username) = lower(:username)", Integer.class)
        .setParameter("username", username.value())
        .setMaxResults(1)
        .getResultList()
        .isEmpty();
  }

  @Override
  public boolean existsEmail(Email email) {
    return !em.createQuery("SELECT 1 FROM User u WHERE u.email = :email", Integer.class)
        .setParameter("email", email.value())
        .setMaxResults(1)
        .getResultList()
        .isEmpty();
  }

  @Override
  public boolean existsDocument(DocumentIdentity documento) {
    return existsDocumentForOther(documento, null);
  }

  @Override
  public boolean existsDocumentForOther(DocumentIdentity documento, UUID excepto) {
    if (documento == null || !documento.completa()) {
      return false;
    }
    // SIN filtro por `deleted_at`: `RN-SP-035` no libera el documento al
    // eliminar. Es la misma decisión que `uq_users_document`, que tampoco lo
    // lleva, y la simetría entre las dos es lo que hace que el mensaje y la
    // garantía digan lo mismo.
    String jpql =
        "SELECT 1 FROM User u WHERE u.documentTypeId = :tipo AND u.documentNumber = :numero"
            + (excepto == null ? "" : " AND u.id <> :excepto");

    var consulta =
        em.createQuery(jpql, Integer.class)
            .setParameter("tipo", documento.typeId())
            .setParameter("numero", documento.number());
    if (excepto != null) {
      consulta.setParameter("excepto", excepto);
    }
    return !consulta.setMaxResults(1).getResultList().isEmpty();
  }

  @Override
  public Optional<User> findUsableById(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery(
            "SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL AND u.status = :activo",
            User.class)
        .setParameter("id", id)
        .setParameter("activo", UserStatus.ACTIVO)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public Optional<User> findNotDeletedById(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL", User.class)
        .setParameter("id", id)
        .getResultList()
        .stream()
        .findFirst();
  }

  // ---------------------------------------------------------------------------
  // Roles
  // ---------------------------------------------------------------------------

  /**
   * `RF-SP-030` §2. El conflicto se declara esperado y no se traduce después.
   *
   * <p>Una a una y no en lote: con {@code ON CONFLICT DO NOTHING} el número de filas afectadas por
   * sentencia es la única forma de saber cuál se insertó de verdad, y un lote lo devolvería sumado.
   * Son como mucho cien, el límite que el propio contrato impone al cuerpo.
   */
  @Override
  public int addRoles(UUID userId, Collection<UUID> roleIds) {
    int insertadas = 0;
    for (UUID rol : roleIds) {
      // `role_type` SE TOMA DEL ROL Y NO DE QUIEN PIDE, con una subconsulta en
      // la propia inserción. Es una copia (`RN-SP-025`, `V52`) y la clave
      // foránea compuesta impide que mienta — pero si el valor lo aportara el
      // caso de uso, la FK rechazaría la fila y el fallo saldría como `500` en
      // lugar de ser imposible de cometer.
      insertadas +=
          em.createNativeQuery(
                  """
                  INSERT INTO user_roles (user_id, role_id, role_type)
                  SELECT :usuario, r.id, r.role_type FROM roles r WHERE r.id = :rol
                  ON CONFLICT (user_id, role_id) DO NOTHING
                  """)
              .setParameter("usuario", userId)
              .setParameter("rol", rol)
              .executeUpdate();
    }
    return insertadas;
  }

  @Override
  public int removeRoles(UUID userId, Collection<UUID> roleIds) {
    if (roleIds.isEmpty()) {
      return 0;
    }
    return em.createNativeQuery(
            "DELETE FROM user_roles WHERE user_id = :usuario AND role_id IN (:roles)")
        .setParameter("usuario", userId)
        .setParameter("roles", roleIds)
        .executeUpdate();
  }

  // ---------------------------------------------------------------------------
  // Membresía
  // ---------------------------------------------------------------------------

  @Override
  public void assignMembership(
      UUID id, UUID userId, UUID membershipId, OffsetDateTime endsAt, OffsetDateTime ahora) {
    // 1. CIERRA LA ABIERTA, SIN CONDICIÓN DE VIGENCIA. Cierra también la vencida:
    //    dejarla abierta produciría dos filas actuales, que `uq_user_memberships_abierta`
    //    rechaza y que antes de eso ya habría hecho que el listado de usuarios
    //    devolviera a esa persona dos veces.
    cerrarMembresiaAbierta(userId, ahora);

    // 2. ABRE LA NUEVA. `started_at` y el cierre de arriba comparten instante, y por
    //    eso `ck_user_memberships_cierre` admite la igualdad.
    em.createNativeQuery(
            """
            INSERT INTO user_memberships
                   (id, user_id, membership_id, started_at, ends_at, created_at, updated_at)
            VALUES (:id, :usuario, :membresia, :ahora, :fin, :ahora, :ahora)
            """)
        .setParameter("id", id)
        .setParameter("usuario", userId)
        .setParameter("membresia", membershipId)
        .setParameter("fin", endsAt)
        .setParameter("ahora", ahora)
        .executeUpdate();
  }

  @Override
  public void updateMembershipEnd(UUID userId, OffsetDateTime endsAt, OffsetDateTime ahora) {
    em.createNativeQuery(
            """
            UPDATE user_memberships
               SET ends_at = :fin, updated_at = :ahora
             WHERE user_id = :usuario AND closed_at IS NULL
            """)
        .setParameter("fin", endsAt)
        .setParameter("ahora", ahora)
        .setParameter("usuario", userId)
        .executeUpdate();
  }

  @Override
  public Optional<UserMembership> findMembership(UUID userId) {
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT m.id AS id, m.code AS code, m.name AS name, m.level AS level,
                       m.color AS color,
                       um.ends_at AS ends_at
                  FROM user_memberships um
                  JOIN memberships m ON m.id = um.membership_id
                 WHERE um.user_id = :usuario AND um.closed_at IS NULL
                """,
                Tuple.class)
            .setParameter("usuario", userId)
            .getResultList();

    return filas.stream()
        .map(
            fila ->
                new UserMembership(
                    (UUID) fila.get("id"),
                    (String) fila.get("code"),
                    (String) fila.get("name"),
                    ((Number) fila.get("level")).shortValue(),
                    (String) fila.get("color"),
                    momento(fila.get("ends_at"))))
        .findFirst();
  }

  @Override
  public void closeMembership(UUID userId, OffsetDateTime ahora) {
    cerrarMembresiaAbierta(userId, ahora);
  }

  /**
   * El cierre, escrito una vez.
   *
   * <p>Lo comparten conceder —que cierra antes de abrir— y retirar. <b>No toca {@code ends_at}</b>:
   * la fila cerrada sigue diciendo hasta cuándo se había pagado.
   */
  private void cerrarMembresiaAbierta(UUID userId, OffsetDateTime ahora) {
    em.createNativeQuery(
            """
            UPDATE user_memberships
               SET closed_at = :ahora, updated_at = :ahora
             WHERE user_id = :usuario AND closed_at IS NULL
            """)
        .setParameter("ahora", ahora)
        .setParameter("usuario", userId)
        .executeUpdate();
  }

  // ---------------------------------------------------------------------------
  // Superior comercial
  // ---------------------------------------------------------------------------

  @Override
  public void assignSupervisor(UUID id, UUID userId, UUID supervisorId, OffsetDateTime ahora) {
    em.createNativeQuery(
            """
            INSERT INTO user_supervisors (id, user_id, supervisor_id, started_at, created_at, updated_at)
            VALUES (:id, :usuario, :superior, :ahora, :ahora, :ahora)
            """)
        .setParameter("id", id)
        .setParameter("usuario", userId)
        .setParameter("superior", supervisorId)
        .setParameter("ahora", ahora)
        .executeUpdate();
  }

  @Override
  public Optional<UserSupervisor> findActiveSupervisor(UUID userId) {
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT s.id AS id, s.username AS username,
                       s.first_name AS first_name, s.last_name AS last_name,
                       s.status AS status, us.started_at AS since,
                       (SELECT r.code
                          FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                         WHERE ur.user_id = s.id AND r.role_type = 'VENDEDOR'
                         ORDER BY r.code
                         LIMIT 1) AS role_code
                  FROM user_supervisors us
                  JOIN users s ON s.id = us.supervisor_id
                 WHERE us.user_id = :usuario AND us.ended_at IS NULL
                """,
                Tuple.class)
            .setParameter("usuario", userId)
            .getResultList();

    return filas.stream()
        .map(
            fila ->
                new UserSupervisor(
                    (UUID) fila.get("id"),
                    (String) fila.get("username"),
                    (String) fila.get("first_name"),
                    (String) fila.get("last_name"),
                    (String) fila.get("role_code"),
                    (String) fila.get("status"),
                    momento(fila.get("since"))))
        .findFirst();
  }

  @Override
  public void endSupervisor(UUID userId, OffsetDateTime ahora) {
    em.createNativeQuery(
            """
            UPDATE user_supervisors
               SET ended_at = :ahora, updated_at = :ahora
             WHERE user_id = :usuario AND ended_at IS NULL
            """)
        .setParameter("usuario", userId)
        .setParameter("ahora", ahora)
        .executeUpdate();
  }

  /**
   * `RN-SP-022` y `RF-SP-042`. Cuenta a los <b>no eliminados</b>, activos o no.
   *
   * <p>La primera versión exigía además {@code status = 'ACTIVO'}, con el argumento de que una
   * cuenta suspendida «no es equipo de nadie». Se corrigió el 24-08-2026 al implementar
   * `RF-SP-042`: su `CA-SP-447` exige que <b>este número y el que devuelve el equipo sean el
   * mismo</b>, y el equipo sí incluye a los inactivos. Con dos criterios distintos, las dos
   * operaciones dirían cosas distintas del mismo equipo — y quien recibiera el rechazo de
   * `RN-SP-022` no encontraría a las personas que el número le atribuye.
   *
   * <p>Además, el argumento original no se sostiene: una cuenta suspendida <b>sigue teniendo</b> un
   * superior al que hay que reasignarla, y volverá.
   */
  @Override
  public int countSupervisees(UUID supervisorId) {
    return countTeam(supervisorId, List.of());
  }

  @Override
  public int countTeam(UUID supervisorId, List<String> roleCodes) {
    Query consulta =
        em.createNativeQuery(
            """
            SELECT count(*)
              FROM user_supervisors us
              JOIN users u ON u.id = us.user_id
             WHERE us.supervisor_id = :superior
               AND us.ended_at IS NULL
               AND u.deleted_at IS NULL
            """
                + filtroDeRoles(roleCodes));
    consulta.setParameter("superior", supervisorId);
    enlazarRoles(consulta, roleCodes);

    return ((Number) consulta.getSingleResult()).intValue();
  }

  /**
   * El equipo directo, acotado por códigos de rol cuando vienen.
   *
   * <p><b>Ya no resuelve el rol de cada persona</b> (10-09-2026). Lo hacía con una subconsulta que
   * tomaba el primer rol de clasificación {@code VENDEDOR} por orden alfabético, y eso fallaba en
   * las dos direcciones: devolvía <b>nulo</b> para la cartera de clientes —que cuelga de esta misma
   * estructura desde `RF-SP-045`— y elegía {@code AGENTE} sobre {@code DIRECTOR} para quien portara
   * los dos, pese a que el javadoc afirmaba resolverlo «por nivel de profundidad». Los roles los
   * trae ahora {@code UserQueryRepository.rolesOf}, completos y por lote.
   *
   * <p>El orden desempata por {@code username} y no solo por apellido: sin un desempate único, dos
   * páginas consecutivas pueden repetir a alguien y omitir a otro sin que nada falle.
   */
  @Override
  public List<TeamMember> findTeam(
      UUID supervisorId, List<String> roleCodes, int offset, int limit) {
    Query consulta =
        em.createNativeQuery(
            """
            SELECT u.id AS id, u.username AS username,
                   u.first_name AS first_name, u.last_name AS last_name,
                   u.status AS status
              FROM user_supervisors us
              JOIN users u ON u.id = us.user_id
             WHERE us.supervisor_id = :superior
               AND us.ended_at IS NULL
               AND u.deleted_at IS NULL
            """
                + filtroDeRoles(roleCodes)
                + """
                 ORDER BY u.last_name, u.first_name, u.username
                 OFFSET :salto LIMIT :tope
                """,
            Tuple.class);
    consulta.setParameter("superior", supervisorId);
    consulta.setParameter("salto", offset);
    consulta.setParameter("tope", limit);
    enlazarRoles(consulta, roleCodes);

    @SuppressWarnings("unchecked")
    List<Tuple> filas = consulta.getResultList();

    return filas.stream()
        .map(
            fila ->
                new TeamMember(
                    (UUID) fila.get("id"),
                    (String) fila.get("username"),
                    (String) fila.get("first_name"),
                    (String) fila.get("last_name"),
                    (String) fila.get("status")))
        .toList();
  }

  /**
   * El predicado del filtro por rol, o nada cuando no hay filtro.
   *
   * <p><b>{@code EXISTS} y no {@code JOIN}</b>, que es la misma lección que `RF-SP-025` dejó
   * escrita en su predicado: un {@code JOIN} a {@code user_roles} multiplica la fila de la persona
   * por cada rol suyo que case, de modo que quien porte dos de los códigos pedidos <b>saldría dos
   * veces</b> y el conteo contaría asignaciones en lugar de personas. Con un solo código el
   * resultado parece correcto, y por eso el fallo no aparece hasta que alguien filtra por dos.
   *
   * <p><b>Sin `r.deleted_at IS NULL`</b>: un rol eliminado no lo porta nadie —`RF-SP-009` retira
   * las asignaciones—, y añadir la condición sugeriría que el caso existe.
   */
  private static String filtroDeRoles(List<String> roleCodes) {
    return roleCodes == null || roleCodes.isEmpty()
        ? ""
        : """
           AND EXISTS (SELECT 1
                         FROM user_roles ur
                         JOIN roles r ON r.id = ur.role_id
                        WHERE ur.user_id = u.id AND r.code IN (:roles))
          """;
  }

  private static void enlazarRoles(Query consulta, List<String> roleCodes) {
    if (roleCodes != null && !roleCodes.isEmpty()) {
      consulta.setParameter("roles", roleCodes);
    }
  }

  @Override
  public Optional<User> findNotDeletedByIdForUpdate(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL", User.class)
        .setParameter("id", id)
        .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        .getResultList()
        .stream()
        .findFirst();
  }

  // ---------------------------------------------------------------------------
  // Estado y eliminación
  // ---------------------------------------------------------------------------

  @Override
  public Optional<OffsetDateTime> lockedUntilOf(UUID userId) {
    List<?> filas =
        em.createNativeQuery("SELECT locked_until FROM users WHERE id = :id")
            .setParameter("id", userId)
            .getResultList();

    // `findFirst` sobre un flujo cuyo primer elemento es NULO lanza
    // `NullPointerException`, y aquí el nulo es el caso normal: significa que la
    // cuenta no está bloqueada. Se comprueba la lista antes de mirar dentro.
    return filas.isEmpty() ? Optional.empty() : Optional.ofNullable(momento(filas.get(0)));
  }

  @Override
  public void applyStatus(UUID userId, String estado, boolean limpiarAcceso, OffsetDateTime ahora) {
    em.createNativeQuery(
            """
            UPDATE users
               SET status = :estado,
                   updated_at = :ahora,
                   locked_until = CASE WHEN :limpiar THEN NULL ELSE locked_until END,
                   failed_attempts = CASE WHEN :limpiar THEN 0 ELSE failed_attempts END
             WHERE id = :id
            """)
        .setParameter("estado", estado)
        .setParameter("limpiar", limpiarAcceso)
        .setParameter("ahora", ahora)
        .setParameter("id", userId)
        .executeUpdate();
  }

  @Override
  public void markDeleted(UUID userId, OffsetDateTime ahora) {
    em.createNativeQuery("UPDATE users SET deleted_at = :ahora, updated_at = :ahora WHERE id = :id")
        .setParameter("ahora", ahora)
        .setParameter("id", userId)
        .executeUpdate();
  }

  @Override
  public int removeAllRoles(UUID userId) {
    return em.createNativeQuery("DELETE FROM user_roles WHERE user_id = :usuario")
        .setParameter("usuario", userId)
        .executeUpdate();
  }

  // ---------------------------------------------------------------------------

  /** Misma conversión que en el módulo de sesión: una consulta nativa entrega {@link Instant}. */
  private static OffsetDateTime momento(Object valor) {
    return switch (valor) {
      case null -> null;
      case OffsetDateTime instante -> instante;
      case Instant instante -> instante.atOffset(ZoneOffset.UTC);
      case Timestamp marca -> marca.toInstant().atOffset(ZoneOffset.UTC);
      default ->
          throw new IllegalStateException(
              "Tipo temporal inesperado en la proyección: " + valor.getClass());
    };
  }

  private static RuntimeException traducir(PersistenceException fallo) {
    String restriccion = nombreDeRestriccion(fallo);
    if (UQ_USERNAME.equals(restriccion)) {
      String mensaje = "Ese nombre de usuario ya está en uso.";
      return new BusinessRuleException(
          "RN-SP-016", mensaje, List.of(new FieldError("username", "RN-SP-016", mensaje)));
    }
    if (UQ_EMAIL.equals(restriccion)) {
      String mensaje = "Ese correo ya está en uso.";
      return new BusinessRuleException(
          "RN-SP-016", mensaje, List.of(new FieldError("email", "RN-SP-016", mensaje)));
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
