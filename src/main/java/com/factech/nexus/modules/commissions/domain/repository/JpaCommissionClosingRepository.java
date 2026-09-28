package com.factech.nexus.modules.commissions.domain.repository;

import com.factech.nexus.modules.commissions.domain.models.ClosingOrigin;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * {@link CommissionClosingRepository} sobre SQL nativo.
 *
 * <p><b>Sin {@code @Transactional}</b>: el servicio decide qué va en qué transacción —la constancia
 * en la suya, el bloqueo y el cierre de los lotes en la externa— y este adaptador no puede elegir
 * por él (`specs/cm/009-cerrar-periodo-comisiones/plan.md` §1).
 */
@Repository
public class JpaCommissionClosingRepository implements CommissionClosingRepository {

  /** Espacio del bloqueo consultivo del cierre; la clave es fija: hay un solo cierre a la vez. */
  private static final int ESPACIO_CIERRE = 4309;

  private static final String COLUMNAS =
      "id, origin, scheduled_for, triggered_by, started_at, closed_at, batches_closed,"
          + " lines_swept, lines_retried, lines_recovered";

  private static final String FILTRO =
      """
       WHERE (CAST(:origen AS varchar) IS NULL OR origin = CAST(:origen AS varchar))
         AND (CAST(:desde AS timestamptz) IS NULL OR started_at >= CAST(:desde AS timestamptz))
         AND (CAST(:hasta AS timestamptz) IS NULL OR started_at <= CAST(:hasta AS timestamptz))
      """;

  private final EntityManager em;

  public JpaCommissionClosingRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public boolean openScheduled(UUID id, OffsetDateTime turno, OffsetDateTime at) {
    return em.createNativeQuery(
                """
                INSERT INTO commission_closings (id, origin, scheduled_for, started_at, created_at)
                VALUES (:id, 'PROGRAMADO', :turno, :at, :at)
                ON CONFLICT (scheduled_for) DO NOTHING
                """)
            .setParameter("id", id)
            .setParameter("turno", turno)
            .setParameter("at", at)
            .executeUpdate()
        == 1;
  }

  @Override
  public void openManual(UUID id, UUID actor, OffsetDateTime at) {
    em.createNativeQuery(
            """
            INSERT INTO commission_closings (id, origin, triggered_by, started_at, created_at)
            VALUES (:id, 'MANUAL', :actor, :at, :at)
            """)
        .setParameter("id", id)
        .setParameter("actor", actor)
        .setParameter("at", at)
        .executeUpdate();
  }

  @Override
  public void lock() {
    em.createNativeQuery("SELECT pg_advisory_xact_lock(:ns, 0)")
        .setParameter("ns", ESPACIO_CIERRE)
        .getSingleResult();
  }

  @Override
  public boolean tryLock() {
    return Boolean.TRUE.equals(
        em.createNativeQuery("SELECT pg_try_advisory_xact_lock(:ns, 0)")
            .setParameter("ns", ESPACIO_CIERRE)
            .getSingleResult());
  }

  @Override
  public int closeOpenBatches(UUID closingId, OffsetDateTime at) {
    // El UPDATE toma cada fila con su bloqueo: si un devengo la tiene tomada,
    // espera, y la cierra con la comisión dentro. Un lote nacido en este mismo
    // instante o después se queda abierto: su periodo no ha empezado.
    return em.createNativeQuery(
            """
            UPDATE commission_batches
               SET status = 'PENDIENTE', period_end = :at, closing_id = :cierre, updated_at = :at
             WHERE status = 'ABIERTO' AND period_start < :at
            """)
        .setParameter("at", at)
        .setParameter("cierre", closingId)
        .executeUpdate();
  }

  @Override
  public void finish(
      UUID closingId, OffsetDateTime at, int batches, int swept, int retried, int recovered) {
    em.createNativeQuery(
            """
            UPDATE commission_closings
               SET closed_at = :at, batches_closed = :lotes, lines_swept = :barridas,
                   lines_retried = :reintentadas, lines_recovered = :recuperadas
             WHERE id = :id
            """)
        .setParameter("id", closingId)
        .setParameter("at", at)
        .setParameter("lotes", batches)
        .setParameter("barridas", swept)
        .setParameter("reintentadas", retried)
        .setParameter("recuperadas", recovered)
        .executeUpdate();
  }

  @Override
  public Optional<ClosingRow> find(UUID id) {
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        em.createNativeQuery("SELECT " + COLUMNAS + " FROM commission_closings WHERE id = :id")
            .setParameter("id", id)
            .getResultList();
    return filas.stream().findFirst().map(JpaCommissionClosingRepository::fila);
  }

  @Override
  public List<ClosingRow> search(ClosingFilter filtro, int offset, int limit) {
    Query consulta =
        em.createNativeQuery(
            "SELECT "
                + COLUMNAS
                + " FROM commission_closings"
                + FILTRO
                + " ORDER BY COALESCE(closed_at, started_at) DESC, id DESC"
                + " OFFSET :offset LIMIT :limite");
    parametros(consulta, filtro);
    @SuppressWarnings("unchecked")
    List<Object[]> filas =
        consulta.setParameter("offset", offset).setParameter("limite", limit).getResultList();
    List<ClosingRow> resultado = new ArrayList<>(filas.size());
    for (Object[] f : filas) {
      resultado.add(fila(f));
    }
    return resultado;
  }

  @Override
  public long count(ClosingFilter filtro) {
    Query consulta = em.createNativeQuery("SELECT count(*) FROM commission_closings" + FILTRO);
    parametros(consulta, filtro);
    return ((Number) consulta.getSingleResult()).longValue();
  }

  private static void parametros(Query consulta, ClosingFilter filtro) {
    consulta
        .setParameter("origen", filtro.origin() == null ? null : filtro.origin().name())
        .setParameter("desde", filtro.from())
        .setParameter("hasta", filtro.to());
  }

  private static ClosingRow fila(Object[] f) {
    return new ClosingRow(
        (UUID) f[0],
        ClosingOrigin.valueOf((String) f[1]),
        instante(f[2]),
        (UUID) f[3],
        instante(f[4]),
        instante(f[5]),
        ((Number) f[6]).intValue(),
        ((Number) f[7]).intValue(),
        ((Number) f[8]).intValue(),
        ((Number) f[9]).intValue());
  }

  private static OffsetDateTime instante(Object valor) {
    if (valor == null) {
      return null;
    }
    if (valor instanceof OffsetDateTime odt) {
      return odt;
    }
    if (valor instanceof java.time.Instant i) {
      return i.atOffset(java.time.ZoneOffset.UTC);
    }
    if (valor instanceof java.sql.Timestamp t) {
      return t.toInstant().atOffset(java.time.ZoneOffset.UTC);
    }
    throw new IllegalStateException("Tipo temporal inesperado: " + valor.getClass());
  }
}
