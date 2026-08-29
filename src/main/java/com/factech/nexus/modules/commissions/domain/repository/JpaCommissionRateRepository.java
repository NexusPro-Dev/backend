package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.CommissionRate;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de {@link CommissionRateRepository}.
 *
 * <p><b>Traduce por nombre de restricción, nunca por el texto del mensaje del driver</b>, que
 * cambia entre versiones de PostgreSQL y convertiría una traducción correcta en un {@code 500}
 * silencioso el día de una actualización. Es la regla que `SP` y `PM` ya siguen.
 */
@Repository
public class JpaCommissionRateRepository implements CommissionRateRepository {

  /** El nombre exacto de la restricción de `V44`. Si cambia allí, cambia aquí. */
  private static final String EX_SOLAPE = "ex_commission_rates_sin_solape";

  /**
   * El centinela con el que `V44` normaliza las ausencias en la restricción.
   *
   * <p>Se repite aquí porque esta consulta tiene que <b>ver lo mismo</b> que ve el índice: si una
   * de las dos comparara los nulos de otra forma, el mensaje de la carrera señalaría a una tarifa
   * distinta de la que realmente chocó.
   */
  private static final String NULO = "00000000-0000-0000-0000-000000000000";

  /** `SQLState` estándar de «violación de restricción de exclusión». */
  private static final String ESTADO_EXCLUSION = "23P01";

  private final EntityManager em;

  public JpaCommissionRateRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public CommissionRate save(CommissionRate tarifa) {
    lockCase(tarifa);
    try {
      em.persist(tarifa);
      em.flush();
      return tarifa;
    } catch (RuntimeException fallo) {
      throw traducir(fallo);
    }
  }

  /**
   * Bloqueo consultivo de transacción sobre el caso, con {@code pg_advisory_xact_lock}.
   *
   * <p><b>Es BLOQUEANTE y no un intento</b>, al revés que el de la jerarquía de roles: aquí no se
   * quiere rechazar a la segunda peticionaria, se quiere que espere y compruebe. Lo que decide si
   * su tarifa cabe sigue siendo la restricción del motor.
   *
   * <p>La clave son dos enteros derivados del caso. <b>Una colisión de hash no rompe nada</b>:
   * serializa dos casos que no compartían periodo, que es una pérdida de paralelismo y no un fallo
   * de corrección. El identificador nulo se sustituye por el mismo centinela que usa el índice,
   * para que dos tarifas por omisión caigan en la misma clave.
   *
   * <p>Se libera solo, al confirmar o revertir la transacción.
   */
  @Override
  public void lockCase(CommissionRate tarifa) {
    int caso = tarifa.getRoleId().hashCode();
    int alcance =
        java.util.Objects.hash(
            tarifa.getProductId() == null ? NULO : tarifa.getProductId().toString(),
            tarifa.getUserId() == null ? NULO : tarifa.getUserId().toString());

    em.createNativeQuery("SELECT pg_advisory_xact_lock(:caso, :alcance)")
        .setParameter("caso", caso)
        .setParameter("alcance", alcance)
        .getSingleResult();
  }

