package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.domain.models.ProductImage;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaProductImageRepository implements ProductImageRepository {

  private final EntityManager em;

  public JpaProductImageRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public ProductImage save(ProductImage imagen) {
    em.persist(imagen);
    // Se vuelca en el acto: `products.cover_image_id` va a señalar esta fila en
    // la siguiente sentencia, y la clave foránea exige que exista antes.
    em.flush();
    return imagen;
  }

  @Override
  public Optional<ProductImage> findById(UUID id) {
    return id == null ? Optional.empty() : Optional.ofNullable(em.find(ProductImage.class, id));
  }

  @Override
  public void deleteById(UUID id) {
    // Una sentencia y sin cargar los bytes: `em.remove` obligaría a leer cinco
    // megas para borrarlos.
    em.createQuery("DELETE FROM ProductImage i WHERE i.id = :id")
        .setParameter("id", id)
        .executeUpdate();
  }
}
