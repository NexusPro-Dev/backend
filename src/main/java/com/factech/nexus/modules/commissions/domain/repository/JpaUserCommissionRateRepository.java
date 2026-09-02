package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.UserCommissionRate;
import com.factech.nexus.shared.error.BusinessRuleException;
import com.factech.nexus.shared.error.FieldError;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de {@link UserCommissionRateRepository}.
 *
 * <p><b>Traduce por nombre de restricción y por {@code SQLState}, nunca por el texto del mensaje
 * del driver</b>, que cambia entre versiones de PostgreSQL y convertiría una traducción correcta en
 * un {@code 500} silencioso el día de una actualización.
 */
@Repository
public class JpaUserCommissionRateRepository implements UserCommissionRateRepository {

  /** El nombre exacto de la restricción de `V48`. Si cambia allí, cambia aquí. */
  private static final String EX_SOLAPE = "uq_user_commission_rates_vigente";

  /** `SQLState` estándar de «violación de restricción de exclusión». */
  private static final String ESTADO_EXCLUSION = "23P01";

  private final EntityManager em;

  public JpaUserCommissionRateRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public UserCommissionRate save(UserCommissionRate tasa) {
    lockUser(tasa.getUserId());
    try {
      em.persist(tasa);
      em.flush();
      return tasa;
    } catch (RuntimeException fallo) {
      throw traducir(fallo);
    }
  }

  /**
   * Bloqueo consultivo de transacción sobre la persona, con {@code pg_advisory_xact_lock}.
   *
   * <p><b>Es BLOQUEANTE y no un intento</b>, al revés que el de la jerarquía de roles: aquí no se
   * quiere rechazar a la segunda peticionaria, se quiere que espere y compruebe. Lo que decide si
   * su tasa cabe sigue siendo la restricción del motor.
   *
   * <p>La clave es un entero derivado de la persona. <b>Una colisión de hash no rompe nada</b>:
   * serializa a dos personas que no compartían nada, que es una pérdida de paralelismo y no un
   * fallo de corrección.
   *
   * <p>Se libera solo, al confirmar o revertir la transacción.
   */
  @Override
  public void lockUser(UUID userId) {
    em.createNativeQuery("SELECT pg_advisory_xact_lock(:persona)")
        .setParameter("persona", (long) userId.hashCode())
        .getSingleResult();
  }

  @Override
  public Optional<UserCommissionRate> findAlive(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return em
        .createQuery(
            "SELECT t FROM UserCommissionRate t WHERE t.id = :id AND t.deletedAt IS NULL",
            UserCommissionRate.class)
        .setParameter("id", id)
        .setMaxResults(1)
        .getResultList()
        .stream()
        .findFirst();
  }

  @Override
  public Optional<UserCommissionRate> findAny(UUID id) {
    return id == null
        ? Optional.empty()
        : Optional.ofNullable(em.find(UserCommissionRate.class, id));
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
  public Optional<UserCommissionRate> findOverlapping(
      UUID userId, LocalDate validFrom, LocalDate validTo, UUID excluida) {

    List<?> ids =
        em.createNativeQuery(
                """
                SELECT t.id
                  FROM user_commission_rates t
                 WHERE t.deleted_at IS NULL
                   AND t.user_id = :persona
                   AND daterange(t.valid_from, t.valid_to, '[]')
                       && daterange(CAST(:desde AS date), CAST(:hasta AS date), '[]')
                   AND (CAST(:excluida AS uuid) IS NULL OR t.id <> CAST(:excluida AS uuid))
                 LIMIT 1
                """)
            .setParameter("persona", userId)
            .setParameter("desde", validFrom.toString())
            .setParameter("hasta", validTo == null ? null : validTo.toString())
            .setParameter("excluida", excluida == null ? null : excluida.toString())
            .getResultList();

    return ids.stream().findFirst().map(id -> em.find(UserCommissionRate.class, (UUID) id));
  }

  /**
   * La violación del solapamiento, traducida.
   *
   * <p><b>Se atrapa {@code RuntimeException} y no {@code PersistenceException}</b>, y no es pereza:
   * según por dónde salga el volcado, el fallo llega envuelto como {@code PersistenceException} o
   * ya traducido por Spring a {@code DataIntegrityViolationException}, que <b>no</b> es lo primero.
   * Atrapando solo una de las dos, el mismo defecto se escapa como {@code 500} en un camino y no en
   * el otro — comprobado el 28-08-2026. Lo que decide es la cadena de causas, no el envoltorio.
   */
  private static RuntimeException traducir(RuntimeException fallo) {
    if (esSolapamiento(fallo)) {
      String mensaje = "Esa persona ya tiene una tasa personalizada viva en parte de ese periodo.";
      return new BusinessRuleException(
          "EX-003", mensaje, List.of(new FieldError("validFrom", "EX-003", mensaje)));
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
   * este método deja de bastar.
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
