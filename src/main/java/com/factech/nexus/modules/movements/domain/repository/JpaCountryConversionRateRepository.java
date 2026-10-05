package com.factech.nexus.modules.movements.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
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

/**
 * SQL nativo, como {@link JpaPointsRateRepository}: la tabla no tiene entidad porque no se edita, y
 * el país y las monedas son de `SP` y se leen en la misma sentencia.
 */
@Repository
public class JpaCountryConversionRateRepository implements CountryConversionRateRepository {

  /** Lo que devuelven las dos lecturas: la fila, su país y sus dos monedas. */
  private static final String COLUMNAS =
      """
      r.id AS id, p.id AS pais, p.code AS pais_codigo, p.name AS pais_nombre,
      m.id AS moneda, m.code AS moneda_codigo, m.decimal_places AS moneda_decimales,
      b.id AS base, b.code AS base_codigo, b.decimal_places AS base_decimales,
      r.pay_in_price AS cobro, r.payout_price AS retiro, r.shop_id AS tienda,
      r.shop_secret_key AS clave, r.valid_from AS desde
      """;

  private static final String TABLAS =
      """
      country_conversion_rates r
      JOIN countries p ON p.id = r.country_id
      JOIN currencies m ON m.id = r.currency_id
      JOIN currencies b ON b.id = r.base_currency_id
      """;

  private final EntityManager em;

  public JpaCountryConversionRateRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ConversionRow> current(UUID countryId, OffsetDateTime at) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                "SELECT "
                    + COLUMNAS
                    + " FROM "
                    + TABLAS
                    + " WHERE r.country_id = :pais AND r.valid_from <= :ahora"
                    + " ORDER BY r.valid_from DESC LIMIT 1",
                Tuple.class)
            .setParameter("pais", countryId)
            .setParameter("ahora", at)
            .getResultList();
    return filas.stream().findFirst().map(JpaCountryConversionRateRepository::fila);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ConversionRow> currentOfActiveCountries(OffsetDateTime at, UUID countryId) {
    // DISTINCT ON y no una consulta por país: `ix_country_conversion_rates_vigente`
    // da la primera fila de cada país ya ordenada.
    Query consulta =
        em.createNativeQuery(
                "SELECT v.* FROM (SELECT DISTINCT ON (r.country_id) "
                    + COLUMNAS
                    + " FROM "
                    + TABLAS
                    + " WHERE r.valid_from <= :ahora AND p.is_active"
                    + (countryId == null ? "" : " AND r.country_id = :pais")
                    + " ORDER BY r.country_id, r.valid_from DESC) v"
                    + " ORDER BY v.pais_codigo",
                Tuple.class)
            .setParameter("ahora", at);
    if (countryId != null) {
      consulta.setParameter("pais", countryId);
    }
    @SuppressWarnings("unchecked")
    List<Tuple> filas = consulta.getResultList();
    return filas.stream().map(JpaCountryConversionRateRepository::fila).toList();
  }

  @Override
  @Transactional
  public void insert(
      UUID id,
      UUID countryId,
      UUID currencyId,
      UUID baseCurrencyId,
      BigDecimal payInPrice,
      BigDecimal payoutPrice,
      String shopId,
      String shopSecretKey,
      OffsetDateTime validFrom,
      UUID by) {
    em.createNativeQuery(
            """
            INSERT INTO country_conversion_rates (id, country_id, currency_id, base_currency_id,
                                                  pay_in_price, payout_price, shop_id,
                                                  shop_secret_key, valid_from, created_by,
                                                  created_at)
            VALUES (:id, :pais, :moneda, :base, :cobro, :retiro, CAST(:tienda AS varchar),
                    CAST(:clave AS text), :desde, :quien, :desde)
            """)
        .setParameter("id", id)
        .setParameter("pais", countryId)
        .setParameter("moneda", currencyId)
        .setParameter("base", baseCurrencyId)
        .setParameter("cobro", payInPrice)
        .setParameter("retiro", payoutPrice)
        .setParameter("tienda", shopId)
        .setParameter("clave", shopSecretKey)
        .setParameter("desde", validFrom)
        .setParameter("quien", by)
        .executeUpdate();
  }

  private static ConversionRow fila(Tuple f) {
    return new ConversionRow(
        (UUID) f.get("id"),
        (UUID) f.get("pais"),
        ((String) f.get("pais_codigo")).trim(),
        (String) f.get("pais_nombre"),
        (UUID) f.get("moneda"),
        ((String) f.get("moneda_codigo")).trim(),
        ((Number) f.get("moneda_decimales")).intValue(),
        (UUID) f.get("base"),
        ((String) f.get("base_codigo")).trim(),
        ((Number) f.get("base_decimales")).intValue(),
        (BigDecimal) f.get("cobro"),
        (BigDecimal) f.get("retiro"),
        (String) f.get("tienda"),
        (String) f.get("clave"),
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
