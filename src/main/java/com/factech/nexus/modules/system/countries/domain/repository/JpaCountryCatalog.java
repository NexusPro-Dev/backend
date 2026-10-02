package com.factech.nexus.modules.system.countries.domain.repository;

import com.factech.nexus.modules.system.countries.application.CountryCatalog;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de {@link CountryCatalog}: vive junto a la tabla que lee y proyecta antes de salir. */
@Repository
public class JpaCountryCatalog implements CountryCatalog {

  private final EntityManager em;

  public JpaCountryCatalog(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CountryView> find(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT id, code, name, is_active FROM countries WHERE id = :id", Tuple.class)
            .setParameter("id", id)
            .getResultList();
    return filas.stream()
        .findFirst()
        .map(
            f ->
                new CountryView(
                    (UUID) f.get("id"),
                    ((String) f.get("code")).trim(),
                    (String) f.get("name"),
                    (Boolean) f.get("is_active")));
  }
}
