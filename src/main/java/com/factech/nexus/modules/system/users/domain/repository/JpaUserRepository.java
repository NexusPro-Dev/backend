package com.factech.nexus.modules.system.users.domain.repository;

import com.factech.nexus.modules.system.users.domain.models.Email;
import com.factech.nexus.modules.system.users.domain.models.User;
import com.factech.nexus.modules.system.users.domain.models.UserStatus;
import com.factech.nexus.modules.system.users.domain.models.Username;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Adaptador de escritura de personas (`RF-SP-024`). */
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

  /**
   * Compara sobre {@code lower(username)}, igual que el índice.
   *
   * <p>Si comparara el texto literal mientras el índice ignora la caja, el `409` legible se
   * convertiría en un fallo de integridad justo en el caso que más importa: {@code JPerez} frente a
   * {@code jperez}.
   */
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

  /**
   * <b>Ni eliminada ni inactiva.</b> `RN-SP-020` exige que el superior esté {@code ACTIVO}: una
   * persona dada de baja no puede quedar a cargo de nadie, porque `RN-SP-022` impide después
   * retirarle el acceso sin reasignar a su equipo — se crearía el bloqueo desde el propio alta.
   */
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

  /**
   * Sentencia nativa y no una entidad.
   *
   * <p>{@code user_memberships} es del agregado de asignación, no de este, y `RF-SP-032` será quien
   * la modele. El alta solo necesita escribir una fila en su misma transacción, y montar una
   * entidad para eso obligaría a mantener un mapeo que este requerimiento no vuelve a leer.
   *
   * <p>{@code ends_at} queda nula a propósito: la membresía que concede el alta es indefinida
   * (`spec.md` §6.1), y acotarla es una operación aparte.
   */
  @Override
  public void assignMembership(UUID userId, UUID membershipId, OffsetDateTime ahora) {
    em.createNativeQuery(
            """
            INSERT INTO user_memberships (user_id, membership_id, started_at, created_at, updated_at)
            VALUES (:usuario, :membresia, :ahora, :ahora, :ahora)
            """)
        .setParameter("usuario", userId)
        .setParameter("membresia", membershipId)
        .setParameter("ahora", ahora)
        .executeUpdate();
  }

  /** Misma razón que la anterior: la tabla es de `RF-SP-041`, que la modelará. */
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

  /**
   * Traduce por <b>nombre de restricción</b>, nunca por el texto del mensaje del driver.
   *
   * <p>El {@code error_code} es `RN-SP-016` y no `EX-001`: hay una regla con identificador propio,
   * y la convención de `development-guide.md` §7.2 dice que en ese caso el código es el de la
   * regla.
   */
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
