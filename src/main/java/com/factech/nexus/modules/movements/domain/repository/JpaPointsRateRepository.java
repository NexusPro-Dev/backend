package com.factech.nexus.modules.movements.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de las tasas de puntos. SQL nativo, como {@link JpaMovementRepository}. */
@Repository
public class JpaPointsRateRepository implements PointsRateRepository {

  private final EntityManager em;

  public JpaPointsRateRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<RateRow> current(UUID currencyId, OffsetDateTime at) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT r.id AS id, c.id AS moneda, c.code AS codigo, r.points_per_unit AS valor,
                       r.valid_from AS desde
                  FROM points_rates r
                  JOIN currencies c ON c.id = r.currency_id
                 WHERE r.currency_id = :moneda AND r.valid_from <= :ahora
                 ORDER BY r.valid_from DESC
                 LIMIT 1
                """,
                Tuple.class)
            .setParameter("moneda", currencyId)
            .setParameter("ahora", at)
            .getResultList();
    return filas.stream().findFirst().map(JpaPointsRateRepository::fila);
  }

  @Override
  @Transactional(readOnly = true)
  public List<RateRow> currentOfActiveCurrencies(OffsetDateTime at) {
    // DISTINCT ON y no una consulta por moneda: `ix_points_rates_vigente` da la
    // primera fila de cada moneda ya ordenada.
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT v.id AS id, v.moneda AS moneda, v.codigo AS codigo, v.valor AS valor,
                       v.desde AS desde
                  FROM (SELECT DISTINCT ON (r.currency_id)
                               r.id AS id, c.id AS moneda, c.code AS codigo,
                               r.points_per_unit AS valor, r.valid_from AS desde
                          FROM points_rates r
                          JOIN currencies c ON c.id = r.currency_id
                         WHERE r.valid_from <= :ahora AND c.is_active
                         ORDER BY r.currency_id, r.valid_from DESC) v
                 ORDER BY v.codigo
                """,
                Tuple.class)
            .setParameter("ahora", at)
            .getResultList();
    return filas.stream().map(JpaPointsRateRepository::fila).toList();
  }

  @Override
  @Transactional
  public void insert(
      UUID id, UUID currencyId, BigDecimal pointsPerUnit, OffsetDateTime validFrom, UUID by) {
    em.createNativeQuery(
            """
            INSERT INTO points_rates (id, currency_id, points_per_unit, valid_from, created_by,
                                      created_at)
            VALUES (:id, :moneda, :valor, :desde, :quien, :desde)
            """)
        .setParameter("id", id)
        .setParameter("moneda", currencyId)
        .setParameter("valor", pointsPerUnit)
        .setParameter("desde", validFrom)
        .setParameter("quien", by)
        .executeUpdate();
  }

  private static RateRow fila(Tuple f) {
    return new RateRow(
        (UUID) f.get("id"),
        (UUID) f.get("moneda"),
        ((String) f.get("codigo")).trim(),
        (BigDecimal) f.get("valor"),
        instante(f.get("desde")));
  }

  private static OffsetDateTime instante(Object valor) {
    return switch (valor) {
      case null -> null;
      case OffsetDateTime momento -> momento;
      case Instant momento -> momento.atOffset(ZoneOffset.UTC);
      case Timestamp marca -> marca.toInstant().atOffset(ZoneOffset.UTC);
      default ->
          throw new IllegalStateException(
              "Tipo temporal inesperado en la proyección: " + valor.getClass());
    };
  }
}
