package com.factech.nexus.modules.system.roles.domain.repository;

import com.factech.nexus.modules.system.roles.domain.models.Role;
import com.factech.nexus.modules.system.roles.domain.models.RoleCode;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
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
