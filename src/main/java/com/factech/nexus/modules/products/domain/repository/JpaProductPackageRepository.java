package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.domain.models.ProductPackage;
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
 * {@link ProductPackageRepository} sobre JPA.
 *
 * <p><b>La traducción de las dos unicidades es lo único que este repositorio decide</b>, y la
 * decide por el <b>nombre</b> de la restricción, como {@link JpaProductRepository}: {@code
 * uq_product_packages_code} es `EX-001` y {@code uq_product_packages_name} es `EX-002` de
 * `RF-PM-017`. Es el camino de la carrera; el normal es la comprobación previa del caso de uso, y
 * los dos responden lo mismo para que quien llama no note por cuál entró.
 */
@Repository
public class JpaProductPackageRepository implements ProductPackageRepository {

  private static final String UQ_CODIGO = "uq_product_packages_code";
  private static final String UQ_NOMBRE = "uq_product_packages_name";

  private final EntityManager em;

  public JpaProductPackageRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public boolean existsCode(String code) {
    return !em.createQuery("SELECT 1 FROM ProductPackage p WHERE p.code = :code", Integer.class)
        .setParameter("code", code)
        .setMaxResults(1)
        .getResultList()
        .isEmpty();
  }

  @Override
  public boolean existsAliveName(String name) {
    // El mismo predicado que `uq_product_packages_name`, a propósito: si esta
    // lectura mirara otra cosa que el índice, la comprobación previa y la
    // traducción de la carrera dejarían pasar casos distintos.
    return !em.createNativeQuery(
            """
            SELECT 1 FROM product_packages
             WHERE deleted_at IS NULL
               AND f_unaccent(lower(name)) = f_unaccent(lower(CAST(:name AS text)))
             LIMIT 1
            """)
        .setParameter("name", name)
        .getResultList()
        .isEmpty();
  }

  @Override
  public boolean existsAliveNameForOther(String name, UUID packageId) {
    return !em.createNativeQuery(
            """
            SELECT 1 FROM product_packages
             WHERE deleted_at IS NULL
               AND id <> CAST(:id AS uuid)
               AND f_unaccent(lower(name)) = f_unaccent(lower(CAST(:name AS text)))
             LIMIT 1
            """)
        .setParameter("name", name)
        .setParameter("id", packageId.toString())
        .getResultList()
        .isEmpty();
  }

  @Override
  public ProductPackage save(ProductPackage paquete) {
    try {
      em.persist(paquete);
      em.flush();
      return paquete;
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public Optional<ProductPackage> findAliveByIdForUpdate(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery(
            "SELECT p FROM ProductPackage p WHERE p.id = :id AND p.deletedAt IS NULL",
            ProductPackage.class)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public Optional<ProductPackage> findByIdForUpdate(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery("SELECT p FROM ProductPackage p WHERE p.id = :id", ProductPackage.class)
        .setParameter("id", id)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
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
      return duplicado("code", "EX-001", "Ya existe un paquete con ese código.");
    }
    if (UQ_NOMBRE.equals(restriccion)) {
      return duplicado("name", "EX-002", "Ya existe un paquete con ese nombre.");
    }
    return fallo;
  }

  private static BusinessRuleException duplicado(String campo, String codigo, String mensaje) {
    return new BusinessRuleException(
        codigo, mensaje, List.of(new FieldError(campo, codigo, mensaje)));
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
