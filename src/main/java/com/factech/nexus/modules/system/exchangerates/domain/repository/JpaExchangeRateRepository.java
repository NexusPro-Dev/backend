package com.factech.nexus.modules.system.exchangerates.domain.repository;

import com.factech.nexus.modules.system.exchangerates.application.ExchangeRateLookup;
import com.factech.nexus.modules.system.exchangerates.domain.models.ExchangeRate;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de escritura y de la lectura publicada (`RF-SP-047` · `T-04`, `T-02` de `RF-PM-008`).
 *
 * <p><b>Traduce la violación del {@code EXCLUDE} por nombre de restricción y por {@code
 * SQLState}</b>, nunca por el texto del driver: ese texto cambia entre versiones de PostgreSQL y la
 * traducción se rompería sin que ninguna prueba lo dijera. Por qué el nombre <b>no basta</b>, en
 * {@link #ESTADO_EXCLUSION}.
 *
 * <p><b>Publica además la lectura por lotes</b> ({@code ratesOn}), que existe por el listado de
 * productos: desde que cada fila lleva su conversión, preguntar de una en una convertiría una
 * página en veinte consultas sin que la respuesta cambiara ni una coma.
 */
@Repository
public class JpaExchangeRateRepository implements ExchangeRateRepository, ExchangeRateLookup {

  private static final String UQ_VIGENTE = "uq_exchange_rates_vigente";

  /**
   * {@code 23P01} — violación de restricción de <b>exclusión</b> en PostgreSQL.
   *
   * <p><b>Hace falta porque Hibernate no da el nombre de la restricción cuando la violación es de
   * exclusión</b>: su extractor reconoce los mensajes de {@code UNIQUE} y de clave foránea, y ante
   * «conflicting key value violates exclusion constraint» devuelve {@code null}. Estaba escrito
   * desde el 28-08-2026 en {@code JpaUserCommissionRateRepository} —el otro {@code EXCLUDE} del
   * sistema— y <b>aquí se olvidó</b>: el síntoma era un {@code 500} donde toca un {@code 409}, y
   * <b>solo se ve con dos peticiones a la vez</b>, porque por el camino normal corta la
   * verificación previa. Lo destapó `T-12`.
   *
   * <p>Mirar el {@code SQLState} es igual de estructural que el nombre y además estándar, de modo
   * que la regla del proyecto —no traducir por el texto del driver— se sigue cumpliendo. <b>Asume
   * que esta tabla tiene una sola restricción de exclusión</b>; el día que tenga dos, el estado
   * deja de identificarla.
   */
  private static final String ESTADO_EXCLUSION = "23P01";

  private final EntityManager em;

  public JpaExchangeRateRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional
  public ExchangeRate save(ExchangeRate tasa) {
    em.persist(tasa);
    return tasa;
  }

  /**
   * El mismo predicado que el {@code EXCLUDE}, escrito a mano para poder dar un mensaje.
   *
   * <p><b>Los dos intervalos tienen que ser el mismo</b>, y por eso este también es cerrado: con un
   * abierto aquí, la comprobación previa diría «libre» sobre un día que la restricción considera
   * ocupado, y el actor recibiría un `409` que su propia consulta previa no explicaba.
   */
  @Override
  @Transactional(readOnly = true)
  public boolean seSolapa(UUID origen, UUID destino, LocalDate desde, LocalDate hasta) {
    Number cuantas =
        (Number)
            em.createNativeQuery(
                    """
                    SELECT count(*)
                      FROM exchange_rates
                     WHERE is_active AND deleted_at IS NULL
                       AND source_currency_id = CAST(:origen AS uuid)
                       AND target_currency_id = CAST(:destino AS uuid)
                       AND daterange(valid_from, valid_to, '[]')
                           && daterange(CAST(:desde AS date), CAST(:hasta AS date), '[]')
                    """)
                .setParameter("origen", origen)
                .setParameter("destino", destino)
                .setParameter("desde", desde)
                .setParameter("hasta", hasta)
                .getSingleResult();
    return cuantas.longValue() > 0;
  }

  @Override
  @Transactional
  public void flush() {
    try {
      em.flush();
    } catch (PersistenceException fallo) {
      throw traducir(fallo);
    }
  }

  /**
   * `RF-PM-008` · `T-02` — la tasa vigente ese día, ya elegida.
   *
   * <p><b>Los dos extremos entran</b> (`<=` y `>=`), igual que el {@code daterange(..., '[]')} de
   * la restricción. Escrito con `<` habría días que el {@code EXCLUDE} da por cubiertos y esta
   * lectura declara libres — y no fallaría nada: devolvería vacío sobre un día que sí tiene tasa.
   *
   * <p>{@code LIMIT 1} no elige: `RN-SP-032` garantiza que solo puede haber una. Está para que, si
   * esa regla se rompiera alguna vez, esta lectura devolviera una tasa y no reventara.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<ExchangeRateView> rateOn(UUID origen, UUID destino, LocalDate dia) {
    if (origen == null || destino == null || dia == null) {
      return Optional.empty();
    }
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT id, price, valid_from, valid_to
                  FROM exchange_rates
                 WHERE is_active AND deleted_at IS NULL
                   AND source_currency_id = CAST(:origen AS uuid)
                   AND target_currency_id = CAST(:destino AS uuid)
                   AND valid_from <= CAST(:dia AS date)
                   AND (valid_to IS NULL OR valid_to >= CAST(:dia AS date))
                 LIMIT 1
                """,
                Tuple.class)
            .setParameter("origen", origen)
            .setParameter("destino", destino)
            .setParameter("dia", dia)
            .getResultList();

    return filas.stream()
        .findFirst()
        .map(
            fila ->
                new ExchangeRateView(
                    (UUID) fila.get("id"),
                    (BigDecimal) fila.get("price"),
                    fecha(fila.get("valid_from")),
                    fecha(fila.get("valid_to"))));
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, ExchangeRateView> ratesOn(
      Collection<UUID> origenes, UUID destino, LocalDate dia) {

    if (origenes == null || origenes.isEmpty() || destino == null || dia == null) {
      return Map.of();
    }

    // Las repetidas se colapsan ANTES de consultar: una página de veinte
    // productos en tres monedas pregunta por tres, no por veinte.
    Set<UUID> distintas = new LinkedHashSet<>(origenes);
    distintas.remove(null);
    if (distintas.isEmpty()) {
      return Map.of();
    }

    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT source_currency_id, id, price, valid_from, valid_to
                  FROM exchange_rates
                 WHERE is_active AND deleted_at IS NULL
                   AND source_currency_id IN (:origenes)
                   AND target_currency_id = CAST(:destino AS uuid)
                   AND valid_from <= CAST(:dia AS date)
                   AND (valid_to IS NULL OR valid_to >= CAST(:dia AS date))
                """,
                Tuple.class)
            .setParameter("origenes", distintas)
            .setParameter("destino", destino)
            .setParameter("dia", dia)
            .getResultList();

    // `RN-SP-032` garantiza UNA por par y día, de modo que no hay que elegir
    // entre dos: si algún día hubiera dos, quedarse con la primera es lo mismo
    // que hace `rateOn` con su `LIMIT 1`, y las dos mentirían igual.
    Map<UUID, ExchangeRateView> tasas = new LinkedHashMap<>();
    for (Tuple fila : filas) {
      tasas.putIfAbsent(
          (UUID) fila.get("source_currency_id"),
          new ExchangeRateView(
              (UUID) fila.get("id"),
              (BigDecimal) fila.get("price"),
              fecha(fila.get("valid_from")),
              fecha(fila.get("valid_to"))));
    }
    return tasas;
  }

  private static LocalDate fecha(Object valor) {
    return switch (valor) {
      case null -> null;
      case LocalDate dia -> dia;
      case Date dia -> dia.toLocalDate();
      default -> throw new IllegalStateException("Tipo de fecha inesperado: " + valor.getClass());
    };
  }

  private static RuntimeException traducir(PersistenceException fallo) {
    if (esSolapamiento(fallo)) {
      // Sin decir con cuál choca, y es inevitable: la verificación previa no vio
      // a nadie —por eso llegamos hasta aquí— y averiguarlo ahora exigiría una
      // consulta dentro del fallo. Este es el camino de la CARRERA; el mensaje
      // accionable lo da la verificación previa, que es el camino normal.
      String mensaje = "Ya hay una tasa vigente para ese par en ese periodo.";
      return new BusinessRuleException(
          "EX-002", mensaje, List.of(new FieldError("validFrom", "EX-002", mensaje)));
    }
    return fallo;
  }

  /**
   * ¿Es la violación del no solapamiento?
   *
   * <p><b>Se mira por dos vías, y la segunda es la que de verdad dispara</b>: el nombre de la
   * restricción —que es lo que el proyecto exige— y, cuando Hibernate no lo da, el {@code SQLState}
   * de exclusión. Ver {@link #ESTADO_EXCLUSION} para por qué no basta con el nombre.
   */
  private static boolean esSolapamiento(Throwable fallo) {
    for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
      if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion
          && UQ_VIGENTE.equals(violacion.getConstraintName())) {
        return true;
      }
      if (causa instanceof java.sql.SQLException sql
          && ESTADO_EXCLUSION.equals(sql.getSQLState())) {
        return true;
      }
    }
    return false;
  }
}
