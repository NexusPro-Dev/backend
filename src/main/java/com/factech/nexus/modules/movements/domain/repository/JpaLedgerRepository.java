package com.factech.nexus.modules.movements.domain.repository;

import com.factech.nexus.modules.movements.domain.models.AccountKind;
import com.factech.nexus.modules.movements.domain.models.AccountNumber;
import com.factech.nexus.modules.movements.domain.models.EntryEvent;
import com.factech.nexus.shared.persistence.MinorUnits;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de las cuentas y los asientos. SQL nativo, como el resto del módulo. */
@Repository
public class JpaLedgerRepository implements LedgerRepository {

  /** Tres, por lo mismo que el comprobante: si tres números aleatorios chocan, algo está roto. */
  private static final int INTENTOS = 3;

  private final EntityManager em;

  public JpaLedgerRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional
  public UUID accountOf(UUID titular, AccountKind tipo, UUID moneda) {
    for (int intento = 1; intento <= INTENTOS; intento++) {
      Optional<UUID> existente = buscar(titular, tipo, moneda);
      if (existente.isPresent()) {
        return existente.get();
      }
      // SIN COLUMNA EN EL ON CONFLICT: choque de titular —otro hilo la creó— o de
      // número —azar—. En los dos casos se relee; si no está, fue el número.
      em.createNativeQuery(
              """
              INSERT INTO accounts (id, user_id, kind, name, number, currency_id, balance)
              VALUES (:id, :titular, :tipo, :nombre, :numero, :moneda, 0)
              ON CONFLICT DO NOTHING
              """)
          .setParameter("id", UUID.randomUUID())
          .setParameter("titular", titular)
          .setParameter("tipo", tipo.name())
          .setParameter("nombre", tipo.nombre())
          .setParameter("numero", AccountNumber.generar())
          .setParameter("moneda", moneda)
          .executeUpdate();
    }
    return buscar(titular, tipo, moneda)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No se pudo crear la cuenta %s de %s en %d intentos."
                        .formatted(tipo, titular, INTENTOS)));
  }

  private Optional<UUID> buscar(UUID titular, AccountKind tipo, UUID moneda) {
    @SuppressWarnings("unchecked")
    List<Object> filas =
        em.createNativeQuery(
                """
                SELECT id FROM accounts
                 WHERE user_id IS NOT DISTINCT FROM CAST(:titular AS uuid)
                   AND kind = :tipo AND currency_id = :moneda
                """)
            .setParameter("titular", titular)
            .setParameter("tipo", tipo.name())
            .setParameter("moneda", moneda)
            .getResultList();
    return filas.stream().findFirst().map(UUID.class::cast);
  }

  @Override
  @Transactional
  public Optional<BigDecimal> move(UUID cuenta, BigDecimal delta) {
    // LA CONDICIÓN VA EN LA SENTENCIA y no en una lectura previa: el UPDATE
    // bloquea la fila, de modo que una segunda petición espera a la primera y
    // ve su saldo. Si no alcanza, no toca nada y la transacción sigue viva.
    @SuppressWarnings("unchecked")
    List<Object> filas =
        em.createNativeQuery(
                """
                UPDATE accounts SET balance = balance + :delta
                 WHERE id = :id AND (user_id IS NULL OR balance + :delta >= 0)
                RETURNING balance
                """)
            .setParameter("id", cuenta)
            .setParameter("delta", MinorUnits.toMinor(delta))
            .getResultList();
    return filas.stream().findFirst().map(MinorUnits::fromMinor);
  }

  @Override
  @Transactional
  public void post(
      UUID movimiento,
      UUID pago,
      UUID cuenta,
      EntryEvent evento,
      BigDecimal importe,
      BigDecimal saldoTras,
      OffsetDateTime cuando) {
    em.createNativeQuery(
            """
            INSERT INTO movement_entries (id, movement_id, payment_id, account_id, event,
                                          amount, balance_after, created_at)
            VALUES (:id, :movimiento, :pago, :cuenta, :evento, :importe, :saldo, :cuando)
            """)
        .setParameter("id", UUID.randomUUID())
        .setParameter("movimiento", movimiento)
        .setParameter("pago", pago)
        .setParameter("cuenta", cuenta)
        .setParameter("evento", evento.name())
        .setParameter("importe", MinorUnits.toMinor(importe))
        .setParameter("saldo", MinorUnits.toMinor(saldoTras))
        .setParameter("cuando", cuando)
        .executeUpdate();
  }

  @Override
  @Transactional(readOnly = true)
  public BigDecimal balanceOf(UUID titular, AccountKind tipo, UUID moneda) {
    @SuppressWarnings("unchecked")
    List<Object> filas =
        em.createNativeQuery(
                """
                SELECT balance FROM accounts
                 WHERE user_id IS NOT DISTINCT FROM CAST(:titular AS uuid)
                   AND kind = :tipo AND currency_id = :moneda
                """)
            .setParameter("titular", titular)
            .setParameter("tipo", tipo.name())
            .setParameter("moneda", moneda)
            .getResultList();
    return filas.stream().findFirst().map(MinorUnits::fromMinor).orElse(BigDecimal.ZERO);
  }

  @Override
  @Transactional(readOnly = true)
  public List<BalanceRow> balancesOf(UUID persona) {
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        em.createNativeQuery(
                """
                SELECT a.currency_id AS moneda, c.code AS codigo, a.kind AS tipo, a.balance AS saldo
                  FROM accounts a
                  JOIN currencies c ON c.id = a.currency_id
                 WHERE a.user_id = :persona
                 ORDER BY c.code, a.kind
                """,
                Tuple.class)
            .setParameter("persona", persona)
            .getResultList();
    List<BalanceRow> resultado = new ArrayList<>(filas.size());
    for (Tuple f : filas) {
      resultado.add(
          new BalanceRow(
              (UUID) f.get("moneda"),
              (String) f.get("codigo"),
              (String) f.get("tipo"),
              MinorUnits.fromMinor(f.get("saldo"))));
    }
    return resultado;
  }

  /**
   * El alcance —{@code a.user_id = :persona}— va en la misma sentencia y antes que los filtros:
   * excluye por construcción las cuentas de la empresa y las ajenas (`CA-MV-254`).
   */
  private static final String HISTORIAL =
      """
      FROM movement_entries e
      JOIN accounts a ON a.id = e.account_id
      JOIN currencies c ON c.id = a.currency_id
      JOIN movements m ON m.id = e.movement_id
      JOIN movement_types t ON t.id = m.movement_type_id
      WHERE a.user_id = :persona
      """;

  private static String filtros(EntryFilter f) {
    StringBuilder sql = new StringBuilder();
    if (f.currencyId() != null) {
      sql.append(" AND a.currency_id = :moneda");
    }
    if (f.account() != null) {
      sql.append(" AND a.kind = :cuenta");
    }
    if (f.from() != null) {
      sql.append(" AND e.created_at >= :desde");
    }
    if (f.to() != null) {
      sql.append(" AND e.created_at < :hasta");
    }
    return sql.toString();
  }

  private static void enlazar(Query q, UUID persona, EntryFilter f) {
    q.setParameter("persona", persona);
    if (f.currencyId() != null) {
      q.setParameter("moneda", f.currencyId());
    }
    if (f.account() != null) {
      q.setParameter("cuenta", f.account());
    }
    if (f.from() != null) {
      q.setParameter("desde", f.from());
    }
    if (f.to() != null) {
      q.setParameter("hasta", f.to());
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<EntryRow> entriesOf(UUID persona, EntryFilter filtro, int offset, int limit) {
    Query consulta =
        em.createNativeQuery(
            """
            SELECT e.id AS id, e.created_at AS cuando, a.kind AS cuenta, c.id AS moneda,
                   c.code AS codigo, e.amount AS importe, e.balance_after AS saldo,
                   e.event AS evento, m.id AS mov_id, m.code AS mov_code, t.code AS mov_tipo,
                   m.status AS mov_estado, m.concept AS concepto, m.rejection_reason AS motivo
            """
                + HISTORIAL
                + filtros(filtro)
                + " ORDER BY e.created_at DESC, e.id DESC LIMIT :limite OFFSET :desplazamiento",
            Tuple.class);
    enlazar(consulta, persona, filtro);
    @SuppressWarnings("unchecked")
    List<Tuple> filas =
        consulta
            .setParameter("limite", limit)
            .setParameter("desplazamiento", offset)
            .getResultList();
    List<EntryRow> resultado = new ArrayList<>(filas.size());
    for (Tuple f : filas) {
      resultado.add(
          new EntryRow(
              (UUID) f.get("id"),
              instante(f.get("cuando")),
              (String) f.get("cuenta"),
              (UUID) f.get("moneda"),
              (String) f.get("codigo"),
              MinorUnits.fromMinor(f.get("importe")),
              MinorUnits.fromMinor(f.get("saldo")),
              (String) f.get("evento"),
              (UUID) f.get("mov_id"),
              (String) f.get("mov_code"),
              (String) f.get("mov_tipo"),
              (String) f.get("mov_estado"),
              (String) f.get("concepto"),
              (String) f.get("motivo")));
    }
    return resultado;
  }

  @Override
  @Transactional(readOnly = true)
  public long countEntries(UUID persona, EntryFilter filtro) {
    Query consulta = em.createNativeQuery("SELECT count(*) " + HISTORIAL + filtros(filtro));
    enlazar(consulta, persona, filtro);
    return ((Number) consulta.getSingleResult()).longValue();
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
