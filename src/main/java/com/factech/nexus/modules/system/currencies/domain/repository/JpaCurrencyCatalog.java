package com.factech.nexus.modules.system.currencies.domain.repository;

import com.factech.nexus.modules.system.currencies.application.CurrencyCatalog;
import com.factech.nexus.modules.system.currencies.domain.models.Currency;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de {@link CurrencyCatalog} (**D-25**).
 *
 * <p>Vive en `SP`, junto a la tabla que lee, y proyecta a {@code CurrencyView} antes de devolver:
 * la entidad no sale del módulo.
 */
@Repository
public class JpaCurrencyCatalog implements CurrencyCatalog {

  private final EntityManager em;

  public JpaCurrencyCatalog(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CurrencyView> find(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery("SELECT c FROM Currency c WHERE c.id = :id", Currency.class)
        .setParameter("id", id)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst()
        .map(
            moneda ->
                new CurrencyView(
                    moneda.getId(),
                    moneda.getCode(),
                    moneda.getDecimalPlaces(),
                    moneda.isActive()));
  }
}
