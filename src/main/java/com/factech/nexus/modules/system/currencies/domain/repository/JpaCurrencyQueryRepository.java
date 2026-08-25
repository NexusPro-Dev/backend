package com.factech.nexus.modules.system.currencies.domain.repository;

import com.factech.nexus.modules.system.currencies.application.CurrencyItem;
import com.factech.nexus.modules.system.currencies.domain.models.Currency;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de consulta del catálogo (`RF-SP-019`).
 *
 * <p><b>Proyecta directamente sobre el modelo de lectura</b> y no instancia la entidad: la consulta
 * solo necesita siete columnas, y devolver entidades gestionadas obligaría al contexto de
 * persistencia a seguirlas sin que nadie las modifique. {@code Currency} se nombra aquí como
 * metamodelo, no como agregado.
 *
 * <p><b>Una sola sentencia</b>, sin subconsultas y sin cruces: el catálogo no se une a nada.
 */
@Repository
public class JpaCurrencyQueryRepository implements CurrencyQueryRepository {

  private final EntityManager em;

  public JpaCurrencyQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<CurrencyItem> findAll(boolean includeInactive) {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<CurrencyItem> criteria = cb.createQuery(CurrencyItem.class);
    Root<Currency> moneda = criteria.from(Currency.class);

    criteria.select(
        cb.construct(
            CurrencyItem.class,
            moneda.get("id"),
            moneda.get("code"),
            moneda.get("name"),
            moneda.get("symbol"),
            moneda.get("decimalPlaces"),
            moneda.get("isDefault"),
            moneda.get("isActive")));

    // El filtro se OMITE cuando se piden todas, en lugar de escribir
    // `isActive IN (true, false)`: una condición que siempre se cumple es ruido
    // en el plan de ejecución y en la lectura.
    if (!includeInactive) {
      criteria.where(cb.isTrue(moneda.get("isActive")));
    }
    criteria.orderBy(cb.asc(moneda.get("code")));

    return em.createQuery(criteria).getResultList().stream()
        // `char(3)` rellena con espacios al leerse. Se recorta aquí, en el único
        // punto por el que el código sale de la base de datos hacia la API.
        .map(
            item ->
                new CurrencyItem(
                    item.id(),
                    item.code() == null ? null : item.code().trim(),
                    item.name(),
                    item.symbol(),
                    item.decimalPlaces(),
                    item.isDefault(),
                    item.isActive()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countDefaultActive() {
    return em.createQuery(
            "SELECT count(c) FROM Currency c WHERE c.isDefault = true AND c.isActive = true",
            Long.class)
        .getSingleResult();
  }
}
