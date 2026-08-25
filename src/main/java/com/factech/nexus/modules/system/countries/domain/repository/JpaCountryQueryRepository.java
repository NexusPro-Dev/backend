package com.factech.nexus.modules.system.countries.domain.repository;

import com.factech.nexus.modules.system.countries.application.CountryItem;
import com.factech.nexus.modules.system.countries.domain.models.Country;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de consulta del catálogo de países (`RF-SP-021`).
 *
 * <p><b>El orden alfabético no aparece en esta clase, y es deliberado.</b> {@code ORDER BY name} a
 * secas ordena bien porque la <b>columna</b> declara la intercalación {@code es-x-icu}
 * (`V16__create_countries.sql`). La API de criterios no puede expresar {@code COLLATE} —tampoco
 * Hibernate 6—, de modo que si el orden dependiera de la sentencia habría que abandonarla por una
 * consulta nativa. Con la intercalación en la columna, el orden correcto es el comportamiento por
 * omisión.
 */
@Repository
public class JpaCountryQueryRepository implements CountryQueryRepository {

  private static final String PARAMETRO_BUSQUEDA = "termino";

  /**
   * Carácter de escape del {@code LIKE}, declarado explícito.
   *
   * <p>En PostgreSQL coincide con el de por omisión, pero dejarlo implícito hace que el escape
   * dependa de una configuración del motor en lugar de la consulta. Sin él, un término con {@code
   * %} devolvería el catálogo entero.
   */
  private static final char ESCAPE = '\\';

  private final EntityManager em;

  public JpaCountryQueryRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public List<CountryItem> findAll(String search, boolean includeInactive) {
    String termino = normalizar(search);

    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<CountryItem> criteria = cb.createQuery(CountryItem.class);
    Root<Country> pais = criteria.from(Country.class);

    criteria.select(
        cb.construct(
            CountryItem.class,
            pais.get("id"),
            pais.get("code"),
            pais.get("name"),
            pais.get("isActive")));

    List<Predicate> filtros = new ArrayList<>();

    // La búsqueda y el estado son INDEPENDIENTES: buscar «pan» sin pedir los
    // inactivos devuelve solo los activos que coinciden.
    if (!includeInactive) {
      filtros.add(cb.isTrue(pais.get("isActive")));
    }

    ParameterExpression<String> parametro = cb.parameter(String.class, PARAMETRO_BUSQUEDA);
    if (termino != null) {
      Expression<String> patron = normalizada(cb, parametro);
      filtros.add(
          cb.or(
              cb.like(normalizada(cb, pais.get("code")), patron, ESCAPE),
              cb.like(normalizada(cb, pais.get("name")), patron, ESCAPE)));
    }

    if (!filtros.isEmpty()) {
      criteria.where(cb.and(filtros.toArray(Predicate[]::new)));
    }
    criteria.orderBy(cb.asc(pais.get("name")));

    TypedQuery<CountryItem> consulta = em.createQuery(criteria);
    if (termino != null) {
      consulta.setParameter(PARAMETRO_BUSQUEDA, "%" + termino + "%");
    }

    return consulta.getResultList().stream()
        // `char(2)` rellena con espacios al leerse; se recorta en el único punto
        // por el que el código sale hacia la API.
        .map(
            item ->
                new CountryItem(
                    item.id(),
                    item.code() == null ? null : item.code().trim(),
                    item.name(),
                    item.isActive()))
        .toList();
  }

  /**
   * Se normaliza <b>el término y la columna</b>, no solo una de las dos.
   *
   * <p>Si solo se normalizara la columna, buscar «panama» no encontraría «Panamá», que es justo lo
   * que la comparación insensible a acentos existe para resolver.
   */
  private static Expression<String> normalizada(CriteriaBuilder cb, Expression<String> valor) {
    return cb.function("f_unaccent", String.class, cb.lower(valor));
  }

  /** En blanco equivale a ausente: un formulario que envía el campo vacío no pide un filtro. */
  private static String normalizar(String search) {
    if (search == null || search.isBlank()) {
      return null;
    }
    return search
        .trim()
        .toLowerCase(Locale.ROOT)
        .replace(String.valueOf(ESCAPE), ESCAPE + "\\")
        .replace("%", ESCAPE + "%")
        .replace("_", ESCAPE + "_");
  }
}
