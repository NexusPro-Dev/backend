package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.domain.models.PackageItem;
import com.factech.nexus.modules.products.domain.models.PackageItemId;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** {@link PackageItemRepository} sobre JPA. */
@Repository
public class JpaPackageItemRepository implements PackageItemRepository {

  private static final String PK = "pk_product_package_items";

  private final EntityManager em;

  public JpaPackageItemRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public PackageItem save(PackageItem fila) {
    try {
      em.persist(fila);
      em.flush();
      return fila;
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public Optional<PackageItem> find(UUID packageId, UUID productId) {
    if (packageId == null || productId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(em.find(PackageItem.class, new PackageItemId(packageId, productId)));
  }

  @Override
  public List<PackageItem> findByPackage(UUID packageId) {
    return em.createQuery(
            "SELECT i FROM PackageItem i WHERE i.id.packageId = :paquete"
                + " ORDER BY i.createdAt, i.id.productId",
            PackageItem.class)
        .setParameter("paquete", packageId)
        .getResultList();
  }

  @Override
  public List<Hermana> findSiblings(UUID packageId) {
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT i.product_id AS product_id, p.code AS code, p.type AS type,
                       p.source_membership_id AS source_membership_id, p.price AS price
                  FROM product_package_items i
                  JOIN products p ON p.id = i.product_id
                 WHERE i.package_id = :paquete
                 ORDER BY i.created_at, i.product_id
                """,
                Tuple.class)
            .setParameter("paquete", packageId)
            .getResultList();
    return filas.stream()
        .map(
            fila ->
                new Hermana(
                    (UUID) fila.get("product_id"),
                    (String) fila.get("code"),
                    (String) fila.get("type"),
                    (UUID) fila.get("source_membership_id"),
                    (BigDecimal) fila.get("price")))
        .toList();
  }

  @Override
  public void delete(PackageItem fila) {
    em.remove(fila);
    em.flush();
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
    for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
      if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion
          && PK.equals(violacion.getConstraintName())) {
        // El camino de la CARRERA: la comprobación previa sobre las hermanas no
        // vio la fila porque todavía no estaba. Responde lo mismo que ella.
        String mensaje =
            "Ese producto ya está en el paquete. Corrija su descuento en lugar de asociarlo de nuevo.";
        return new BusinessRuleException(
            "EX-005", mensaje, List.of(new FieldError("productId", "EX-005", mensaje)));
      }
    }
    return fallo;
  }
}
