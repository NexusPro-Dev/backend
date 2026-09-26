package com.factech.nexus.modules.academy.domain.repository;

import com.factech.nexus.modules.academy.domain.models.AcademyImage;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** {@link AcademyImageRepository} sobre JPA: {@code JpaProductImageRepository} con otra tabla. */
@Repository
public class JpaAcademyImageRepository implements AcademyImageRepository {

  private final EntityManager em;

  public JpaAcademyImageRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public AcademyImage save(AcademyImage imagen) {
    em.persist(imagen);
    em.flush();
    return imagen;
  }

  @Override
  public Optional<AcademyImage> findById(UUID id) {
    return id == null ? Optional.empty() : Optional.ofNullable(em.find(AcademyImage.class, id));
  }

  @Override
  public void deleteById(UUID id) {
    // Una sentencia y sin cargar los bytes.
    em.createQuery("DELETE FROM AcademyImage i WHERE i.id = :id")
        .setParameter("id", id)
        .executeUpdate();
  }
}
