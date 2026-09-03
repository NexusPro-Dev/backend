package com.factech.nexus.modules.products.domain.repository;

import com.factech.nexus.modules.products.application.ProductCatalog;
import com.factech.nexus.modules.products.domain.models.Product;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de la interfaz que `PM` <b>publica</b> hacia otros módulos (**D-25**).
 *
 * <p>Se llama {@code Published…} por el mismo motivo que sus equivalentes en `SP`: este es el
 * contrato que <b>cruza la frontera del módulo</b>, distinto de los repositorios internos.
 *
 * <p><b>No filtra por retirado</b>, y devuelve la marca. Es lo que permite a `CM` rechazar declarar
 * una tarifa nueva sobre un producto retirado (`RN-CM-010`) y a la vez <b>resolver con
 * normalidad</b> sobre él (`RF-CM-005`): preguntar qué se pagaba por algo que ya no se vende es
 * legítimo.
 */
@Repository
public class PublishedProductCatalog implements ProductCatalog {

  private final EntityManager em;

  public PublishedProductCatalog(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ProductView> find(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery("SELECT p FROM Product p WHERE p.id = :id", Product.class)
        .setParameter("id", id)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst()
        .map(
            producto ->
                new ProductView(
                    producto.getId(),
                    producto.getCode(),
                    producto.getName(),
                    producto.estaRetirado()));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<BigDecimal> findPrice(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery("SELECT p.price FROM Product p WHERE p.id = :id", BigDecimal.class)
        .setParameter("id", id)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }
}
