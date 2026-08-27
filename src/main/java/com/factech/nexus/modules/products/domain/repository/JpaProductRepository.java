package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.domain.models.Product;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
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

  private static RuntimeException traducir(PersistenceException fallo) {
    String restriccion = nombreDeRestriccion(fallo);
    if (UQ_CODIGO.equals(restriccion)) {
      return duplicado("code", "Ya existe un producto con ese código.");
    }
    if (UQ_NOMBRE.equals(restriccion)) {
      return duplicado("name", "Ya existe un producto con ese nombre.");
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