  @Override
  public Optional<CommissionRate> findAlive(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery(
            "SELECT t FROM CommissionRate t WHERE t.id = :id AND t.deletedAt IS NULL",
            CommissionRate.class)
        .setParameter("id", id)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public Optional<CommissionRate> findAny(UUID id) {
    return id == null ? Optional.empty() : Optional.ofNullable(em.find(CommissionRate.class, id));
  }

  @Override
  public void flushChanges() {
    try {
      em.flush();
    } catch (RuntimeException fallo) {
      throw traducir(fallo);
    }
  }

  @Override
  public Optional<CommissionRate> findOverlapping(
      UUID roleId,
      UUID productId,
      UUID userId,
      LocalDate validFrom,
      LocalDate validTo,
      UUID excluida) {

    List<?> ids =
        em.createNativeQuery(
                """
                SELECT t.id
                  FROM commission_rates t
                 WHERE t.deleted_at IS NULL
                   AND t.role_id = :rol
                   AND COALESCE(t.product_id, CAST(:nulo AS uuid))
                       = COALESCE(CAST(:producto AS uuid), CAST(:nulo AS uuid))
                   AND COALESCE(t.user_id, CAST(:nulo AS uuid))
                       = COALESCE(CAST(:persona AS uuid), CAST(:nulo AS uuid))
                   AND daterange(t.valid_from, t.valid_to, '[]')
                       && daterange(CAST(:desde AS date), CAST(:hasta AS date), '[]')
                   AND (CAST(:excluida AS uuid) IS NULL OR t.id <> CAST(:excluida AS uuid))
                 LIMIT 1
                """)
            .setParameter("rol", roleId)
            .setParameter("nulo", NULO)
            .setParameter("producto", productId == null ? null : productId.toString())
            .setParameter("persona", userId == null ? null : userId.toString())
            .setParameter("desde", validFrom.toString())
            .setParameter("hasta", validTo == null ? null : validTo.toString())
            .setParameter("excluida", excluida == null ? null : excluida.toString())
            .getResultList();

    return ids.stream().findFirst().map(id -> em.find(CommissionRate.class, (UUID) id));
  }

  /**
   * La violación del solapamiento, con la tarifa que la causó.
   *
   * <p><b>Se atrapa {@code RuntimeException} y no {@code PersistenceException}</b>, y no es pereza:
   * según por dónde salga el volcado, el fallo llega envuelto como {@code PersistenceException} o
   * ya traducido por Spring a {@code DataIntegrityViolationException}, que <b>no</b> es lo primero.
   * Atrapando solo una de las dos, el mismo defecto se escapa como {@code 500} en un camino y no en
   * el otro — comprobado el 28-08-2026. Lo que decide es la cadena de causas, no el envoltorio.
   *
   * <p><b>Se busca la otra DESPUÉS de fallar</b>, ya fuera de la carrera: en ese punto existe con
   * certeza. Si por lo que fuera no se encontrara —otra transacción la retiró entretanto—, el
   * mensaje sigue diciendo qué ocurrió, solo que sin señalar cuál.
   */
  private static RuntimeException traducir(RuntimeException fallo) {
    if (esSolapamiento(fallo)) {
      String mensaje =
          "Ya hay una tarifa viva para ese rol, ese producto y esa persona en parte de ese"
              + " periodo.";
      return new BusinessRuleException(
          "EX-007", mensaje, List.of(new FieldError("validFrom", "EX-007", mensaje)));
    }
    return fallo;
  }

  /**
   * ¿Es la violación del no solapamiento?
   *
   * <p><b>Hibernate NO da el nombre de la restricción cuando la violación es de EXCLUSIÓN.</b> Su
   * extractor reconoce los mensajes de {@code UNIQUE} y de clave foránea, y ante «conflicting key
   * value violates exclusion constraint» devuelve {@code null} — comprobado el 28-08-2026 con
   * Hibernate 6.6 y PostgreSQL 17, y el síntoma era un {@code 500} donde tocaba un {@code 409}.
   *
   * <p>De ahí que aquí se mire el <b>{@code SQLState}</b>, que es igual de estructural y además
   * estándar: <b>{@code 23P01} es, y solo es, «violación de restricción de exclusión»</b>. No se
   * lee el texto del mensaje, de modo que la regla del proyecto —traducir por algo que no cambie
   * entre versiones del driver— se sigue cumpliendo.
   *
   * <p><b>Lo que esto asume, y hay que saberlo:</b> esta tabla tiene <b>una sola</b> restricción de
   * exclusión, así que el estado la identifica sin ambigüedad. El día que se añada una segunda,
   * este método deja de bastar y habrá que obtener el nombre del error estructurado del servidor —
   * que el driver expone, pero no desde el código de producción, porque su dependencia es de
   * ejecución.
   */
  private static boolean esSolapamiento(Throwable fallo) {
    for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
      if (causa instanceof org.hibernate.exception.ConstraintViolationException violacion
          && EX_SOLAPE.equals(violacion.getConstraintName())) {
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
