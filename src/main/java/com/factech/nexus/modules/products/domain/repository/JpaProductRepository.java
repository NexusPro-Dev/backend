package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.domain.models.Product;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de {@link ProductRepository}.
 *
 * <p><b>Traduce por nombre de restricción, nunca por el texto del mensaje del driver</b>, que
 * cambia entre versiones de PostgreSQL y convertiría una traducción correcta en un {@code 500}
 * silencioso el día de una actualización. Es la regla que `SP` ya sigue en tres adaptadores.
 */
@Repository
public class JpaProductRepository implements ProductRepository {

  private static final String UQ_CODIGO = "uq_products_code";
  private static final String UQ_NOMBRE = "uq_products_name";
  private static final String UQ_UPGRADE_DESTINO = "uq_products_upgrade_target";

  private final EntityManager em;

  public JpaProductRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public boolean existsCode(String code) {
    return !em.createQuery("SELECT 1 FROM Product p WHERE p.code = :code", Integer.class)
        .setParameter("code", code)
        .setMaxResults(1)
        .getResultList()
        .isEmpty();
  }

  @Override
  public boolean existsAliveName(String name) {
    return !em.createNativeQuery(
            """
            SELECT 1 FROM products
             WHERE deleted_at IS NULL
               AND f_unaccent(lower(name)) = f_unaccent(lower(CAST(:name AS text)))
             LIMIT 1
            """)
        .setParameter("name", name)
        .getResultList()
        .isEmpty();
  }

  @Override
  public boolean existsAliveNameForOther(String name, UUID productId) {
    return !em.createNativeQuery(
            """
            SELECT 1 FROM products
             WHERE deleted_at IS NULL
               AND id <> CAST(:id AS uuid)
               AND f_unaccent(lower(name)) = f_unaccent(lower(CAST(:name AS text)))
             LIMIT 1
            """)
        .setParameter("name", name)
        .setParameter("id", productId == null ? null : productId.toString())
        .getResultList()
        .isEmpty();
  }

  /**
   * Persiste y fuerza el volcado.
   *
   * <p>El {@code flush} explícito es lo que permite traducir el duplicado a un {@code 409} legible:
   * sin él, la violación saltaría al confirmar, fuera de este método, y llegaría al manejador
   * global como un fallo no controlado.
   */
  @Override
  public Product save(Product producto) {
    try {
      em.persist(producto);
      em.flush();
      return producto;
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public Optional<Product> findAliveByIdForUpdate(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery(
            "SELECT p FROM Product p WHERE p.id = :id AND p.deletedAt IS NULL", Product.class)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public Optional<Product> findByIdForUpdate(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery("SELECT p FROM Product p WHERE p.id = :id", Product.class)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public Optional<Product> findActiveUpgradeFor(
      UUID sourceMembershipId, UUID targetMembershipId, UUID excluido) {
    if (sourceMembershipId == null || targetMembershipId == null) {
      return Optional.empty();
    }
    // Mismo predicado que `uq_products_upgrade_target`, y a propósito: si esta
    // lectura mirara un conjunto distinto del que el índice protege, el mensaje
    // nombraría a un producto que no es el que va a provocar el rechazo. El
    // índice es sobre la PAREJA (origen, destino), no solo el destino.
    return em
        .createQuery(
            """
            SELECT p FROM Product p
             WHERE p.sourceMembershipId = :origen
               AND p.targetMembershipId = :destino
               AND p.type = com.factech.nexus.modules.products.domain.models.ProductType
                            .UPGRADE_MEMBRESIA
               AND p.status = com.factech.nexus.modules.products.domain.models.ProductStatus.ACTIVO
               AND p.deletedAt IS NULL
               AND p.id <> :excluido
            """,
            Product.class)
        .setParameter("origen", sourceMembershipId)
        .setParameter("destino", targetMembershipId)
        .setParameter("excluido", excluido)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public void flush() {
    try {
      em.flush();
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  private static RuntimeException traducir(PersistenceException fallo) {
    String restriccion = nombreDeRestriccion(fallo);
    if (UQ_CODIGO.equals(restriccion)) {
      return duplicado("code", "Ya existe un producto con ese código.");
    }
    if (UQ_NOMBRE.equals(restriccion)) {
      return duplicado("name", "Ya existe un producto con ese nombre.");
    }
    if (UQ_UPGRADE_DESTINO.equals(restriccion)) {
      // Sin el nombre del producto que ocupa el destino, y es inevitable: la
      // verificación previa no vio a nadie —por eso llegamos hasta aquí— y
      // averiguarlo ahora exigiría una consulta dentro del fallo. Este es el
      // camino de la CARRERA, poco frecuente; el mensaje accionable lo da la
      // verificación previa, que es el camino normal.
      String mensaje = "Ya hay otro upgrade activo hacia esa membresía destino.";
      return new BusinessRuleException(
          "EX-002", mensaje, List.of(new FieldError("status", "EX-002", mensaje)));
    }
    return fallo;
  }

  private static BusinessRuleException duplicado(String campo, String mensaje) {
    return new BusinessRuleException(
        "EX-001", mensaje, List.of(new FieldError(campo, "EX-001", mensaje)));
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
