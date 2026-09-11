package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.UserRateProduct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador JPA de {@link UserRateProductRepository}. */
@Repository
public class JpaUserRateProductRepository implements UserRateProductRepository {

  private final EntityManager em;

  public JpaUserRateProductRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional
  public void save(UserRateProduct asociacion) {
    em.persist(asociacion);
    // VOLCADO EXPLÍCITO: la violación de la clave primaria —asociar dos veces lo
    // mismo— tiene que salir DENTRO de la transacción del caso de uso para poder
    // traducirse, y no en el `commit`, fuera de todo try.
    em.flush();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existe(UUID rateId, UUID productId) {
    return em.find(UserRateProduct.class, new UserRateProduct.Key(rateId, productId)) != null;
  }

  @Override
  @Transactional
  public void borrar(UUID rateId, UUID productId) {
    UserRateProduct asociacion =
        em.find(UserRateProduct.class, new UserRateProduct.Key(rateId, productId));
    if (asociacion != null) {
      em.remove(asociacion);
      em.flush();
    }
  }

  @Override
  @Transactional(readOnly = true)
  public boolean tieneAsociaciones(UUID rateId) {
    Number cuantas =
        (Number)
            em.createNativeQuery(
                    "SELECT count(*) FROM user_commission_rate_products"
                        + " WHERE user_commission_rate_id = :tasa")
                .setParameter("tasa", rateId)
                .getSingleResult();
    return cuantas.intValue() > 0;
  }

  @Override
  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public List<UUID> productosDe(UUID rateId) {
    return em.createNativeQuery(
            "SELECT product_id FROM user_commission_rate_products"
                + " WHERE user_commission_rate_id = :tasa")
        .setParameter("tasa", rateId)
        .getResultList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProductoAsociado> asociadosDe(UUID rateId) {
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT p.id AS id, p.code AS code, p.name AS name
                  FROM user_commission_rate_products a
                  JOIN products p ON p.id = a.product_id
                 WHERE a.user_commission_rate_id = :tasa
                 ORDER BY p.code
                """,
                Tuple.class)
            .setParameter("tasa", rateId)
            .getResultList();

    return filas.stream()
        .map(
            fila ->
                new ProductoAsociado(
                    (UUID) fila.get("id"), (String) fila.get("code"), (String) fila.get("name")))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean haySolape(
      UUID userId, UUID productId, UUID excluida, LocalDate validFrom, LocalDate validTo) {

    // `daterange` con '[]' incluye los dos extremos: si una termina el 30, la
    // siguiente no puede empezar el 30. Es la misma expresión que el `EXCLUDE`
    // usaba antes de `V85`, y conservarla igual es lo que hace que la regla no
    // cambie de significado al cambiar de sitio.
    List<?> filas =
        em.createNativeQuery(
                """
                SELECT 1
                  FROM user_commission_rates t
                  JOIN user_commission_rate_products a
                    ON a.user_commission_rate_id = t.id
                 WHERE t.deleted_at IS NULL
                   AND t.user_id = :persona
                   AND a.product_id = :producto
                   AND (CAST(:excluida AS uuid) IS NULL OR t.id <> CAST(:excluida AS uuid))
                   AND daterange(t.valid_from, t.valid_to, '[]')
                       && daterange(CAST(:desde AS date), CAST(:hasta AS date), '[]')
                 LIMIT 1
                """)
            .setParameter("persona", userId)
            .setParameter("producto", productId)
            .setParameter("excluida", excluida == null ? null : excluida.toString())
            .setParameter("desde", validFrom.toString())
            .setParameter("hasta", validTo == null ? null : validTo.toString())
            .getResultList();

    return !filas.isEmpty();
  }
}
